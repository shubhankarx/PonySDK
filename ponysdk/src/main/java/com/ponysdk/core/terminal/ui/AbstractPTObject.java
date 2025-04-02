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

import com.ponysdk.core.model.HandlerModel;
import com.ponysdk.core.terminal.UIBuilder;
import com.ponysdk.core.terminal.model.BinaryModel;
import com.ponysdk.core.terminal.model.ReaderBuffer;

abstract class AbstractPTObject implements PTObject {

    protected int objectID;

    protected UIBuilder uiBuilder;

    @Override
    public int getObjectID() {
        return objectID;
    }

    @Override
    public void create(final ReaderBuffer buffer, final int id, final UIBuilder uiBuilder) {
        this.objectID = id;
        this.uiBuilder = uiBuilder;
    }

    @Override
    public boolean update(final ReaderBuffer buffer, final BinaryModel binaryModel) {
        return false;
    }

    @Override
    public void add(final ReaderBuffer buffer, final PTObject ptObject) {
    }

    @Override
    public void remove(final ReaderBuffer buffer, final PTObject ptObject) {
    }

    @Override
    public void addHandler(final ReaderBuffer buffer, final HandlerModel handlerModel) {
    }

    @Override
    public void removeHandler(final ReaderBuffer buffer, final HandlerModel handlerModel) {
    }

    @Override
    public void destroy() {
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " #" + getObjectID();
    }

}
