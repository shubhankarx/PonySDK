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

import com.google.gwt.dom.client.Element;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONValue;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwt.user.client.ui.Widget;
import com.ponysdk.core.model.ClientToServerModel;
import com.ponysdk.core.model.HandlerModel;
import com.ponysdk.core.model.ServerToClientModel;
import com.ponysdk.core.model.WidgetType;
import com.ponysdk.core.terminal.instruction.PTInstruction;
import com.ponysdk.core.terminal.model.BinaryModel;
import com.ponysdk.core.terminal.model.ReaderBuffer;
//import com.ponysdk.core.server.websocket.ModelValuePair;
import com.ponysdk.core.terminal.socket.ClientModelTracker.ModelValuePair;
import com.ponysdk.core.terminal.request.RequestBuilder;
import com.ponysdk.core.terminal.ui.*;
import elemental.html.Uint8Array;
import elemental.util.Collections;
import elemental.util.MapFromIntTo;
import elemental.util.MapFromStringTo;
import com.ponysdk.core.terminal.socket.ClientModelTracker;
import com.ponysdk.core.model.ValueTypeModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UIBuilder {

    private static final Logger log = Logger.getLogger(UIBuilder.class.getName());

    private final UIFactory uiFactory = new UIFactory();
    private final MapFromIntTo<PTObject> objectByID = Collections.mapFromIntTo();
    private final Map<UIObject, Integer> objectIDByWidget = new HashMap<>();
    private final MapFromIntTo<UIObject> widgetIDByObjectID = Collections.mapFromIntTo();
    private final MapFromStringTo<JavascriptAddOnFactory> javascriptAddOnFactories = Collections.mapFromStringTo();

    private final ReaderBuffer readerBuffer = new ReaderBuffer();
    
    private final ClientModelTracker clientTracker = new ClientModelTracker();
    // Will hold a model we can reuse during pattern replay
    private final BinaryModel replayBinaryModel = new BinaryModel();

    private RequestBuilder requestBuilder;

    private int currentWindowId = -1;

    private long lastReceivedMessage;

    public void init(final RequestBuilder requestBuilder) {
        if (log.isLoggable(Level.INFO)) log.info("Init graphical system");

        this.requestBuilder = requestBuilder;
        // replayBinaryModel remains a separate instance for pattern replay
        PTHistory.addValueChangeHandler(this);

        final PTCookies cookies = new PTCookies(this);
        objectByID.put(0, cookies);

        // Signal to server that dictionary compression is supported
        final PTInstruction dictionaryEnabledMsg = new PTInstruction();
        dictionaryEnabledMsg.put(ClientToServerModel.DICTIONARY_ENABLED, true);
        requestBuilder.send(dictionaryEnabledMsg);

        // hide loading component
        final Widget w = RootPanel.get("loading");
        if (w != null) {
            w.setSize("0px", "0px");
            w.setVisible(false);
        } else {
            log.log(Level.WARNING, "Include splash screen html element into your index.html with id=\"loading\"");
        }
    }

    public void updateMainTerminal(final Uint8Array buffer) {
        lastReceivedMessage = System.currentTimeMillis();

        readerBuffer.init(buffer);

        while (readerBuffer.hasEnoughKeyBytes()) {
            final int nextBlockPosition = readerBuffer.shiftNextBlock(true);
            if (nextBlockPosition == ReaderBuffer.NOT_FULL_BUFFER_POSITION) return;

            // Detect if the message is not for the main terminal but for a specific window
            final BinaryModel binaryModel = readerBuffer.readBinaryModel();
            final ServerToClientModel model = binaryModel.getModel();

            if (ServerToClientModel.ROUNDTRIP_LATENCY == model) {
                final PTInstruction requestData = new PTInstruction();
                requestData.put(ClientToServerModel.TERMINAL_LATENCY, System.currentTimeMillis() - lastReceivedMessage);
                requestBuilder.send(requestData);
                readerBuffer.readBinaryModel(); // Read ServerToClientModel.END element
            } else if (ServerToClientModel.CREATE_CONTEXT == model) {
                PonySDK.get().setContextId(binaryModel.getIntValue());
                // Read ServerToClientModel.OPTION_FORMFIELD_TABULATION element
                PonySDK.get().setTabindexOnlyFormField(readerBuffer.readBinaryModel().getBooleanValue());
                PonySDK.get().setHeartBeatPeriod(readerBuffer.readBinaryModel().getIntValue());
                readerBuffer.readBinaryModel(); // Read ServerToClientModel.END element
            } else if (ServerToClientModel.DESTROY_CONTEXT == model) {
                // Clear recorded patterns on context reset
                clientTracker.clear();
                destroy();
                readerBuffer.readBinaryModel(); // Read ServerToClientModel.END element
            } else if (ServerToClientModel.HEARTBEAT == model) {
                // consume END frame ONCE
                readerBuffer.readBinaryModel();
                // tell server we're still here using HEARTBEAT_REQUEST
                final PTInstruction hb = new PTInstruction();
                hb.put(ClientToServerModel.HEARTBEAT_REQUEST, true);
                requestBuilder.send(hb);
                return;
            } else {
                final int oldCurrentWindowId = currentWindowId;
                if (ServerToClientModel.WINDOW_ID == model) currentWindowId = binaryModel.getIntValue();

                if (currentWindowId == PTWindowManager.getMainWindowId() || oldCurrentWindowId == -1) {
                    BinaryModel binaryModel2;
                    if (ServerToClientModel.WINDOW_ID == model) binaryModel2 = readerBuffer.readBinaryModel();
                    else binaryModel2 = binaryModel;

                    final ServerToClientModel model2 = binaryModel.getModel();
                    if (ServerToClientModel.FRAME_ID == model2) {
                        final int frameId = binaryModel2.getIntValue();
                        final PTFrame frame = (PTFrame) getPTObject(frameId);
                        frame.postMessage(readerBuffer.slice(readerBuffer.getPosition(), nextBlockPosition));
                    } else {
                        update(binaryModel, readerBuffer);
                    }
                } else {
                    if (ServerToClientModel.WINDOW_ID != model) readerBuffer.rewind(binaryModel);

                    final PTWindow window = PTWindowManager.getWindow(currentWindowId);
                    if (window != null && window.isReady()) {
                        final int startPosition = readerBuffer.getPosition();
                        int endPosition = nextBlockPosition;

                        // Concat multiple messages for the same window
                        readerBuffer.setPosition(endPosition);
                        while (readerBuffer.hasEnoughKeyBytes()) {
                            final int nextBlockPosition1 = readerBuffer.shiftNextBlock(true);
                            if (nextBlockPosition1 != ReaderBuffer.NOT_FULL_BUFFER_POSITION) {
                                final BinaryModel newBinaryModel = readerBuffer.readBinaryModel();
                                final ServerToClientModel model2 = newBinaryModel.getModel();
                                if (ServerToClientModel.WINDOW_ID != model2 && ServerToClientModel.ROUNDTRIP_LATENCY != model2
                                        && ServerToClientModel.CREATE_CONTEXT != model2
                                        && ServerToClientModel.DESTROY_CONTEXT != model2) {
                                    endPosition = nextBlockPosition1;
                                    readerBuffer.setPosition(endPosition);
                                } else {
                                    break;
                                }
                            } else {
                                break;
                            }
                        }

                        window.postMessage(readerBuffer.slice(startPosition, endPosition));
                    } else {
                        readerBuffer.shiftNextBlock(false);
                    }
                }
            }
        }
    }

    public void updateWindowTerminal(final Uint8Array buffer) {
        readerBuffer.init(buffer);

        while (readerBuffer.hasEnoughKeyBytes()) {
            // Detect if the message is not for the window but for a specific frame
            final BinaryModel binaryModel = readerBuffer.readBinaryModel();

            if (ServerToClientModel.FRAME_ID == binaryModel.getModel()) {
                final int requestedId = binaryModel.getIntValue();
                final PTFrame frame = (PTFrame) getPTObject(requestedId);
                if (log.isLoggable(Level.FINE)) log.fine("The main terminal send the buffer to frame " + requestedId);
                frame.postMessage(readerBuffer.slice(readerBuffer.getPosition(), readerBuffer.shiftNextBlock(true)));
            } else {
                update(binaryModel, readerBuffer);
            }
        }
    }

    public void updateFrameTerminal(final Uint8Array buffer) {
        readerBuffer.init(buffer);

        update(readerBuffer.readBinaryModel(), readerBuffer);
    }

    /**
     * NEW: This method processes all instructions coming from the server.
     * We modified it to properly handle dictionary-related commands (DICTIONARY_PATTERN_START, 
     * DICTIONARY_REFERENCE, etc.) at the UIBuilder level instead of passing them to individual 
     * widgets. This fixes the warnings like "Update PTLabel #37 with key : DICTIONARY_PATTERN_START 
     * => 1 doesn't exist" by ensuring dictionary commands are handled in the right place.
     */
    private void update(final BinaryModel binaryModel, final ReaderBuffer buffer) {
        final ServerToClientModel model = binaryModel.getModel();

        try {
            if (ServerToClientModel.TYPE_CREATE == model) {
                processCreate(buffer, binaryModel.getIntValue());
            } else if (ServerToClientModel.TYPE_UPDATE == model) {
                processUpdate(buffer, binaryModel.getIntValue());
            } else if (ServerToClientModel.TYPE_ADD == model) {
                processAdd(buffer, binaryModel.getIntValue());
            } else if (ServerToClientModel.TYPE_GC == model) {
                processGC(buffer, binaryModel.getIntValue());
            } else if (ServerToClientModel.TYPE_REMOVE == model) {
                processRemove(buffer, binaryModel.getIntValue());
            } else if (ServerToClientModel.TYPE_ADD_HANDLER == model) {
                processAddHandler(buffer, binaryModel.getIntValue());
            } else if (ServerToClientModel.TYPE_REMOVE_HANDLER == model) {
                processRemoveHandler(buffer, binaryModel.getIntValue());
            } else if (ServerToClientModel.TYPE_HISTORY == model) {
                processHistory(buffer, binaryModel.getStringValue());
            } else if (ServerToClientModel.DICTIONARY_PATTERN_START == model) {
                // Dictionary patterns must be handled directly, not via widget updates
                final int patternId = binaryModel.getIntValue();
                log.fine("Processing dictionary pattern start: " + patternId);
                List<ModelValuePair> pattern = new ArrayList<>();
                BinaryModel bm;
                // Collect all model-value pairs in the pattern
                while ((bm = buffer.readBinaryModel()).getModel() != ServerToClientModel.DICTIONARY_PATTERN_END) {
                    // Extract value according to type
                    Object val = extractValue(bm);
                    pattern.add(new ModelValuePair(bm.getModel(), val));
                }
                // Store pattern in client-side dictionary
                clientTracker.recordPattern(patternId, pattern);
                return;
            } else if (ServerToClientModel.DICTIONARY_REFERENCE == model) {
                // Reference resolution must be handled directly
                final int refId = binaryModel.getIntValue();
                log.fine("Processing dictionary reference: " + refId);
                final List<ModelValuePair> pattern = clientTracker.getPattern(refId);
                if (pattern != null) {
                    // First extract the TYPE_* command if present, as this dictates which object receives updates
                    int objectId = -1;
                    ServerToClientModel typeCommand = null;
                    
                    // First pass: look for a TYPE_* instruction and extract object ID
                    for (ModelValuePair pair : pattern) {
                        if (isTypeCommand(pair.getModel())) {
                            typeCommand = pair.getModel();
                            objectId = ((Number)pair.getValue()).intValue();
                            break;
                        }
                    }
                    
                    // If we found a TYPE command, apply all instructions in the correct context
                    if (typeCommand != null && objectId != -1) {
                        // First create a new TYPE command manually to set context
                        BinaryModel typeModel = createBinaryModel(typeCommand, objectId);
                        
                        // Process the TYPE command to set up the context
                        update(typeModel, buffer);
                        
                        // Check if pattern has a WIDGET_TYPE command, which needs special handling
                        ModelValuePair widgetTypePair = null;
                        for (ModelValuePair pair : pattern) {
                            if (pair.getModel() == ServerToClientModel.WIDGET_TYPE) {
                                widgetTypePair = pair;
                                break;
                            }
                        }
                        
                        // Special handling for TYPE_CREATE: inject WIDGET_TYPE first if present
                        if (typeCommand == ServerToClientModel.TYPE_CREATE && widgetTypePair != null) {
                            BinaryModel widgetTypeModel = createBinaryModel(widgetTypePair.getModel(), widgetTypePair.getValue());
                            PTObject ptObject = getPTObject(objectId);
                            if (ptObject != null) {
                                ptObject.update(buffer, widgetTypeModel);
                            }
                        }
                        
                        // Now apply each command in the pattern except the TYPE command
                        // and the WIDGET_TYPE if we've already processed it
                        for (ModelValuePair pair : pattern) {
                            if (pair.getModel() != typeCommand && 
                                !(pair == widgetTypePair && typeCommand == ServerToClientModel.TYPE_CREATE)) {
                                
                                // Skip dictionary-specific commands that should be handled at UIBuilder level
                                if (pair.getModel() == ServerToClientModel.DICTIONARY_PATTERN_START ||
                                    pair.getModel() == ServerToClientModel.DICTIONARY_PATTERN_END ||
                                    pair.getModel() == ServerToClientModel.DICTIONARY_REFERENCE) {
                                    continue;
                                }
                                
                                BinaryModel cmdModel = createBinaryModel(pair.getModel(), pair.getValue());
                                
                                // If it's another TYPE command, process it as a complete update
                                if (isTypeCommand(pair.getModel())) {
                                    update(cmdModel, buffer);
                                } 
                                // Otherwise it's a property of the main object
                                else {
                                    // Get the object from our object registry
                                    PTObject ptObject = getPTObject(objectId);
                                    if (ptObject != null) {
                                        // Direct property update on the widget
                                        ptObject.update(buffer, cmdModel);
                                    }
                                }
                            }
                        }
                    } 
                    // No TYPE command found, just process each command in sequence
                    else {
                        for (ModelValuePair pair : pattern) {
                            // Skip dictionary-specific commands
                            if (pair.getModel() == ServerToClientModel.DICTIONARY_PATTERN_START ||
                                pair.getModel() == ServerToClientModel.DICTIONARY_PATTERN_END ||
                                pair.getModel() == ServerToClientModel.DICTIONARY_REFERENCE) {
                                continue;
                            }
                            
                            BinaryModel cmdModel = createBinaryModel(pair.getModel(), pair.getValue());
                            update(cmdModel, buffer);
                        }
                    }
                } else {
                    log.warning("Dictionary pattern not found: " + refId);
                    requestDictionaryPattern(refId);
                }
                return;
            } else if (ServerToClientModel.DICTIONARY_PATTERN_END == model) {
                // This should be handled as part of DICTIONARY_PATTERN_START processing
                log.fine("Processing dictionary pattern end");
                return;
            } else {
                log.log(Level.WARNING, "Unknown instruction type : " + binaryModel + " ; " + buffer.toString());
                if (ServerToClientModel.END != model) buffer.shiftNextBlock(false);
            }
        } catch (final Exception e) {
            if (ServerToClientModel.END != model) buffer.shiftNextBlock(false);
            sendExceptionMessageToServer(e);
        }
    }

    /**
     * NEW: Helper method to create a BinaryModel from a model and value
     */
    private BinaryModel createBinaryModel(ServerToClientModel model, Object value) {
        BinaryModel binaryModel = new BinaryModel();
        switch (model.getTypeModel()) {
            case BOOLEAN:
                binaryModel.init(model, (boolean) value, 1);
                break;
            case BYTE:
            case SHORT:
            case INTEGER:
                binaryModel.init(model, ((Number) value).intValue(), 1);
                break;
            case LONG:
                binaryModel.init(model, ((Number) value).longValue(), 1);
                break;
            case FLOAT:
                binaryModel.init(model, ((Number) value).floatValue(), 1);
                break;
            case DOUBLE:
                binaryModel.init(model, ((Number) value).doubleValue(), 1);
                break;
            case STRING:
                binaryModel.init(model, (String) value, ((String) value).length());
                break;
            case ARRAY:
                binaryModel.init(model, (JSONArray) value, 1);
                break;
            default:
                binaryModel.init(model, 0);
                break;
        }
        return binaryModel;
    }

    /**
     * NEW: This helper method extracts typed values from a BinaryModel.
     * We created this simplified version to cleanly handle value extraction 
     * during dictionary pattern processing without modifying the buffer state.
     * This prevents potential issues with buffer position tracking during pattern replay.
     */
    private Object extractValue(final BinaryModel bm) {
        switch (bm.getModel().getTypeModel()) {
            case BOOLEAN:
                return bm.getBooleanValue();
            case BYTE:
            case SHORT:
            case INTEGER:
                return bm.getIntValue();
            case LONG:
                return bm.getLongValue();
            case FLOAT:
                return bm.getFloatValue();
            case DOUBLE:
                return bm.getDoubleValue();
            case STRING:
                return bm.getStringValue();
            case ARRAY:
                return bm.getArrayValue();
            default:
                return null;
        }
    }

    private void processCreate(final ReaderBuffer buffer, final int objectID) {
        // ServerToClientModel.WIDGET_TYPE
        final WidgetType widgetType = WidgetType.fromRawValue(buffer.readBinaryModel().getIntValue());

        final PTObject ptObject = uiFactory.newUIObject(widgetType);
        if (ptObject != null) {
            ptObject.create(buffer, objectID, this);
            objectByID.put(objectID, ptObject);

            processUpdate(buffer, objectID);
        } else {
            log.warning("Cannot create PObject #" + objectID + " with widget type : " + widgetType);
            buffer.shiftNextBlock(false);
        }
    }

    private void processAdd(final ReaderBuffer buffer, final int objectID) {
        final PTObject ptObject = getPTObject(objectID);
        if (ptObject != null) {
            // ServerToClientModel.PARENT_OBJECT_ID
            final int parentId = buffer.readBinaryModel().getIntValue();
            final PTObject parentObject = getPTObject(parentId);
            if (parentObject != null) {
                parentObject.add(buffer, ptObject);
                buffer.readBinaryModel(); // Read ServerToClientModel.END element
            } else {
                log.warning("Cannot add " + ptObject + " to an garbaged parent object #" + parentId
                        + ", so we will consume all the buffer of this object");
                buffer.shiftNextBlock(false);
            }
        } else {
            log.warning("Add a null PTObject #" + objectID + ", so we will consume all the buffer of this object");
            buffer.shiftNextBlock(false);
        }
    }

    /**
     * NEW: This method handles updates to a specific UI object.
     * We modified it to properly handle dictionary-related commands while maintaining
     * widget functionality. This ensures widgets are fully interactive while still
     * benefiting from dictionary optimization.
     */
    private void processUpdate(final ReaderBuffer buffer, final int objectID) {
        final PTObject ptObject = getPTObject(objectID);
        if (ptObject != null) {
            BinaryModel binaryModel;
            do {
                binaryModel = buffer.readBinaryModel();
                if (ServerToClientModel.END.getValue() != binaryModel.getModel().getValue()) {
                    ServerToClientModel model = binaryModel.getModel();
                    
                    // Special case: If we get a dictionary command directly for a widget,
                    // handle it at the UIBuilder level instead
                    if (model == ServerToClientModel.DICTIONARY_PATTERN_START ||
                        model == ServerToClientModel.DICTIONARY_PATTERN_END ||
                        model == ServerToClientModel.DICTIONARY_REFERENCE) {
                        
                        // We need to process this dictionary command at UIBuilder level, not widget level
                        // Rewind to read this command again in the main update flow
                        buffer.rewind(binaryModel);
                        
                        // Create a temporary TYPE_UPDATE command to send to the main update flow
                        BinaryModel typeUpdateModel = new BinaryModel();
                        typeUpdateModel.init(ServerToClientModel.TYPE_UPDATE, objectID, 1);
                        
                        // Process at UIBuilder level
                        update(typeUpdateModel, buffer);
                        return; // Stop processing current update as we've redirected to the main flow
                    }
                    
                    // Standard widget update flow
                    final boolean result = ptObject.update(buffer, binaryModel);
                    if (!result) {
                        // If this is a WIDGET_TYPE command, try to recreate the widget
                        if (model == ServerToClientModel.WIDGET_TYPE) {
                            log.fine("Special handling for WIDGET_TYPE command on object #" + objectID);
                            // Just skip this command; the widget may handle other properties
                            continue;
                        } else {
                            log.warning("Update " + ptObject.getClass().getSimpleName() + " #" + objectID + 
                                      " with key : " + binaryModel + " doesn't exist");
                            buffer.shiftNextBlock(false);
                        }
                        break;
                    }
                } else {
                    break;
                }
            } while (buffer.hasEnoughKeyBytes());
        } else {
            log.warning("Update on a null PTObject #" + objectID + ", so we will consume all the buffer of this object");
            buffer.shiftNextBlock(false);
        }
    }

    private void processRemove(final ReaderBuffer buffer, final int objectID) {
        final PTObject ptObject = getPTObject(objectID);
        if (ptObject != null) {
            final int parentId = buffer.readBinaryModel().getIntValue();
            final PTObject parentObject = parentId != -1 ? getPTObject(parentId) : ptObject;

            if (parentObject != null) {
                parentObject.remove(buffer, ptObject);
                buffer.readBinaryModel(); // Read ServerToClientModel.END element
            } else {
                log.warning("Cannot remove " + ptObject + " on a garbaged object #" + parentId);
                buffer.shiftNextBlock(false);
            }
        } else {
            log.warning("Remove a null PTObject #" + objectID + ", so we will consume all the buffer of this object");
            buffer.shiftNextBlock(false);
        }
    }

    private void processAddHandler(final ReaderBuffer buffer, final int objectID) {
        // ServerToClientModel.HANDLER_TYPE
        final HandlerModel handlerModel = HandlerModel.fromRawValue(buffer.readBinaryModel().getIntValue());

        if (HandlerModel.HANDLER_STREAM_REQUEST == handlerModel) {
            new PTStreamResource().addHandler(buffer, handlerModel);
            buffer.readBinaryModel(); // Read ServerToClientModel.END element
        } else {
            final PTObject ptObject = getPTObject(objectID);
            if (ptObject != null) {
                ptObject.addHandler(buffer, handlerModel);
                buffer.readBinaryModel(); // Read ServerToClientModel.END element
            } else {
                log.warning("Add handler on a null PTObject #" + objectID + ", so we will consume all the buffer of this object");
                buffer.shiftNextBlock(false);
            }
        }
    }

    private void processRemoveHandler(final ReaderBuffer buffer, final int objectID) {
        final PTObject ptObject = getPTObject(objectID);
        if (ptObject != null) {
            // ServerToClientModel.HANDLER_TYPE
            final HandlerModel handlerModel = HandlerModel.fromRawValue(buffer.readBinaryModel().getIntValue());
            ptObject.removeHandler(buffer, handlerModel);
            buffer.readBinaryModel(); // Read ServerToClientModel.END element
        } else {
            log.warning("Remove handler on a null PTObject #" + objectID + ", so we will consume all the buffer of this object");
            buffer.shiftNextBlock(false);
        }
    }

    private void processHistory(final ReaderBuffer buffer, final String token) {
        final String oldToken = History.getToken();

        // ServerToClientModel.HISTORY_FIRE_EVENTS
        final boolean fireEvents = buffer.readBinaryModel().getBooleanValue();
        if (oldToken != null && oldToken.equals(token)) {
            if (fireEvents) History.fireCurrentHistoryState();
        } else {
            History.newItem(token, fireEvents);
        }

        buffer.readBinaryModel(); // Read ServerToClientModel.END element
    }

    private void processGC(final ReaderBuffer buffer, final int objectID) {
        final PTObject ptObject = unregisterObject(objectID);
        if (ptObject != null) {
            ptObject.destroy();
            buffer.readBinaryModel(); // Read ServerToClientModel.END element
        } else {
            log.warning("Cannot GC a garbaged PTObject #" + objectID);
            buffer.shiftNextBlock(false);
        }
    }

    private PTObject unregisterObject(final int objectID) {
        final PTObject ptObject = objectByID.get(objectID);
        objectByID.remove(objectID);
        final UIObject uiObject = widgetIDByObjectID.get(objectID);
        widgetIDByObjectID.remove(objectID);
        if (uiObject != null) objectIDByWidget.remove(uiObject);
        return ptObject;
    }

    private void destroy() {
        PTWindowManager.closeAll();
        ReconnectionChecker.reloadWindow();
    }

    public void sendDataToServer(final Widget widget, final PTInstruction instruction) {
        if (log.isLoggable(Level.FINE)) {
            if (widget != null) {
                final Element source = widget.getElement();
                if (source != null)
                    log.fine("Action triggered, Instruction [" + instruction + "] , " + source.getInnerHTML());
            }
        }
        sendDataToServer(instruction);
    }

    public void sendDataToServer(final JSONValue instruction) {
        requestBuilder.send(instruction);
    }

    public void sendDataToServer(final JSONObject instruction) {
        final PTInstruction requestData = new PTInstruction();
        final JSONArray jsonArray = new JSONArray();
        jsonArray.set(0, instruction);
        requestData.put(ClientToServerModel.APPLICATION_INSTRUCTIONS, jsonArray);

        if (log.isLoggable(Level.FINE)) log.log(Level.FINE, "Data to send " + requestData.toString());

        requestBuilder.send(requestData);
    }

    public void sendExceptionMessageToServer(final Throwable t) {
        log.log(Level.SEVERE, "PonySDK has encountered an internal error : ", t);
        sendErrorMessageToServer(
                t.getClass().getCanonicalName() + " : " + t.getMessage() + " : " + Arrays.toString(t.getStackTrace()));
    }

    public void sendErrorMessageToServer(final String message) {
        final PTInstruction requestData = new PTInstruction();
        requestData.put(ClientToServerModel.ERROR_MSG, message);
        requestBuilder.send(requestData);
    }

    public void sendErrorMessageToServer(final String message, final int objectID) {
        final PTInstruction requestData = new PTInstruction();
        requestData.put(ClientToServerModel.ERROR_MSG, message);
        requestData.put(ClientToServerModel.OBJECT_ID, objectID);
        requestBuilder.send(requestData);
    }

    public void sendWarningMessageToServer(final String message) {
        final PTInstruction requestData = new PTInstruction();
        requestData.put(ClientToServerModel.WARN_MSG, message);
        requestBuilder.send(requestData);
    }

    public void sendWarningMessageToServer(final String message, final int objectID) {
        final PTInstruction requestData = new PTInstruction();
        requestData.put(ClientToServerModel.WARN_MSG, message);
        requestData.put(ClientToServerModel.OBJECT_ID, objectID);
        requestBuilder.send(requestData);
    }

    public void sendInfoMessageToServer(final String message) {
        final PTInstruction requestData = new PTInstruction();
        requestData.put(ClientToServerModel.INFO_MSG, message);
        requestBuilder.send(requestData);
    }

    public void sendInfoMessageToServer(final String message, final int objectID) {
        final PTInstruction requestData = new PTInstruction();
        requestData.put(ClientToServerModel.INFO_MSG, message);
        requestData.put(ClientToServerModel.OBJECT_ID, objectID);
        requestBuilder.send(requestData);
    }

    public PTObject getPTObject(final int id) {
        final PTObject ptObject = objectByID.get(id);
        if (ptObject == null) {
            log.warning("PTObject #" + id + " not found");
            sendWarningMessageToServer("PTObject #" + id + " not found", id);
        }
        return ptObject;
    }

    public PTObject getPTObject(final UIObject uiObject) {
        final Integer objectID = objectIDByWidget.get(uiObject);
        if (objectID != null) return getPTObject(objectID.intValue());
        return null;
    }

    public void registerUIObject(final Integer ID, final UIObject uiObject) {
        objectIDByWidget.put(uiObject, ID);
        widgetIDByObjectID.put(ID, uiObject);
    }

    void registerJavascriptAddOnFactory(final String signature, final JavascriptAddOnFactory javascriptAddOnFactory) {
        this.javascriptAddOnFactories.put(signature, javascriptAddOnFactory);
    }

    public JavascriptAddOnFactory getJavascriptAddOnFactory(final String signature) {
        return javascriptAddOnFactories.get(signature);
    }

    void setReadyWindow(final int windowID) {
        final PTWindow window = PTWindowManager.getWindow(windowID);
        if (window != null) window.setReady();
        else log.warning("Window " + windowID + " doesn't exist");
    }

    void setReadyFrame(final int frameID) {
        final PTFrame frame = (PTFrame) getPTObject(frameID);
        if (frame != null) frame.setReady();
        else log.warning("Frame " + frame + " doesn't exist");
    }

    /**
     * Replay a single model/value pair using existing update logic.
     */
    private void applyFrame(final ServerToClientModel model, final Object value, final ReaderBuffer buffer) {
        // Use reusable BinaryModel
        switch (model.getTypeModel()) {
            case BOOLEAN:
                replayBinaryModel.init(model, (boolean) value, 1);
                break;
            case BYTE: case SHORT: case INTEGER:
                replayBinaryModel.init(model, ((Number) value).intValue(), 1);
                break;
            case LONG:
                replayBinaryModel.init(model, ((Number) value).longValue(), 1);
                break;
            case FLOAT:
                replayBinaryModel.init(model, ((Number) value).floatValue(), 1);
                break;
            case DOUBLE:
                replayBinaryModel.init(model, ((Number) value).doubleValue(), 1);
                break;
            case STRING:
                replayBinaryModel.init(model, (String) value, ((String) value).length());
                break;
            case ARRAY:
                replayBinaryModel.init(model, (JSONArray) value, 1);
                break;
            default:
                replayBinaryModel.init(model, 0);
        }
        
        // For instructions that require special handling, route accordingly
        if (model == ServerToClientModel.TYPE_CREATE) {
            processCreate(buffer, ((Number) value).intValue());
        } else if (model == ServerToClientModel.TYPE_UPDATE) {
            processUpdate(buffer, ((Number) value).intValue());
        } else if (model == ServerToClientModel.TYPE_ADD) {
            processAdd(buffer, ((Number) value).intValue());
        } else if (model == ServerToClientModel.TYPE_GC) {
            processGC(buffer, ((Number) value).intValue());
        } else if (model == ServerToClientModel.TYPE_REMOVE) {
            processRemove(buffer, ((Number) value).intValue());
        } else if (model == ServerToClientModel.TYPE_ADD_HANDLER) {
            processAddHandler(buffer, ((Number) value).intValue());
        } else if (model == ServerToClientModel.TYPE_REMOVE_HANDLER) {
            processRemoveHandler(buffer, ((Number) value).intValue());
        } else if (model == ServerToClientModel.TYPE_HISTORY) {
            processHistory(buffer, (String) value);
        } else {
            // For normal property updates, pass to the standard update flow
            update(replayBinaryModel, buffer);
        }
    }

    /**
     * Request a missing dictionary pattern from the server.
     */
    private void requestDictionaryPattern(final int patternId) {
        final PTInstruction requestData = new PTInstruction();
        requestData.put(ClientToServerModel.DICTIONARY_REQUEST, patternId);
        requestBuilder.send(requestData);
        log.info("Requested missing dictionary pattern: " + patternId);
    }

    /**
     * Helper method to check if a model is a TYPE_* command
     */
    private boolean isTypeCommand(ServerToClientModel model) {
        return model == ServerToClientModel.TYPE_CREATE ||
               model == ServerToClientModel.TYPE_UPDATE ||
               model == ServerToClientModel.TYPE_ADD ||
               model == ServerToClientModel.TYPE_REMOVE ||
               model == ServerToClientModel.TYPE_ADD_HANDLER ||
               model == ServerToClientModel.TYPE_REMOVE_HANDLER ||
               model == ServerToClientModel.TYPE_GC;
    }

}
