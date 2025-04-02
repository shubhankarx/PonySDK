/*
 * Copyright (c) 2011 PonySDK
 *  Owners:
 *  Luciano Broussal  <luciano.broussal AT gmail.com>
 *	Mathieu Barbier   <mathieu.barbier AT gmail.com>
 *	Nicolas Ciaravola <nicolas.ciaravola.pro AT gmail.com>
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

package com.ponysdk.core.terminal.ui;

import com.google.gwt.user.client.ui.Tree;
import com.ponysdk.core.model.ClientToServerModel;
import com.ponysdk.core.model.ServerToClientModel;
import com.ponysdk.core.terminal.UIBuilder;
import com.ponysdk.core.terminal.instruction.PTInstruction;
import com.ponysdk.core.terminal.model.BinaryModel;
import com.ponysdk.core.terminal.model.ReaderBuffer;

public class PTTree extends PTWidget<Tree> {

    @Override
    public void create(final ReaderBuffer buffer, final int objectId, final UIBuilder uiBuilder) {
        super.create(buffer, objectId, uiBuilder);
        addHandler(uiBuilder);
    }

    @Override
    protected Tree createUIObject() {
        return new Tree();
    }

    private void addHandler(final UIBuilder uiBuilder) {
        uiObject.addSelectionHandler(event -> {
            final PTInstruction eventInstruction = new PTInstruction(getObjectID());
            eventInstruction.put(ClientToServerModel.HANDLER_SELECTION, uiBuilder.getPTObject(event.getSelectedItem()).getObjectID());
            uiBuilder.sendDataToServer(uiObject, eventInstruction);
        });
        uiObject.addOpenHandler(event -> {
            final PTInstruction eventInstruction = new PTInstruction(getObjectID());
            eventInstruction.put(ClientToServerModel.HANDLER_OPEN, uiBuilder.getPTObject(event.getTarget()).getObjectID());
            uiBuilder.sendDataToServer(uiObject, eventInstruction);
        });
        uiObject.addCloseHandler(event -> {
            final PTInstruction eventInstruction = new PTInstruction(getObjectID());
            eventInstruction.put(ClientToServerModel.HANDLER_CLOSE, uiBuilder.getPTObject(event.getTarget()).getObjectID());
            uiBuilder.sendDataToServer(uiObject, eventInstruction);
        });
    }

    @Override
    public void remove(final ReaderBuffer buffer, final PTObject ptObject) {
        uiObject.remove(asWidget(ptObject));
    }

    @Override
    public boolean update(final ReaderBuffer buffer, final BinaryModel binaryModel) {
        final ServerToClientModel model = binaryModel.getModel();
        if (ServerToClientModel.ANIMATION == model) {
            uiObject.setAnimationEnabled(binaryModel.getBooleanValue());
            return true;
        } else if (ServerToClientModel.SELECTED_INDEX == model) {
            final int selectedItemId = binaryModel.getIntValue();
            if (selectedItemId != -1) uiObject.setSelectedItem(asWidget(selectedItemId, uiBuilder), false);
            else uiObject.setSelectedItem(null, false);
            return true;
        } else if (ServerToClientModel.CLEAR == model) {
            uiObject.clear();
            return true;
        } else {
            return super.update(buffer, binaryModel);
        }
    }

}
