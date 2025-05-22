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
import java.util.Arrays;
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
        
        if (model != that.model) return false;
        
        // Handle array values specially
        if (value == that.value) return true;
        if (value == null || that.value == null) return false;
        
        // Array handling
        if (value.getClass().isArray() && that.value.getClass().isArray()) {
            if (value instanceof int[] && that.value instanceof int[]) {
                return Arrays.equals((int[]) value, (int[]) that.value);
            }
            if (value instanceof boolean[] && that.value instanceof boolean[]) {
                return Arrays.equals((boolean[]) value, (boolean[]) that.value);
            }
            if (value instanceof Object[] && that.value instanceof Object[]) {
                return Arrays.equals((Object[]) value, (Object[]) that.value);
            }
            if (value instanceof double[] && that.value instanceof double[]) {
                return Arrays.equals((double[]) value, (double[]) that.value);
            }
            if (value instanceof float[] && that.value instanceof float[]) {
                return Arrays.equals((float[]) value, (float[]) that.value);
            }
        }
        
        // Default equality for non-array values
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        // Handle array values specially for hash code
        if (value != null && value.getClass().isArray()) {
            if (value instanceof int[]) return 31 * model.hashCode() + Arrays.hashCode((int[]) value);
            if (value instanceof boolean[]) return 31 * model.hashCode() + Arrays.hashCode((boolean[]) value);
            if (value instanceof Object[]) return 31 * model.hashCode() + Arrays.hashCode((Object[]) value);
            if (value instanceof double[]) return 31 * model.hashCode() + Arrays.hashCode((double[]) value);
            if (value instanceof float[]) return 31 * model.hashCode() + Arrays.hashCode((float[]) value);
        }
        
        // Default hash for non-array values
        return Objects.hash(model, value);
    }

    @Override
    public String toString() {
        return "ModelValuePair{" + model + "=" + value + '}';
    }
} 