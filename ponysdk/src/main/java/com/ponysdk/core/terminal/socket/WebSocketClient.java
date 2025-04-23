/*
 * Copyright (c) 2011 PonySDK
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

package com.ponysdk.core.terminal.socket;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ponysdk.core.terminal.ReconnectionChecker;
import com.ponysdk.core.terminal.UIBuilder;
import com.ponysdk.core.terminal.request.WebSocketRequestBuilder;
import com.ponysdk.core.terminal.socket.ClientModelTracker;
import com.ponysdk.core.terminal.model.BinaryModel;
import com.ponysdk.core.terminal.model.ReaderBuffer;
import com.ponysdk.core.model.ServerToClientModel;

import elemental.client.Browser;
import elemental.events.CloseEvent;
import elemental.events.MessageEvent;
import elemental.html.ArrayBuffer;
import elemental.html.Uint8Array;
import elemental.html.WebSocket;
import elemental.html.Window;

public class WebSocketClient {

    private static final Logger log = Logger.getLogger(WebSocketClient.class.getName());

    private final Window window;
    private final WebSocket webSocket;
    private final ClientModelTracker modelTracker;
    private final UIBuilder uiBuilder;
    private boolean dictionaryEnabled;
    private long lastMessageTime;

    public WebSocketClient(final String url, final UIBuilder uiBuilder, final ReconnectionChecker reconnectionChecker) {
        if (url == null || uiBuilder == null || reconnectionChecker == null) {
            throw new IllegalArgumentException("URL, UIBuilder, and ReconnectionChecker must not be null");
        }

        this.window = Browser.getWindow();
        this.webSocket = window.newWebSocket(url);
        this.webSocket.setBinaryType("arraybuffer");
        this.modelTracker = new ClientModelTracker();
        this.uiBuilder = uiBuilder;
        this.dictionaryEnabled = false;
        this.lastMessageTime = -1;

        setupWebSocketHandlers(reconnectionChecker);
    }

    private void setupWebSocketHandlers(final ReconnectionChecker reconnectionChecker) {
        webSocket.setOnopen(event -> {
            uiBuilder.init(new WebSocketRequestBuilder(this));
            if (log.isLoggable(Level.INFO)) log.info("WebSocket connected");
            lastMessageTime = System.currentTimeMillis();
        });

        webSocket.setOnclose(event -> {
            if (event instanceof CloseEvent) {
                final CloseEvent closeEvent = (CloseEvent) event;
                final int statusCode = closeEvent.getCode();
                if (log.isLoggable(Level.INFO)) log.info("WebSocket disconnected : " + statusCode);
                if (statusCode != 1000) reconnectionChecker.detectConnectionFailure();
            } else {
                log.severe("WebSocket disconnected : " + event);
                reconnectionChecker.detectConnectionFailure();
            }
        });

        webSocket.setOnerror(event -> {
            log.severe("WebSocket error : " + event);
        });

        webSocket.setOnmessage(event -> {
            lastMessageTime = System.currentTimeMillis();
            final Object data = ((MessageEvent) event).getData();
            if (data instanceof ArrayBuffer) {
                final ArrayBuffer buffer = (ArrayBuffer) data;
                try {
                    uiBuilder.updateMainTerminal(window.newUint8Array(buffer, 0, buffer.getByteLength()));
                } catch (final Exception e) {
                    log.log(Level.SEVERE, "Error while processing the " + buffer, e);
                }
            }
        });
    }

    private void recordModelValue(BinaryModel binaryModel) {
        ServerToClientModel model = binaryModel.getModel();
        Object value = extractValue(binaryModel);
        modelTracker.record(model, value);

        int frequency = modelTracker.getFrequency(model, value);
        if (frequency > 0 && frequency % 100 == 0) {
            log.info("Frequent pattern detected: " + model + "=" + value + "(frequency :" + frequency + ")");
        }
    }

    private Object extractValue(BinaryModel binaryModel) {
        switch(binaryModel.getModel().getTypeModel()) {
            case STRING:
                return binaryModel.getStringValue();
            case INTEGER:
            case UINT31: 
                return binaryModel.getIntValue();
            case BOOLEAN:
                return binaryModel.getBooleanValue();
            case DOUBLE:
                return binaryModel.getDoubleValue();
            case FLOAT:
                return binaryModel.getFloatValue();
            case LONG: 
                return binaryModel.getLongValue();
            case ARRAY:
                return binaryModel.getArrayValue();
            default:
                return null;
        }
    }

    public void setDictionaryEnabled(boolean enabled) {
        this.dictionaryEnabled = enabled;
        if (!enabled) {
            modelTracker.clear();
        }
    }
    
    public List<Map.Entry<ClientModelTracker.ModelValueKey, Integer>> getMostFrequentPatterns(int limit) {
        return modelTracker.getMostFrequent(limit);
    }

    public void send(final String message) {
        webSocket.send(message);
    }

    public void close() {
        webSocket.close();
    }

    public void close(final int code, final String reason) {
        webSocket.close(code, reason);
    }

    public long getLastMessageTime() {
        return lastMessageTime;
    }

    private void processModel(BinaryModel binaryModel) {
        if (dictionaryEnabled) {
            recordModelValue(binaryModel);
        }
    }

    public Map<ClientModelTracker.ModelValueKey, Integer> getClientDictionary() {
        return modelTracker.getDictionary();
    }

    public void compareDictionaries(Map<ClientModelTracker.ModelValueKey, Integer> serverDictionary) {
        if (serverDictionary == null) {
            throw new IllegalArgumentException("Server dictionary must not be null");
        }

        Map<ClientModelTracker.ModelValueKey, Integer> clientDictionary = getClientDictionary();
        
        Set<ClientModelTracker.ModelValueKey> missingInClient = new HashSet<>(serverDictionary.keySet());
        missingInClient.removeAll(clientDictionary.keySet());
        
        Set<ClientModelTracker.ModelValueKey> missingInServer = new HashSet<>(clientDictionary.keySet());
        missingInServer.removeAll(serverDictionary.keySet());
        
        Map<ClientModelTracker.ModelValueKey, Integer> differentFrequencies = new HashMap<>();
        for (Map.Entry<ClientModelTracker.ModelValueKey, Integer> entry : clientDictionary.entrySet()) {
            Integer serverFreq = serverDictionary.get(entry.getKey());
            if (serverFreq != null && !serverFreq.equals(entry.getValue())) {
                differentFrequencies.put(entry.getKey(), serverFreq);
            }
        }
        
        logDictionaryDifferences(missingInClient, missingInServer, differentFrequencies);
    }

    private void logDictionaryDifferences(Set<ClientModelTracker.ModelValueKey> missingInClient,
                                        Set<ClientModelTracker.ModelValueKey> missingInServer,
                                        Map<ClientModelTracker.ModelValueKey, Integer> differentFrequencies) {
        if (!missingInClient.isEmpty()) {
            log.warning("Server has " + missingInClient.size() + " patterns not present in client");
        }
        if (!missingInServer.isEmpty()) {
            log.warning("Client has " + missingInServer.size() + " patterns not present in server");
        }
        if (!differentFrequencies.isEmpty()) {
            log.warning(differentFrequencies.size() + " patterns have different frequencies");
        }
        
        if (missingInClient.isEmpty() && missingInServer.isEmpty() && differentFrequencies.isEmpty()) {
            log.info("Client and server dictionaries are synchronized");
        }
    }

}
