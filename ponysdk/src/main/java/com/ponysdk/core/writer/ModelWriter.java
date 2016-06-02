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

package com.ponysdk.core.writer;

import java.io.IOException;

import com.ponysdk.core.Parser;
import com.ponysdk.ui.model.ServerToClientModel;
import com.ponysdk.ui.server.model.ServerBinaryModel;

public class ModelWriter implements AutoCloseable {

    private final Parser parser;

    public ModelWriter(final Parser parser) {
        this.parser = parser;
    }

    @Deprecated
    public void writeModel(final ServerBinaryModel model) {
        if (model == null) return;
        writeModel(model.getKey(), model.getValue());
    }

    public void writeModel(final ServerToClientModel model, final Object value) {
        if (parser.getPosition() == 0) {
            parser.beginObject();
        }
        parser.parse(model, value);
    }

    @Override
    public void close() throws IOException {
        parser.endObject();
    }
}
