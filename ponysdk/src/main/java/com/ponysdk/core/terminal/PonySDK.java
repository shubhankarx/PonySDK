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

package com.ponysdk.core.terminal;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.GWT.UncaughtExceptionHandler;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Window;
import com.ponysdk.core.model.ClientToServerModel;
import com.ponysdk.core.model.MappingPath;
import com.ponysdk.core.terminal.instruction.PTInstruction;
import com.ponysdk.core.terminal.request.FrameRequestBuilder;
import com.ponysdk.core.terminal.request.WindowRequestBuilder;
import com.ponysdk.core.terminal.socket.WebSocketClient;
import com.ponysdk.core.terminal.ui.PTObject;
import com.ponysdk.core.terminal.ui.PTWindowManager;
import elemental2.dom.DomGlobal;
import elemental2.dom.XMLHttpRequest;
import jsinterop.annotations.JsType;

import java.util.logging.Logger;

@JsType
public class PonySDK implements UncaughtExceptionHandler {

    private static final Logger log = Logger.getLogger(PonySDK.class.getName());
    private static PonySDK INSTANCE = new PonySDK();

    private final UIBuilder uiBuilder = new UIBuilder();

    private int contextId;
    private WebSocketClient socketClient;
    private boolean started;

    private boolean tabindexOnlyFormField;

    private long lastHeartBeatFail = 0L;
    private ReconnectionChecker reconnectionChecker;

    private PonySDK() {
        INSTANCE = this;
    }

    public static PonySDK get() {
        return INSTANCE;
    }

    public void start() {
        if (started) return;

        GWT.setUncaughtExceptionHandler(this);

        final String child = Window.Location.getParameter(ClientToServerModel.UI_CONTEXT_ID.toStringValue());

        if (child == null) {
            startMainContext();
            log.info("PonySDK engine is started");
        } else {
            startChildContext();
            log.info("PonySDK engine is started for child window context");
        }

        started = true;
    }

    private void startMainContext() {
        Window.addCloseHandler(event -> close());

        final String protocol = DomGlobal.window.location.protocol.replace("http", "ws");
        final String server = DomGlobal.window.location.host;
        final String pathName = DomGlobal.window.location.pathname;
        final String search = DomGlobal.window.location.search.replace('?', '&');
        final String wsMappingPath = MappingPath.WEBSOCKET + "?"
                + ClientToServerModel.TYPE_HISTORY.toStringValue() + "=" + History.getToken();

        String newUrl = protocol + "//" + server + pathName + wsMappingPath + search;

        reconnectionChecker = new ReconnectionChecker();
        socketClient = new WebSocketClient(newUrl, uiBuilder, reconnectionChecker);
    }

    private void startChildContext() {
        final String windowId = Window.Location.getParameter(ClientToServerModel.WINDOW_ID.toStringValue());
        final String frameId = Window.Location.getParameter(ClientToServerModel.FRAME_ID.toStringValue());

        contextId = Integer.parseInt(Window.Location.getParameter(ClientToServerModel.UI_CONTEXT_ID.toStringValue()));
        final String tabindexOnlyFormFieldRaw = Window.Location
                .getParameter(ClientToServerModel.OPTION_TABINDEX_ACTIVATED.toStringValue());
        if (tabindexOnlyFormFieldRaw != null) tabindexOnlyFormField = Boolean.parseBoolean(tabindexOnlyFormFieldRaw);

        uiBuilder.init(windowId != null ? new WindowRequestBuilder(windowId, uiBuilder::updateWindowTerminal)
                : new FrameRequestBuilder(frameId, uiBuilder::updateFrameTerminal));
    }

    public void sendDataToServerFromWindow(final String jsObject) {
        uiBuilder.sendDataToServer(JSONParser.parseStrict(jsObject));
    }

    public void sendDataToServer(final Object objectID, final JavaScriptObject jsObject, final AjaxCallback callback) {
        if (callback == null) {
            final PTInstruction instruction = new PTInstruction(Integer.parseInt(objectID.toString()));
            instruction.put(ClientToServerModel.NATIVE, jsObject);
            uiBuilder.sendDataToServer(instruction);
        } else {
            XMLHttpRequest xhr = new XMLHttpRequest();

            final PTObject ptObject = uiBuilder.getPTObject(Integer.parseInt(objectID.toString()));

            xhr.onload = evt -> callback.setAjaxResponse(xhr.responseText);
            xhr.open("GET", MappingPath.AJAX.toString());
            xhr.setRequestHeader(ClientToServerModel.UI_CONTEXT_ID.name(), String.valueOf(contextId));
            xhr.setRequestHeader(ClientToServerModel.OBJECT_ID.name(), String.valueOf(ptObject.getObjectID()));

            final JSONObject jsonArray = new JSONObject(jsObject);
            for (final String key : jsonArray.keySet()) {
                final String value;
                final JSONValue jsonValue = jsonArray.get(key);
                if (jsonValue != null) {
                    final JSONString stringValue = jsonValue.isString();
                    value = stringValue != null ? stringValue.stringValue() : jsonValue.toString();
                } else {
                    value = "";
                }
                xhr.setRequestHeader(key, value);
            }

            xhr.send();
        }
    }

    public void request(final Object objectID, final JavaScriptObject jsObject, final AjaxCallback callback) {
        sendDataToServer(objectID, jsObject, callback);
    }

    public void setReadyFrame(final int frameID) {
        uiBuilder.setReadyFrame(frameID);
    }

    public void setReadyWindow(final int windowID) {
        uiBuilder.setReadyWindow(windowID);
    }

    public void registerAddOnFactory(final String signature, final JavascriptAddOnFactory javascriptAddOnFactory) {
        uiBuilder.registerJavascriptAddOnFactory(signature, javascriptAddOnFactory);
    }

    public String getHostPageBaseURL() {
        return GWT.getHostPageBaseURL();
    }

    @Override
    public void onUncaughtException(final Throwable e) {
        uiBuilder.sendExceptionMessageToServer(e);
    }

    public void close() {
        socketClient.close();
        PTWindowManager.closeAll();
    }

    public int getContextId() {
        return contextId;
    }

    public void setContextId(final int contextId) {
        this.contextId = contextId;
    }

    public boolean isTabindexOnlyFormField() {
        return tabindexOnlyFormField;
    }

    public void setTabindexOnlyFormField(final boolean tabindexOnlyFormField) {
        this.tabindexOnlyFormField = tabindexOnlyFormField;
    }

    public void setHeartBeatPeriod(final int heartBeatInseconds) {
        if (heartBeatInseconds == 0) return;
        final int heartBeatInMilli = heartBeatInseconds * 1000;
        Scheduler.get().scheduleFixedDelay(() -> {
            final long now = System.currentTimeMillis();
            final long lastMessageTime = socketClient.getLastMessageTime();
            if (lastMessageTime != lastHeartBeatFail) {
                if (now - lastMessageTime > heartBeatInMilli) {
                    lastHeartBeatFail = lastMessageTime;
                    final PTInstruction requestData = new PTInstruction();
                    requestData.put(ClientToServerModel.HEARTBEAT_REQUEST);
                    socketClient.send(requestData.toString());
                }
            } else {
                if (now - lastMessageTime > heartBeatInMilli) {
                    socketClient.close(1000, "server did not respond");
                    reconnectionChecker.detectConnectionFailure();
                    //stop the scheduling
                    return false;
                } else {
                    lastHeartBeatFail = 0L;
                }
            }
            return true;
        }, heartBeatInMilli);
    }

}
