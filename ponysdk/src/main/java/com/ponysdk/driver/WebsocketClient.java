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

package com.ponysdk.driver;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.ClientEndpointConfig.Configurator;
import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Extension;
import javax.websocket.HandshakeResponse;
import javax.websocket.MessageHandler;
import javax.websocket.MessageHandler.Whole;
import javax.websocket.Session;

import com.ponysdk.core.model.ClientToServerModel;
import com.ponysdk.core.model.ServerToClientModel;
import com.ponysdk.core.model.ValueTypeModel;
import com.ponysdk.core.terminal.UIBuilder;
import com.ponysdk.core.terminal.instruction.PTInstruction;
import com.ponysdk.core.terminal.model.BinaryModel;
import com.ponysdk.core.terminal.model.ReaderBuffer;
import com.ponysdk.core.useragent.Browser;
import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class WebsocketClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(WebsocketClient.class);

    private volatile Session session;

    private final Map<Integer, List<BinaryModel>> patternDictionary = new HashMap<>();
    private boolean receivingPattern = false;
    private int currentPatternId = -1;
    private final List<BinaryModel> currentPattern = new ArrayList<>();


    private final static List<String> USER_AGENT = List.of("PonyDriver");
    private final static Configurator configurator = new Configurator() {

        @Override
        public void afterResponse(final HandshakeResponse hr) {
            super.afterResponse(hr);
            log.debug("Response Headers : {}", hr.getHeaders());
        }

        @Override
        public void beforeRequest(final Map<String, List<String>> headers) {
            super.beforeRequest(headers);
            headers.put("User-Agent", USER_AGENT);
            log.debug("Request Headers : {}", headers);
        }
    };

    private final MessageHandler.Whole<ByteBuffer> handler;
    private final List<Extension> extensions;
    private PonySessionListener sessionListener;

    public WebsocketClient(final Whole<ByteBuffer> handler, final PonyBandwithListener bandwidthListener,
            final PonySessionListener sessionListener) {
        super();
        this.handler = handler;
        this.extensions = List.of(new PonyDriverPerMessageDeflateExtension(bandwidthListener));
        this.sessionListener = sessionListener;
    }

    

    public void connect(final URI uri) throws Exception {
        if (session != null) session.close();
        final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().configurator(configurator).extensions(extensions)
            .build();
        final ClientManager client = ClientManager.createClient();
        client.getProperties().put(ClientProperties.REDIRECT_ENABLED, true);
        final Lock lock = new ReentrantLock();
        final Condition condition = lock.newCondition();
        client.connectToServer(new Endpoint() {

            @Override
            public void onOpen(final Session session, final EndpointConfig config) {
                lock.lock();
                try {
                    WebsocketClient.this.session = session;
                    condition.signal();
                    session.addMessageHandler(handler);
                } finally {
                    lock.unlock();
                }
                sessionListener.onOpen(session);
            }

            @Override
            public void onClose(final Session session, final CloseReason closeReason) {
                super.onClose(session, closeReason);
                log.debug("Session {} closed : {} {}", session.getId(), closeReason.getCloseCode(), closeReason.getReasonPhrase());
                sessionListener.onClose(session, closeReason);
            }

            @Override
            public void onError(final Session session, final Throwable thr) {
                super.onError(session, thr);
                log.error("Session {} error", session.getId(), thr);
                sessionListener.onError(session, thr);
            }

        }, cec, uri);
        lock.lock();
        try {
            if (session == null && !condition.await(5, TimeUnit.MINUTES)) {
                log.error("Connection to {} failed after 5 minutes timeout", uri);
                return;
            }
        } finally {
            lock.unlock();
        }
        log.debug("Connected to {} with session ID {}", uri, session.getId());
    }

    public void sendMessage(final String message) throws IOException {
        session.getBasicRemote().sendText(message);
    }


/**
 * Process binary data from WebSocket with dictionary support
 * 
 * @param buffer The received binary data
 * @param uiBuilder The UI builder to update
 */
private void processArrayBuffer(ArrayBuffer buffer, UIBuilder uiBuilder) {
    final Uint8Array view = window.newUint8Array(buffer, 0, buffer.getByteLength());
    
    ReaderBuffer reader = new ReaderBuffer(view);
    BinaryModel binaryModel;
    
    while ((binaryModel = reader.readBinaryModel()) != null) {
        final ServerToClientModel model = binaryModel.getModel();
        
        if (model == ServerToClientModel.DICTIONARY_REFERENCE) {
            // Handle dictionary reference
            int patternId = binaryModel.getIntValue();
            List<BinaryModel> pattern = patternDictionary.get(patternId);
            
            if (pattern != null) {
                // Apply pattern
                for (BinaryModel modelValue : pattern) {
                    uiBuilder.updateMainTerminal(modelValue);
                }
            } else {
                // Request pattern
                if (log.isLoggable(Level.INFO)) {
                    log.info("Requesting unknown dictionary pattern: " + patternId);
                }
                requestDictionaryPattern(patternId);
            }
        }
        else if (model == ServerToClientModel.DICTIONARY_PATTERN_START) {
            // Start recording pattern
            receivingPattern = true;
            currentPatternId = binaryModel.getIntValue();
            currentPattern.clear();
        }
        else if (model == ServerToClientModel.DICTIONARY_PATTERN_END) {
            // Save pattern
            if (receivingPattern && currentPatternId != -1) {
                patternDictionary.put(currentPatternId, new ArrayList<>(currentPattern));
                receivingPattern = false;
                currentPatternId = -1;
                currentPattern.clear();
            }
        }
        else {
            // Record if receiving a pattern
            if (receivingPattern) {
                currentPattern.add(cloneBinaryModel(binaryModel));
            }
            
            // Normal processing
            uiBuilder.updateMainTerminal(binaryModel);
        }
    }
}

    /**
     * Request a dictionary pattern from the server
     * 
     * @param patternId The ID of the requested pattern
     */
    private void requestDictionaryPattern(int patternId) {
        final elemental.json.JsonObject instruction = elemental.json.Json.createObject();
        instruction.put(ClientToServerModel.DICTIONARY_REQUEST.toStringValue(), patternId);
        send(instruction.toJson());
    }

    /**
     * Create a clone of a BinaryModel
     * 
     * @param model The model to clone
     * @return A clone of the input model
     */
    private BinaryModel cloneBinaryModel(BinaryModel model) {
        final BinaryModel clone = new BinaryModel();
        
        final ServerToClientModel modelType = model.getModel();
        final ValueTypeModel valueType = modelType.getTypeModel();
        
        if (valueType == ValueTypeModel.NULL) {
            clone.init(modelType, model.getSize());
        } else if (valueType == ValueTypeModel.BOOLEAN) {
            clone.init(modelType, model.getBooleanValue(), model.getSize());
        } else if (valueType == ValueTypeModel.BYTE) {
            clone.init(modelType, model.getByteValue(), model.getSize());
        } else if (valueType == ValueTypeModel.SHORT) {
            clone.init(modelType, model.getShortValue(), model.getSize());
        } else if (valueType == ValueTypeModel.INTEGER) {
            clone.init(modelType, model.getIntValue(), model.getSize());
        } else if (valueType == ValueTypeModel.LONG) {
            clone.init(modelType, model.getLongValue(), model.getSize());
        } else if (valueType == ValueTypeModel.DOUBLE) {
            clone.init(modelType, model.getDoubleValue(), model.getSize());
        } else if (valueType == ValueTypeModel.FLOAT) {
            clone.init(modelType, model.getFloatValue(), model.getSize());
        } else if (valueType == ValueTypeModel.STRING) {
            clone.init(modelType, model.getStringValue(), model.getSize());
        } else if (valueType == ValueTypeModel.ARRAY) {
            clone.init(modelType, model.getArrayValue(), model.getSize());
        } else {
            // Default fallback
            clone.init(modelType, model.getSize());
        }
        
        return clone;
    }

    @Override
    public void close() {
        if (session != null) {
            final String id = session.getId();
            try {
                session.close();
            } catch (final IOException e) {
                log.error("Failed to close session {}", id, e);
            }
        }
    }

    public String getSessionId() {
        return session != null ? session.getId() : null;
    }

    public void setSessionListener(PonySessionListener sessionListener){
        this.sessionListener = sessionListener;
    }
}
