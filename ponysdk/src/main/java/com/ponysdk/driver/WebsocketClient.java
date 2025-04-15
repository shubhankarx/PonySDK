/*
 * Copyright (c) 2017 PonySDK
 *  Owners:
 *  Luciano Broussal  <luciano.broussal AT gmail.com>
 *  Mathieu Baxrbier   <mathieu.barbier AT gmail.com>
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

import javax.json.JsonObjectBuilder;
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
import javax.json.Json;
import javax.json.JsonObject;

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

import elemental.html.ArrayBuffer;
import elemental.html.Uint8Array;
import elemental.html.Window; 
import java.util.logging.Level;
import elemental.events.MessageEvent;

class WebsocketClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(WebsocketClient.class);

    private volatile Session session;
    
    // Dictionary for storing patterns
    private final Map<Integer, List<BinaryModel>> patternDictionary = new HashMap<>();
    private boolean receivingPattern = false;
    private int currentPatternId = -1;
    private final List<BinaryModel> currentPattern = new ArrayList<>();

    // User agent & config for websocket connection
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
    private UIBuilder uiBuilder; //  New line

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
     * Set the UIBuilder that will process binary models
     */
    public void setUIBuilder(UIBuilder uiBuilder) {
        this.uiBuilder = uiBuilder;
    }
    
    // Helper method to update the UI with a BinaryModel
    private void updateUI(BinaryModel model) {
        uiBuilder.updateMainTerminal(model);
    }
    
    /**
     * Process binary data received from the WebSocket
     */
    public void processBuffer(ByteBuffer buffer) {
        try {
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            
            
            ReaderBuffer reader = new ReaderBuffer();
            reader.init(bytes);
            BinaryModel binaryModel;
            
            // Read all binary models from the buffer
            while ((binaryModel = reader.readBinaryModel()) != null) {
                ServerToClientModel modelType = binaryModel.getModel();

                if (modelType == ServerToClientModel.DICTIONARY_PATTERN_START) {
                    receivingPattern = true;
                    currentPatternId = binaryModel.getIntValue();
                    currentPattern.clear();
                } else if (modelType == ServerToClientModel.DICTIONARY_PATTERN_END) {
                    patternDictionary.put(currentPatternId, new ArrayList<>(currentPattern));
                    receivingPattern = false;
                    currentPatternId = -1;
                    currentPattern.clear();
                } else if (modelType == ServerToClientModel.DICTIONARY_REFERENCE) {
                    final int refId = binaryModel.getIntValue();
                    final List<BinaryModel> pattern = patternDictionary.get(refId);
                    if (pattern == null) {
                        requestDictionaryPattern(refId);
                    } else {
                        // Apply all models in the pattern
                        for (BinaryModel item : pattern) {
                            if (uiBuilder != null) {
                                updateUI(item);// uiBuilder.updateMainTerminal(item); New line
                            }
                        }
                    }
                } else {
                    if (receivingPattern) {
                        currentPattern.add(cloneBinaryModel(binaryModel));
                    }
                    
                    if (uiBuilder != null) {
                        //uiBuilder.updateMainTerminal(binaryModel);
                        updateUI(binaryModel); // New line, Assuming updateMainTerminal accepts a BinaryModel
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing buffer", e);
        }
    }

    /**
     * Request a dictionary pattern from the server
     */
    private void requestDictionaryPattern(int patternId) {
        try {
            JsonObjectBuilder builder = Json.createObjectBuilder();
            builder.add(ClientToServerModel.DICTIONARY_REQUEST.toStringValue(), patternId);
            JsonObject jsonObject = builder.build();
            sendMessage(jsonObject.toString());
        } catch (Exception e) {
            log.error("Error requesting dictionary pattern", e);
        }
    }
    
    /**
     * Create a deep copy of a BinaryModel
     */
    private BinaryModel cloneBinaryModel(BinaryModel model) {
        if (model == null) return null;
        
        BinaryModel clone =new BinaryModel();//model.getModel());  //new BinaryModel();// New Line
        ServerToClientModel modelType = model.getModel();
        ValueTypeModel valueType = modelType.getTypeModel();
        
        switch (valueType) {
            case NULL:
                clone.init(modelType, model.getSize());
                break;
            case BOOLEAN:
                clone.init(modelType, model.getBooleanValue(), model.getSize());
                break;
            case BYTE:
                clone.init(modelType, (byte)model.getIntValue(), model.getSize());
                break;
            case SHORT:
                clone.init(modelType, (short)model.getIntValue(), model.getSize());
                break;
            case INTEGER:
                clone.init(modelType, model.getIntValue(), model.getSize());
                break;
            case LONG:
                clone.init(modelType, model.getLongValue(), model.getSize());
                break;
            case FLOAT:
                clone.init(modelType, (float)model.getDoubleValue(), model.getSize());
                break;
            case DOUBLE:
                clone.init(modelType, model.getDoubleValue(), model.getSize());
                break;
            case STRING:
                clone.init(modelType, model.getStringValue(), model.getSize());
                break;
            case ARRAY:
                clone.init(modelType, model.getArrayValue(), model.getSize());
                break;
            default:
                clone.init(modelType, model.getSize());
                break;
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
