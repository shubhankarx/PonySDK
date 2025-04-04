package com.ponysdk.core.server.websocket;

import com.ponysdk.core.model.ServerToClientModel;

/**
 * Represents a pair of model and value from the ServerToClientModel enum.
 * This class is used by the dictionary system to track and compress repeated patterns.
 */
public class ModelValuePair {
    private final ServerToClientModel model;
    private final Object value;
    
    public ModelValuePair(ServerToClientModel model, Object value) {
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
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ModelValuePair)) return false;
        
        ModelValuePair other = (ModelValuePair) obj;
        return model == other.model && 
               (value == null ? other.value == null : value.equals(other.value));
    }
    
    @Override
    public int hashCode() {
        int result = model.hashCode();
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }
    
    @Override
    public String toString() {
        return model + ":" + value;
    }
}