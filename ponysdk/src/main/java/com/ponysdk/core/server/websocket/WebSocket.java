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
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.Json;
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
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

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
    private static final int BATCH_THRESHOLD = 10;
    private boolean dictionaryEnabled = true; // Enabled by default

    private final List<String> bufferedInstructions = new ArrayList<>();
    private String lastPrediction = null;
    private int instructionsProcessedCount=0;
    private final Object predictionLock = new Object();

    private final List<String> recentInstructions = new ArrayList<>();
    private final Map<List<String>, Integer> patternFrequency = new HashMap<>();
    private static final int PATTERN_LENGTH = 3;
    private static final int MIN_FREQUENCY = 5; // Minimum occurrences to consider as a pattern
    private static final int MAX_HISTORY = 1000; // Maximum number of instructions to keep in history

    // Buffer for streaming pattern matching
    private final ArrayDeque<String> instructionBuffer = new ArrayDeque<>(3);
    
    private static final class TrieNode {
            final java.util.Map<String, TrieNode> child = new java.util.HashMap<>();
            boolean isTerminal;            // true when we have a-b-c
    }

    private static TrieNode DICT_TRIE = new TrieNode();

    private static void buildDictionaryTrie(List<List<String>> patterns) {
            for (List<String> p : patterns) {
                TrieNode n = DICT_TRIE;
                for (String ev : p) n = n.child.computeIfAbsent(ev, k -> new TrieNode());
                n.isTerminal = true;
            }
    }

    public WebSocket() {
        // Initialize with some example patterns for testing
        List<List<String>> examplePatterns = new ArrayList<>();
        
        // Example patterns based on common UI interactions
        // These are just examples - in production, you would derive these from actual usage
        List<String> pattern1 = new ArrayList<>();
        pattern1.add("{\"0\":6,\"g\":2}"); // Click event on button #6
        pattern1.add("{\"0\":7,\"g\":2}"); // Click event on button #7
        pattern1.add("{\"0\":8,\"g\":2}"); // Click event on button #8
        examplePatterns.add(pattern1);
        
        List<String> pattern2 = new ArrayList<>();
        pattern2.add("{\"0\":1,\"C\":\"value\"}"); // Value change on widget #1
        pattern2.add("{\"0\":2,\"C\":\"value\"}"); // Value change on widget #2
        pattern2.add("{\"0\":3,\"C\":\"value\"}"); // Value change on widget #3
        examplePatterns.add(pattern2);
        
        // Build the trie with these example patterns
        buildDictionaryTrie(examplePatterns);
        
        log.info("Initialized pattern matcher with {} example patterns", examplePatterns.size());
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
        uiContext.onDestroy();
    }

    @Override
    public void onWebSocketClose(final int statusCode, final String reason) {
        if (log.isInfoEnabled())
            log.info("WebSocket closed on UIContext #{} : {}, reason : {}", uiContext.getID(),
                    NiceStatusCode.getMessage(statusCode), Objects.requireNonNullElse(reason, ""));
        
        // Flush any remaining buffered instructions before destroying the context
        flushInstructionBuffer();
        
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
                JsonObject currentInstructionJson = appInstructions.getJsonObject(i);
                String currentInstructionString = currentInstructionJson.toString();

                synchronized (predictionLock) {
                    instructionsProcessedCount++;
                    
                    // Use the pattern matcher instead of simple buffering
                    processInstructionWithPatternMatching(currentInstructionString);
                }
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

        // For END frames, flush any pending batch first
        if (dictionaryEnabled && model == ServerToClientModel.END && !currentBatch.isEmpty()) {
            flushCurrentBatch();
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
            
            // Process TYPE commands specially (start new dictionary contexts for this frame)
            if (isTypeCommand(model)) {
                // Flush any pending batches to ensure clean command sequence
                if (!currentBatch.isEmpty()) {
                    flushCurrentBatch();
                }
                
                // Start a new batch with this type command
                currentBatch.add(pair);
                return;
            }
            
            // For WIDGET_TYPE commands, always include them in the current batch
            // as they're critical for proper widget initialization
            if (model == ServerToClientModel.WIDGET_TYPE) {
                currentBatch.add(pair);
                return;
            }
            
            // Check if we already have a batch in progress
            if (!currentBatch.isEmpty()) {
                // First try to find the complete pattern including this pair
                List<ModelValuePair> testPattern = new ArrayList<>(currentBatch);
                testPattern.add(pair);
                
                Integer patternId = dictionary.getPatternId(testPattern);
                if (patternId != null) {
                    // Use existing pattern reference
                    if (loggerOut.isTraceEnabled())
                        loggerOut.trace("UIContext #{} : DICTIONARY_REFERENCE {}", this.uiContext.getID(), patternId);
                    websocketPusher.encode(ServerToClientModel.DICTIONARY_REFERENCE, patternId);
                    if (listener != null) listener.onOutgoingPonyFrame(ServerToClientModel.DICTIONARY_REFERENCE, patternId);
                    currentBatch.clear();
                    return;
                }
                
                // Add to current batch and check threshold
                currentBatch.add(pair);
                if (currentBatch.size() >= BATCH_THRESHOLD) {
                    flushCurrentBatch();
                }
                return;
            }
            
            // No batch in progress, start a new one (typically this is for single frame updates)
            currentBatch.add(pair);
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
     * Demonstrates a JSON POST request using HttpURLConnection.
     * This method is placed on the server-side within WebSocket.java.
     * It executes the network request in a separate thread to avoid
     * blocking the main WebSocket processing thread.
     */
    private void sendJsonPostRequestUsingHttpURLConnection(List<String> instructionsToSend) {
        // Wrap the network call in a new thread to avoid blocking the current thread.
        new Thread(() -> {
            HttpURLConnection con = null;
            try {
                // Define the URL of your FastAPI server endpoint.
                // Ensure your FastAPI server is running on http://127.0.0.1:8000/.
                URL url = new URL("http://127.0.0.1:8000/generate"); 
                
                // Open a connection to the URL.
                con = (HttpURLConnection) url.openConnection();
                
                // Set the request method to POST.
                con.setRequestMethod("POST");
                
                // Set Content-Type header to "application/json".
                con.setRequestProperty("Content-Type", "application/json");
                
                // Set Accept header to "application/json".
                con.setRequestProperty("Accept", "application/json");
                
                // 5. Enable output for the connection.
                //    This tells the connection that we intend to write data to the output stream.
                con.setDoOutput(true);

                // 6. Define the JSON string to send.
                //    This is the payload that your FastAPI server expects.
                String instructionsContent = instructionsToSend.stream()
                .map(Object::toString)
                .collect(Collectors.joining("\n"));

                // Use JsonObjectBuilder to properly format the JSON string,
                // which will handle escaping of special characters like newlines and quotes.
                JsonObjectBuilder jsonBuilder = Json.createObjectBuilder()
                    .add("input_text", instructionsContent)
                    .add("max_length", 64);
                
                String jsonInputString = jsonBuilder.build().toString();
                
                //String jsonInputString = "{\"input_text\": \"PButton#6, text=Send Custom UI Component, html=null : {\\\"0\\\":6,\\\"g\\\":2,\\\"f\\\":[116,67,106,15,1,false,false,false,false],\\\"d\\\":[10,52,23,182]}\\nPButton#7, text=Create Dynamic Components, html=null : {\\\"0\\\":7,\\\"g\\\":2,\\\"f\\\":[242,57,51,5,1,false,false,false,false],\\\"d\\\":[191,52,23,186]}\\nPButton#8, text=Static Component, html=null : {\\\"0\\\":8,\\\"g\\\":2,\\\"f\\\":[405,59,28,7,1,false,false,false,false],\\\"d\\\":[377,52,23,119]}\", \"max_length\": 64}";
                
                // 7. Write the JSON string to the connection's output stream.
                //    The data is converted to bytes using UTF-8 encoding.
                try (OutputStream os = con.getOutputStream()) {
                    byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                // Read the response from the server.
                try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    // Log the HTTP response code and the received response body.
                    log.info("FastAPI HTTP Response Code: " + con.getResponseCode() + ", Body: " + response);

                    String prediction = null;
                    try{
                        // Parse the JSON response to extract the 'instruction' field
                        try (JsonReader jsonResponseReader = Json.createReader(new StringReader(response.toString()))) {
                            JsonObject jsonResponse = jsonResponseReader.readObject();
                            if (jsonResponse.containsKey("generated_text")) {
                                prediction = jsonResponse.getString("generated_text");
                            }
                        }

                        log.info("FastAPI HTTP Response Code: " + con.getResponseCode() + ", Body: " + response);

                        // BEGIN : Prediction Comparison Logic (FastAPI Response)
                        if (prediction != null && !instructionsToSend.isEmpty()) {
                            String actualInstruction = instructionsToSend.get(instructionsToSend.size() - 1); // Get the last instruction sent
                            String predictedInstruction = prediction;

                            log.info("--- Instruction Comparison (FastAPI Response) ---");
                            log.info("Actual (Client) : {}", actualInstruction);
                            log.info("Predicted (FastAPI): {}", predictedInstruction);

                            double similarityScore = calculateSimilarity(actualInstruction, predictedInstruction);
                            log.info("Similarity Score: {}", String.format("%.2f", similarityScore));

                            if (actualInstruction.equals(predictedInstruction)) {
                                log.info("Result: Prediction EXACTLY MATCHED the actual instruction.");
                            } else if (similarityScore >= 0.8) { // You can adjust this threshold
                                log.warn("Result: Prediction is SIMILAR (score >= 0.8) but not exact.");
                                int diffIndex = findFirstDifferenceIndex(actualInstruction, predictedInstruction);
                                if (diffIndex != -1) {
                                    log.warn("First difference at index {}: Actual='{}', Predicted='{}'",
                                             diffIndex,
                                             (diffIndex < actualInstruction.length() ? actualInstruction.charAt(diffIndex) : "EOF"),
                                             (diffIndex < predictedInstruction.length() ? predictedInstruction.charAt(diffIndex) : "EOF"));
                                }
                            } else {
                                log.warn("Result: Prediction MISMATCH! Low similarity score.");
                                int diffIndex = findFirstDifferenceIndex(actualInstruction, predictedInstruction);
                                if (diffIndex != -1) {
                                    log.warn("First difference at index {}: Actual='{}', Predicted='{}'",
                                             diffIndex,
                                             (diffIndex < actualInstruction.length() ? actualInstruction.charAt(diffIndex) : "EOF"),
                                             (diffIndex < predictedInstruction.length() ? predictedInstruction.charAt(diffIndex) : "EOF"));
                                }
                            }
                        }
                        // END : Prediction Comparison Logic

                        synchronized (predictionLock) {
                            lastPrediction = prediction; // Update lastPrediction with the current prediction
                        }
                    } catch (Exception e) {
                        log.error("Error parsing FastAPI response or performing comparison: ", e);
                    }

                    con.disconnect();
                }
            } catch (IOException e) {
                log.error("Error sending POST request to FastAPI: ", e);
            }
        }).start(); // Start the new thread for the network call.
    }

    /**
     * Calculates a simple character-by-character similarity ratio between two strings.
     * This is a basic implementation and not equivalent to Python's difflib.SequenceMatcher.ratio().
     * Returns a value between 0.0 (no similarity) and 1.0 (exact match).
     */
    private static double calculateSimilarity(String a, String b) {
        if (a == null || b == null) {
            return 0.0;
        }
        if (a.equals(b)) {
            return 1.0;
        }

        int maxLength = Math.max(a.length(), b.length());
        if (maxLength == 0) {
            return 1.0; // Both empty strings, considered similar
        }

        int matchingCharacters = 0;
        int minLength = Math.min(a.length(), b.length());

        for (int i = 0; i < minLength; i++) {
            if (a.charAt(i) == b.charAt(i)) {
                matchingCharacters++;
            }
        }
        // Ratio based on matching characters over the longest string length
        return (double) matchingCharacters / maxLength;
    }

    private static int findFirstDifferenceIndex(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return 0; // Or -1, depending on desired behavior for nulls
        }
        int minLength = Math.min(s1.length(), s2.length());
        for (int i = 0; i < minLength; i++) {
            if (s1.charAt(i) != s2.charAt(i)) {
                return i;
            }
        }
        if (s1.length() != s2.length()) {
            return minLength; // One string is a prefix of the other
        }
        return -1; // Strings are identical
    }

    /**
     * Process a single instruction through the pattern matcher.
     * If it forms a known pattern with buffered instructions, send them as a batch.
     * If it cannot form any pattern, send the oldest instruction individually.
     */
    private void processInstructionWithPatternMatching(String instruction) {
        // Learn from this instruction
        learnPatterns(instruction);
        
        instructionBuffer.addLast(instruction);
        
        while (!instructionBuffer.isEmpty()) {
            // Try to match the current buffer against the trie
            TrieNode node = DICT_TRIE;
            boolean possibleMatch = true;
            int index = 0;
            
            // Create a copy of the buffer for iteration to avoid concurrent modification
            List<String> bufferCopy = new ArrayList<>(instructionBuffer);
            
            for (String event : bufferCopy) {
                if (!node.child.containsKey(event)) {
                    // This prefix cannot match any pattern
                    possibleMatch = false;
                    break;
                }
                
                node = node.child.get(event);
                index++;
                
                // If we've matched a complete pattern of length 3
                if (index == 3 && node.isTerminal) {
                    // We have a complete match, send these 3 instructions as a batch
                    List<String> matchedPattern = new ArrayList<>(3);
                    for (int i = 0; i < 3; i++) {
                        matchedPattern.add(instructionBuffer.pollFirst());
                    }
                    
                    log.info("Pattern match found! Sending batch of 3 instructions");
                    sendJsonPostRequestUsingHttpURLConnection(matchedPattern);
                    break; // Restart with the remaining buffer
                }
            }
            
            if (!possibleMatch) {
                // The first instruction cannot be part of any pattern, send it individually
                String singleInstruction = instructionBuffer.pollFirst();
                log.info("No pattern match possible for: {}", singleInstruction.length() > 50 ? 
                         singleInstruction.substring(0, 50) + "..." : singleInstruction);
                sendJsonPostRequestUsingHttpURLConnection(Collections.singletonList(singleInstruction));
            } else if (instructionBuffer.size() < 3) {
                // We have a partial match but not enough instructions to complete it
                log.debug("Partial pattern match, buffering {} instructions", instructionBuffer.size());
                break;
            } else {
                // We have enough instructions but no complete match yet
                break;
            }
        }
        
        // Safety check: ensure buffer doesn't grow beyond pattern length
        while (instructionBuffer.size() > 3) {
            instructionBuffer.pollFirst();
        }
    }

    /**
     * Flush any remaining instructions in the buffer.
     * This should be called when the WebSocket is closing or when we need to ensure
     * all buffered instructions are sent.
     */
    private void flushInstructionBuffer() {
        if (!instructionBuffer.isEmpty()) {
            log.info("Flushing {} remaining buffered instructions", instructionBuffer.size());
            List<String> remainingInstructions = new ArrayList<>(instructionBuffer);
            instructionBuffer.clear();
            sendJsonPostRequestUsingHttpURLConnection(remainingInstructions);
        }
    }

    /**
     * Learn patterns from actual usage by tracking instruction sequences.
     * This method should be called periodically to update the trie with new patterns.
     */
    private void learnPatterns(String newInstruction) {
        // Add the new instruction to our recent history
        recentInstructions.add(newInstruction);
        
        // If we have enough history to form a pattern
        if (recentInstructions.size() >= PATTERN_LENGTH) {
            // Extract the most recent pattern of length PATTERN_LENGTH
            List<String> pattern = new ArrayList<>(PATTERN_LENGTH);
            for (int i = recentInstructions.size() - PATTERN_LENGTH; i < recentInstructions.size(); i++) {
                pattern.add(recentInstructions.get(i));
            }
            
            // Update the frequency count for this pattern
            patternFrequency.put(pattern, patternFrequency.getOrDefault(pattern, 0) + 1);
            
            // If this pattern is frequent enough, add it to our trie
            if (patternFrequency.get(pattern) >= MIN_FREQUENCY) {
                // Add this pattern to our trie if it's not already there
                TrieNode node = DICT_TRIE;
                for (String event : pattern) {
                    node = node.child.computeIfAbsent(event, k -> new TrieNode());
                }
                node.isTerminal = true;
                
                log.info("Learned new pattern with frequency {}: {}", 
                         patternFrequency.get(pattern), pattern);
            }
        }
        
        // Limit the size of our history
        if (recentInstructions.size() > MAX_HISTORY) {
            recentInstructions.remove(0);
        }
    }
}

