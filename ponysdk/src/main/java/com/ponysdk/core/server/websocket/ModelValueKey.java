package com.ponysdk.core.server.websocket;
import java.util.Objects;

import com.ponysdk.core.model.ServerToClientModel;
// identifies a model-value pair sent through WebSocket. Used as keys in the tracking dictionary// identifies a model-value pair sent through WebSocket. Used as keys in the tracking dictionary.
public class ModelValueKey {
    private final ServerToClientModel model;
    private final Object value;

    public ModelValueKey(ServerToClientModel model, Object value) {
        this.model = Objects.requireNonNull(model, "Model cannot be null");
        this.value = value; // Value can be null
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModelValueKey)) return false;
        ModelValueKey that = (ModelValueKey) o;
        return model == that.model && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(model, value);
    }

    @Override
    public String toString() {
        return model + "=" + value;
    }

    public ServerToClientModel getModel() {
        return model;
    }

    public Object getValue() {
        return value;
    }    
}
