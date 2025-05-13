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

import com.ponysdk.core.model.ServerToClientModel;
import java.util.Objects;

/**
 * Immutable pair of ServerToClientModel and its associated value for pattern detection.
 */
public final class ModelValuePair {

    private final ServerToClientModel model;
    private final Object value;

    public ModelValuePair(final ServerToClientModel model, final Object value) {
        this.model = model;
        this.value = value;
    }

    public ServerToClientModel getModel() {
        return model;
    }

    public Object getValue() {
        return value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof ModelValuePair)) return false;
        final ModelValuePair that = (ModelValuePair) o;
        return model == that.model && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(model, value);
    }

    @Override
    public String toString() {
        return "ModelValuePair{" + model + "=" + value + '}';
    }
} 