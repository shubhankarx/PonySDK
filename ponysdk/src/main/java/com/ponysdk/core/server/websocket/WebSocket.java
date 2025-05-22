/*
 * Copyright (c) 2017 PonySDK
 *  Owners:
 *  Luciano Broussal  <luciano.broussal AT gmail.com>
 *  Mathieu Barbier   <mathieu.barbier AT gmail.com>
 *  Nicolas Ciaravola <nicolas.ciaravola.pro AT gmail.com>
 *
 *  WebSite:
 *  http://code.google.com/p/pony-sdk/
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.ponysdk.core.server.websocket;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import org.eclipse.jetty.util.component.Container;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.common.extensions.ExtensionStack;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ponysdk.core.model.ClientToServerModel;
import com.ponysdk.core.model.ServerToClientModel;
import com.ponysdk.core.model.ValueTypeModel;
import com.ponysdk.core.server.application.ApplicationConfiguration;
import com.ponysdk.core.server.application.ApplicationManager;
import com.ponysdk.core.server.application.UIContext;
import com.ponysdk.core.server.context.CommunicationSanityChecker;
import com.ponysdk.core.server.stm.TxnContext;
import com.ponysdk.core.ui.basic.PObject;

public class WebSocket implements WebSocketListener, WebsocketEncoder {

    private static final String MSG_RECEIVED = "Message received from terminal : UIContext #{} on {} : {}";
    private static final Logger log = LoggerFactory.getLogger(WebSocket.class);
    private static final Logger loggerIn = LoggerFactory.getLogger("WebSocket-IN");
    private static final Logger loggerOut = LoggerFactory.getLogger("WebSocket-OUT");

    private ServletUpgradeRequest request;
    private WebsocketMonitor monitor;
    private WebSocketPusher websocketPusher;
    private ApplicationManager applicationManager;

    private TxnContext context;
    private Session session;
    private UIContext uiContext;
    private Listener listener;

    private long lastSentPing;
    
    // Dictionary compression settings (enabled by default)
    private final ModelValueDictionary dictionary = new ModelValueDictionary();
    private final List<ModelValuePair> currentBatch = new ArrayList<>();
    private static final int BATCH_THRESHOLD = 3;
    private boolean dictionaryEnabled = true; // Enabled by default
    
    // Timer for logging dictionary status
    private java.util.Timer dictionaryLogTimer;

    public WebSocket() {
        // Timer will be initialized after UIContext is available
    }

    @Override
    public void onWebSocketConnect(final Session session) {
        try {
            if (!session.isOpen()) throw new IllegalStateException("Session already closed");
            this.session = session;

            // 1K for max chunk size and 1M for total buffer size
            // Don't set max chunk size > 8K because when using Jetty Websocket compression, the chunks are limited to 8K

            this.websocketPusher = new WebSocketPusher(session, 1 << 20, 1 << 12, TimeUnit.SECONDS.toMillis(60));
            uiContext = new UIContext(this, context, applicationManager.getConfiguration(), request);
            log.info("Creating a new {}", uiContext);

            final CommunicationSanityChecker communicationSanityChecker = new CommunicationSanityChecker(uiContext);
            context.registerUIContext(uiContext);
            
            // Temporarily disable dictionary until initial UI setup is complete
            setDictionaryEnabled(false);

            uiContext.acquire();
            try {
                beginObject();
                final ApplicationConfiguration configuration = uiContext.getConfiguration();
                final boolean enableClientToServerHeartBeat = configuration.isEnableClientToServerHeartBeat();
                final TimeUnit heartBeatPeriodTimeUnit = configuration.getHeartBeatPeriodTimeUnit();
                final int heartBeatPeriod = enableClientToServerHeartBeat
                        ? (int) heartBeatPeriodTimeUnit.toSeconds(configuration.getHeartBeatPeriod())
                        : 0;

                encode(ServerToClientModel.CREATE_CONTEXT, uiContext.getID()); // TODO nciaravola integer ?
                encode(ServerToClientModel.OPTION_FORMFIELD_TABULATION, configuration.isTabindexOnlyFormField());
                encode(ServerToClientModel.HEARTBEAT_PERIOD, heartBeatPeriod);
                endObject();
                if (isAlive()) flush0();
            } catch (final Throwable e) {
                log.error("Cannot send initial setup to client", e);
            } finally {
                uiContext.release();
            }

            applicationManager.startApplication(uiContext);
            communicationSanityChecker.start();
            
            // Start periodic dictionary logging (every 10 seconds)
            dictionaryLogTimer = new java.util.Timer("dictionary-logger", true);
            dictionaryLogTimer.scheduleAtFixedRate(new java.util.TimerTask() {
                @Override
                public void run() {
                    if (isAlive()) {
                        logDictionary();
                    }
                }
            }, 20000, 10000); // First log after 20 seconds, then every 10 seconds
            
            // Enable dictionary compression after UI is completely loaded and stable (15 seconds)
            // This delay ensures all widgets are correctly initialized before enabling compression
            new java.util.Timer(true).schedule(new java.util.TimerTask() {
                @Override
                public void run() {
                    if (isAlive()) {
                        uiContext.acquire();
                        try {
                            setDictionaryEnabled(true);
                            log.info("Dictionary compression enabled for UIContext #{}", uiContext.getID());
                        } finally {
                            uiContext.release();
                        }
                    }
                }
            }, 15000); // 15 seconds delay
        } catch (final Exception e) {
            log.error("Cannot process WebSocket instructions", e);
        }
    }

    @Override
    public void onWebSocketError(final Throwable throwable) {
        log.error("WebSocket Error on UIContext #{}", uiContext.getID(), throwable);
        
        // Clean up timer on error
        if (dictionaryLogTimer != null) {
            dictionaryLogTimer.cancel();
            dictionaryLogTimer = null;
        }
        
        uiContext.onDestroy();
    }

    @Override
    public void onWebSocketClose(final int statusCode, final String reason) {
        if (log.isInfoEnabled())
            log.info("WebSocket closed on UIContext #{} : {}, reason : {}", uiContext.getID(),
                    NiceStatusCode.getMessage(statusCode), Objects.requireNonNullElse(reason, ""));
        
        // Clean up timer when socket is closed
        if (dictionaryLogTimer != null) {
            dictionaryLogTimer.cancel();
            dictionaryLogTimer = null;
        }
        
        uiContext.onDestroy();
    }

    /**
     * Receive from the terminal
     */

    @Override
    public void onWebSocketText(final String message) {
        if (this.listener != null) listener.onIncomingText(message);
        if (isAlive()) {
            try {
                uiContext.onMessageReceived();
                if (monitor != null) monitor.onMessageReceived(WebSocket.this, message);

                final JsonObject jsonObject;
                try (final JsonReader reader = uiContext.getJsonProvider().createReader(new StringReader(message))) {
                    jsonObject = reader.readObject();
                }

                if (jsonObject.containsKey(ClientToServerModel.HEARTBEAT_REQUEST.toStringValue())) {
                    sendHeartbeat();
                } else if (jsonObject.containsKey(ClientToServerModel.TERMINAL_LATENCY.toStringValue())) {
                    processRoundtripLatency(jsonObject);
                } else if (jsonObject.containsKey(ClientToServerModel.APPLICATION_INSTRUCTIONS.toStringValue())) {
                    processInstructions(jsonObject);
                } else if (jsonObject.containsKey(ClientToServerModel.ERROR_MSG.toStringValue())) {
                    processTerminalLog(jsonObject, ClientToServerModel.ERROR_MSG);
                } else if (jsonObject.containsKey(ClientToServerModel.WARN_MSG.toStringValue())) {
                    processTerminalLog(jsonObject, ClientToServerModel.WARN_MSG);
                } else if (jsonObject.containsKey(ClientToServerModel.INFO_MSG.toStringValue())) {
                    processTerminalLog(jsonObject, ClientToServerModel.INFO_MSG);
                } else if (jsonObject.containsKey(ClientToServerModel.DICTIONARY_REQUEST.toStringValue())) {
                    // Handle dictionary pattern request
                    final int patternId = jsonObject.getJsonNumber(ClientToServerModel.DICTIONARY_REQUEST.toStringValue()).intValue();
                    handleDictionaryRequest(patternId);
                } else {
                    log.error("Unknown message from terminal #{} : {}", uiContext.getID(), message);
                }

                if (monitor != null) monitor.onMessageProcessed(this, message);
            } catch (final Throwable e) {
                log.error("Cannot process message from terminal  #{} : {}", uiContext.getID(), message, e);
            } finally {
                if (monitor != null) monitor.onMessageUnprocessed(this, message);
            }
        } else {
            log.info("UI Context #{} is destroyed, message dropped from terminal : {}", uiContext != null ? uiContext.getID() : -1,
                    message);
        }
    }

    private void processRoundtripLatency(final JsonObject jsonObject) {
        final long roundtripLatency = TimeUnit.MILLISECONDS.convert(System.nanoTime() - lastSentPing, TimeUnit.NANOSECONDS);
        log.trace("Roundtrip measurement : {} ms from terminal #{}", roundtripLatency, uiContext.getID());
        uiContext.addRoundtripLatencyValue(roundtripLatency);

        final long terminalLatency = jsonObject.getJsonNumber(ClientToServerModel.TERMINAL_LATENCY.toStringValue()).longValue();
        log.trace("Terminal measurement : {} ms from terminal #{}", terminalLatency, uiContext.getID());
        uiContext.addTerminalLatencyValue(terminalLatency);

        final long networkLatency = roundtripLatency - terminalLatency;
        log.trace("Network measurement : {} ms from terminal #{}", networkLatency, uiContext.getID());
        uiContext.addNetworkLatencyValue(networkLatency);
    }

    private void processInstructions(final JsonObject jsonObject) {
        final String applicationInstructions = ClientToServerModel.APPLICATION_INSTRUCTIONS.toStringValue();
        loggerIn.trace("UIContext #{} : {}", this.uiContext.getID(), jsonObject);
        
        // Handle dictionary request directly and bypass normal processing
        if (jsonObject.containsKey(ClientToServerModel.DICTIONARY_REQUEST.toStringValue())) {
            final int patternId = jsonObject.getJsonNumber(ClientToServerModel.DICTIONARY_REQUEST.toStringValue()).intValue();
            handleDictionaryRequest(patternId);
            return;
        }
        
        // Handle dictionary stats request
        if (jsonObject.containsKey("Y")) { // "Y" is the key for DICTIONARY_STATS
            log.info("Client requested dictionary stats for UIContext #{}", uiContext.getID());
            logDictionary();
            return;
        }
        
        // Handle dictionary enabled/disabled status
        if (jsonObject.containsKey("X")) { // "X" is the value for DICTIONARY_ENABLED
            final boolean enabled = jsonObject.getBoolean("X");
            setDictionaryEnabled(enabled);
            log.info("Client requested dictionary mode: {}", enabled ? "enabled" : "disabled");
            return;
        }
        
        uiContext.execute(() -> {
            final JsonArray appInstructions = jsonObject.getJsonArray(applicationInstructions);
            for (int i = 0; i < appInstructions.size(); i++) {
                uiContext.fireClientData(appInstructions.getJsonObject(i));
            }
        });
    }

    private void processTerminalLog(final JsonObject json, final ClientToServerModel level) {
        final String message = json.getJsonString(level.toStringValue()).getString();
        String objectInformation = "";

        if (json.containsKey(ClientToServerModel.OBJECT_ID.toStringValue())) {
            final PObject object = uiContext.getObject(json.getJsonNumber(ClientToServerModel.OBJECT_ID.toStringValue()).intValue());
            objectInformation = object == null ? "NA" : object.toString();
        }

        switch (level) {
            case INFO_MSG:
                if (log.isInfoEnabled())
                    log.info(MSG_RECEIVED, uiContext.getID(), objectInformation, message); 
                break;
            case WARN_MSG:
                if (log.isWarnEnabled())
                    log.warn(MSG_RECEIVED, uiContext.getID(), objectInformation, message);
                break;
            case ERROR_MSG:
                if (log.isErrorEnabled())
                    log.error(MSG_RECEIVED, uiContext.getID(), objectInformation, message);
                break;
            default:
                log.error("Unknown log level during terminal log processing : {}", level);
        }
    }

    /**
     * Receive from the terminal
     */
    @Override
    public void onWebSocketBinary(final byte[] payload, final int offset, final int len) {
        // Can't receive binary data from terminal (GWT limitation)
    }

    /**
     * Send round trip to the client
     */
    public void sendRoundTrip() {
        if (isAlive() && isSessionOpen()) {
            lastSentPing = System.nanoTime();
            beginObject();
            encode(ServerToClientModel.ROUNDTRIP_LATENCY, null);
            endObject();
            flush0();
        }
    }

    private void sendHeartbeat() {
        if (!isAlive() || !isSessionOpen()) return;
        uiContext.acquire();
        try {
            beginObject();
            encode(ServerToClientModel.HEARTBEAT, null);
            endObject();
            flush0();
        } finally {
            uiContext.release();
        }
    }

    public void flush() {
        if (isAlive() && isSessionOpen()) flush0();
    }

    void flush0() {
        try {
            websocketPusher.flush();
        } catch (final IOException e) {
            log.error("Can't write on the websocket for #{}, so we destroy the application", uiContext.getID(), e);
            uiContext.onDestroy();
        }
    }

    public void close() {
        if (isSessionOpen()) {
            final UIContext context = this.uiContext;
            log.info("Closing websocket programmatically for UIContext #{}", context == null ? null : context.getID());
            session.close();
        }
    }

    public void disconnect() {
        if (isSessionOpen()) {
            final UIContext context = this.uiContext;
            log.info("Disconnecting websocket programmatically for UIContext #{}", context == null ? null : context.getID());
            try {
                session.disconnect();
            } catch (final IOException e) {
                log.error("Unable to disconnect session for UIContext #{}", context == null ? null : context.getID(), e);
            }
        }
    }

    private boolean isAlive() {
        return uiContext != null && uiContext.isAlive();
    }

    private boolean isSessionOpen() {
        return session != null && session.isOpen();
    }

    @Override
    public void beginObject() {
        // Nothing to do
    }

    @Override
    public void endObject() {
        encode(ServerToClientModel.END, null);
    }

    @Override
    public void encode(final ServerToClientModel model, final Object value) {
        if (UIContext.get() == null) {
            log.warn("encode in websocket without current ui context acquired", new Exception());
            uiContext.acquire();
            try {
                encode(model, value);
            } finally {
                uiContext.release();
            }
            return;
        }

        // Count frames for statistics
        if (model != null && model != ServerToClientModel.END) {
            dictionary.incrementTotalFrames();
        }

        // For END frames, flush any pending batch first
        if (model == ServerToClientModel.END) {
            if (dictionaryEnabled && !currentBatch.isEmpty()) {
                log.debug("END marker - flushing current batch of size {}", currentBatch.size());
                flushCurrentBatch();
            }
            
            // Always encode END directly
            try {
                if (loggerOut.isTraceEnabled())
                    loggerOut.trace("UIContext #{} : {} {}", this.uiContext.getID(), model, value);
                websocketPusher.encode(model, value);
                if (listener != null) listener.onOutgoingPonyFrame(model, value);
            } catch (final IOException e) {
                log.error("Can't write on the websocket for UIContext #{}, so we destroy the application", uiContext.getID(), e);
                uiContext.destroy();
            }
            return;
        }

        // Skip dictionary for critical protocol frames
        if (!dictionaryEnabled || isControlFrame(model)) {
            try {
                if (loggerOut.isTraceEnabled())
                    loggerOut.trace("UIContext #{} : {} {}", this.uiContext.getID(), model, value);
                websocketPusher.encode(model, value);
                if (listener != null) listener.onOutgoingPonyFrame(model, value);
            } catch (final IOException e) {
                log.error("Can't write on the websocket for UIContext #{}, so we destroy the application", uiContext.getID(), e);
                uiContext.destroy();
            }
            return;
        }

        // Process with dictionary for application frames
        try {
            final ModelValuePair pair = new ModelValuePair(model, value);
            
            // Skip dictionary specific frames like DICTIONARY_*
            if (model == ServerToClientModel.DICTIONARY_PATTERN_START ||
                model == ServerToClientModel.DICTIONARY_PATTERN_END ||
                model == ServerToClientModel.DICTIONARY_REFERENCE) {
                
                websocketPusher.encode(model, value);
                if (listener != null) listener.onOutgoingPonyFrame(model, value);
                return;
            }
            
            // Check if this is an array value which is common in UI events
            boolean containsArray = value != null && value.getClass().isArray();
            if (containsArray && log.isDebugEnabled()) {
                String arrayStr = "unknown array";
                if (value instanceof int[]) {
                    arrayStr = Arrays.toString((int[])value);
                } else if (value instanceof boolean[]) {
                    arrayStr = Arrays.toString((boolean[])value);
                } else if (value instanceof Object[]) {
                    arrayStr = Arrays.toString((Object[])value);
                } else if (value instanceof double[]) {
                    arrayStr = Arrays.toString((double[])value);
                } else if (value instanceof float[]) {
                    arrayStr = Arrays.toString((float[])value);
                }
                log.debug("Processing array value for model {} in UIContext #{}: {}", 
                         model, uiContext.getID(), arrayStr);
            }
            
            // Process TYPE commands specially (start new dictionary contexts for this frame)
            if (isTypeCommand(model)) {
                // Flush any pending batches to ensure clean command sequence
                if (!currentBatch.isEmpty()) {
                    log.debug("TYPE command - flushing previous batch of size {}", currentBatch.size());
                    flushCurrentBatch();
                }
                
                // Start a new batch with this type command
                currentBatch.add(pair);
                log.debug("Starting new batch with TYPE command: {}", model);
                return;
            }
            
            // For WIDGET_TYPE commands, always include them in the current batch
            // as they're critical for proper widget initialization
            if (model == ServerToClientModel.WIDGET_TYPE) {
                currentBatch.add(pair);
                log.debug("Added WIDGET_TYPE to batch (size: {})", currentBatch.size());
                return;
            }
            
            // Check if we already have a batch in progress
            if (!currentBatch.isEmpty()) {
                // Add to current batch first to build up the command sequence
                currentBatch.add(pair);
                log.debug("Added to existing batch (size: {})", currentBatch.size());
                
                // Check for matching patterns after adding to batch
                List<ModelValuePair> testPattern = new ArrayList<>(currentBatch);
                Integer patternId = dictionary.getPatternId(testPattern);
                
                if (patternId != null) {
                    // Use existing pattern reference instead of flushing the batch
                    log.debug("Found pattern match ID {} for batch of size {}", patternId, currentBatch.size());
                    
                    if (loggerOut.isTraceEnabled())
                        loggerOut.trace("UIContext #{} : DICTIONARY_REFERENCE {}", this.uiContext.getID(), patternId);
                    websocketPusher.encode(ServerToClientModel.DICTIONARY_REFERENCE, patternId);
                    if (listener != null) listener.onOutgoingPonyFrame(ServerToClientModel.DICTIONARY_REFERENCE, patternId);
                    
                    // Count as compressed frames
                    dictionary.incrementCompressedFrames();
                    
                    currentBatch.clear();
                    return;
                }
                
                // Check if we've reached the batch threshold
                if (currentBatch.size() >= BATCH_THRESHOLD) {
                    log.debug("Batch threshold reached ({}) - flushing", BATCH_THRESHOLD);
                    flushCurrentBatch();
                }
                return;
            }
            
            // No batch in progress, start a new one (typically this is for single frame updates)
            currentBatch.add(pair);
            log.debug("Started new batch with non-TYPE command: {}", model);
        } catch (final IOException e) {
            log.error("Can't write on the websocket for UIContext #{}, so we destroy the application", uiContext.getID(), e);
            uiContext.destroy();
        }
    }

    /**
     * Check if a model is a critical control frame that should bypass dictionary
     */
    private boolean isControlFrame(final ServerToClientModel model) {
        // treat any UINT31-typed model as protocol frame to bypass dictionary
        if (model.getTypeModel() == ValueTypeModel.UINT31) return true;
        switch (model) {
            case CREATE_CONTEXT:
            case OPTION_FORMFIELD_TABULATION:
            case HEARTBEAT_PERIOD:
            case HEARTBEAT:
            case ROUNDTRIP_LATENCY:
            case END:
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Handle dictionary pattern request from client
     */
    public void handleDictionaryRequest(final int patternId) {
        List<ModelValuePair> pattern = dictionary.getPattern(patternId);
        if (pattern != null) {
            if (log.isDebugEnabled()) {
                log.debug("Sending dictionary pattern {} to client (size: {})", patternId, pattern.size());
            }
            
            try {
                uiContext.acquire();
                try {
                    beginObject();
                    encode(ServerToClientModel.DICTIONARY_PATTERN_START, patternId);
                    for (ModelValuePair pair : pattern) {
                        encode(pair.getModel(), pair.getValue());
                    }
                    encode(ServerToClientModel.DICTIONARY_PATTERN_END, null);
                    endObject();
                    flush0();
                } finally {
                    uiContext.release();
                }
            } catch (Exception e) {
                log.error("Error sending dictionary pattern to client", e);
            }
        } else {
            log.warn("Client requested unknown dictionary pattern: {}", patternId);
        }
    }

    /**
     * Enable or disable dictionary compression
     */
    public void setDictionaryEnabled(boolean enabled) {
        if (this.dictionaryEnabled != enabled) {
            log.info("Dictionary compression {} for UIContext #{}", 
                    enabled ? "enabled" : "disabled", 
                    uiContext != null ? uiContext.getID() : "?");
            
            this.dictionaryEnabled = enabled;
            if (!enabled) {
                // Clear current batch and dictionary when disabling
                currentBatch.clear();
                dictionary.clear();
            }
        }
    }

    public void sendUIComponent(String componentType, String componentId, String componentText) {
        try {
            beginObject();
            encode(ServerToClientModel.UI_COMPONENT_TYPE, componentType);
            encode(ServerToClientModel.UI_COMPONENT_ID, componentId);
            encode(ServerToClientModel.UI_COMPONENT_TEXT, componentText);
            endObject();
            flush();
        } catch (Exception e) {
            log.error("Cannot send UI component to client for UIContext #{}", uiContext.getID(), e);
        }
    }

    private enum NiceStatusCode {

        NORMAL(StatusCode.NORMAL, "Normal closure"),
        SHUTDOWN(StatusCode.SHUTDOWN, "Shutdown"),
        PROTOCOL(StatusCode.PROTOCOL, "Protocol error"),
        BAD_DATA(StatusCode.BAD_DATA, "Received bad data"),
        UNDEFINED(StatusCode.UNDEFINED, "Undefined"),
        NO_CODE(StatusCode.NO_CODE, "No code present"),
        NO_CLOSE(StatusCode.NO_CLOSE, "Abnormal connection closed"),
        ABNORMAL(StatusCode.ABNORMAL, "Abnormal connection closed"),
        BAD_PAYLOAD(StatusCode.BAD_PAYLOAD, "Not consistent message"),
        POLICY_VIOLATION(StatusCode.POLICY_VIOLATION, "Received message violates policy"),
        MESSAGE_TOO_LARGE(StatusCode.MESSAGE_TOO_LARGE, "Message too big"),
        REQUIRED_EXTENSION(StatusCode.REQUIRED_EXTENSION, "Required extension not sent"),
        SERVER_ERROR(StatusCode.SERVER_ERROR, "Server error"),
        SERVICE_RESTART(StatusCode.SERVICE_RESTART, "Server restart"),
        TRY_AGAIN_LATER(StatusCode.TRY_AGAIN_LATER, "Server overload"),
        FAILED_TLS_HANDSHAKE(StatusCode.POLICY_VIOLATION, "Failure handshake");

        private final int statusCode;
        private final String message;

        NiceStatusCode(final int statusCode, final String message) {
            this.statusCode = statusCode;
            this.message = message;
        }

        public static String getMessage(final int statusCode) {
            final List<NiceStatusCode> codes = Arrays.stream(values())
                    .filter(niceStatusCode -> niceStatusCode.statusCode == statusCode).collect(Collectors.toList());
            if (!codes.isEmpty()) {
                return codes.get(0).toString();
            } else {
                log.error("No matching status code found for {}", statusCode);
                return String.valueOf(statusCode);
            }
        }

        @Override
        public String toString() {
            return message + " (" + statusCode + ")";
        }

    }

    public ServletUpgradeRequest getRequest() {
        return request;
    }

    public void setRequest(final ServletUpgradeRequest request) {
        this.request = request;
    }

    public void setApplicationManager(final ApplicationManager applicationManager) {
        this.applicationManager = applicationManager;
    }

    public void setMonitor(final WebsocketMonitor monitor) {
        this.monitor = monitor;
    }

    public void setContext(final TxnContext context) {
        this.context = context;
    }

    public void setListener(final Listener listener) {
        this.listener = listener;
        this.websocketPusher.setWebSocketListener(listener);
        if (!(session instanceof Container)) {
            log.warn("Unrecognized session type {} for {}", session == null ? null : session.getClass(), uiContext);
            return;
        }
        final ExtensionStack extensionStack = ((Container) session).getBean(ExtensionStack.class);
        if (extensionStack == null) {
            log.warn("No Extension Stack for {}", uiContext);
            return;
        }
        final PonyPerMessageDeflateExtension extension = extensionStack.getBean(PonyPerMessageDeflateExtension.class);
        if (extension == null) {
            log.warn("Missing PonyPerMessageDeflateExtension from Extension Stack for {}", uiContext);
            return;
        }
        extension.setWebSocketListener(listener);
    }

    public interface Listener {

        void onOutgoingPonyFrame(ServerToClientModel model, Object value);

        void onOutgoingPonyFramesBytes(int bytes);

        void onOutgoingWebSocketFrame(int headerLength, int payloadLength);

        void onIncomingText(String text);

        void onIncomingWebSocketFrame(int headerLength, int payloadLength);

    }

    /**
     * Flush current batch before handling critical frames like END
     * to ensure proper UI response cycle completion
     */
    private void flushCurrentBatch() {
        if (currentBatch.isEmpty()) return;
        
        try {
            if (loggerOut.isTraceEnabled())
                loggerOut.trace("UIContext #{} : Flushing batch of size {}", this.uiContext.getID(), currentBatch.size());
            
            // Check if batch has any TYPE_* instructions to ensure valid pattern
            boolean hasTypeCommand = false;
            ServerToClientModel foundType = null;
            for (ModelValuePair pair : currentBatch) {
                if (isTypeCommand(pair.getModel())) {
                    hasTypeCommand = true;
                    foundType = pair.getModel();
                    break;
                }
            }
            
            // Only try to save patterns that have a TYPE_* command to ensure proper replay
            Integer newId = null;
            if (hasTypeCommand) {
                newId = dictionary.recordPattern(currentBatch);
            }
            
            if (newId != null) {
                // Send pattern definition to client
                if (loggerOut.isTraceEnabled())
                    loggerOut.trace("UIContext #{} : Recording new pattern {} (size: {})", 
                            this.uiContext.getID(), newId, currentBatch.size());
                    
                websocketPusher.encode(ServerToClientModel.DICTIONARY_PATTERN_START, newId);
                if (listener != null) listener.onOutgoingPonyFrame(ServerToClientModel.DICTIONARY_PATTERN_START, newId);
                
                // Send all frames in the pattern
                for (ModelValuePair p : currentBatch) {
                    websocketPusher.encode(p.getModel(), p.getValue());
                    if (listener != null) listener.onOutgoingPonyFrame(p.getModel(), p.getValue());
                }
                
                websocketPusher.encode(ServerToClientModel.DICTIONARY_PATTERN_END, null);
                if (listener != null) listener.onOutgoingPonyFrame(ServerToClientModel.DICTIONARY_PATTERN_END, null);
            } else {
                // Send all frames directly without pattern recording
                for (ModelValuePair p : currentBatch) {
                    websocketPusher.encode(p.getModel(), p.getValue());
                    if (listener != null) listener.onOutgoingPonyFrame(p.getModel(), p.getValue());
                }
            }
        } catch (IOException e) {
            log.error("Error flushing batch", e);
        } finally {
            currentBatch.clear();
        }
    }

    /**
     * Helper method to check if a model is a TYPE_* command
     */
    private boolean isTypeCommand(final ServerToClientModel model) {
        return model == ServerToClientModel.TYPE_CREATE || 
               model == ServerToClientModel.TYPE_UPDATE || 
               model == ServerToClientModel.TYPE_ADD || 
               model == ServerToClientModel.TYPE_REMOVE || 
               model == ServerToClientModel.TYPE_ADD_HANDLER || 
               model == ServerToClientModel.TYPE_REMOVE_HANDLER || 
               model == ServerToClientModel.TYPE_GC;
    }

    /**
     * Log the current dictionary contents and statistics to help with debugging
     */
    private void logDictionary() {
        try {
            StringBuilder sb = new StringBuilder("\n=== Dictionary Contents (UIContext #")
                .append(uiContext.getID())
                .append(") ===\n");
            
            // Add dictionary status
            sb.append("Dictionary enabled: ").append(dictionaryEnabled).append("\n");
            
            // Add compression statistics
            long totalFrames = dictionary.getTotalFramesSent();
            long compressedFrames = dictionary.getCompressedFramesSent();
            double compressionRatio = totalFrames > 0 ? (double)compressedFrames / totalFrames : 0;
            
            sb.append("\n=== COMPRESSION STATS ===\n");
            sb.append("Total frames sent: ").append(totalFrames).append("\n");
            sb.append("Compressed frames: ").append(compressedFrames).append("\n");
            sb.append("Compression ratio: ").append(String.format("%.2f%%", compressionRatio * 100)).append("\n\n");
            
            // Get dictionary patterns
            Map<Integer, List<ModelValuePair>> patterns = dictionary.getPatternMap();
            sb.append("Total patterns: ").append(patterns.size()).append("\n\n");
            
            // Show patterns (limited to 5 for readability)
            int count = 0;
            for (Map.Entry<Integer, List<ModelValuePair>> entry : patterns.entrySet()) {
                if (count++ >= 5) {
                    sb.append("... and ").append(patterns.size() - 5).append(" more patterns\n");
                    break;
                }
                
                int id = entry.getKey();
                List<ModelValuePair> pattern = entry.getValue();
                
                sb.append("Pattern #").append(id).append(" (").append(pattern.size()).append(" pairs):\n");
                for (ModelValuePair pair : pattern) {
                    sb.append("  ").append(pair.getModel()).append(" = ");
                    Object pairValue = pair.getValue();
                    if (pairValue instanceof String) {
                        // Truncate long strings
                        String strValue = (String) pairValue;
                        sb.append("\"").append(strValue.length() > 20 ? 
                            strValue.substring(0, 20) + "..." : strValue).append("\"");
                    } else {
                        sb.append(pairValue);
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }
            
            // End of logging
            sb.append("================================\n");
            
            // Log the information
            log.info(sb.toString());
        } catch (Exception e) {
            log.warn("Error logging dictionary contents", e);
        }
    }
}

