package com.ponysdk.core.terminal.socket;

//import com.ponysdk.core.server.websocket.ModelValuePair;
import com.ponysdk.core.model.ServerToClientModel;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Client-side dictionary for replaying server-compressed patterns.
 */
public class ClientModelTracker {

    /**
     * Simplified ModelValuePair for terminal-side usage
     */
    public static class ModelValuePair {
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
    }
    private final ConcurrentMap<Integer, List<ModelValuePair>> idToPattern = new ConcurrentHashMap<>();

    /**
     * Record a pattern sent by the server under given ID.
     */
    public void recordPattern(final int id, final List<ModelValuePair> pattern) {
        idToPattern.put(id, new ArrayList<>(pattern));
    }

    /**
     * Retrieve a recorded pattern by its ID.
     */
    public List<ModelValuePair> getPattern(final int id) {
        return idToPattern.get(id);
    }

    /**
     * Clear all recorded patterns (e.g. on context reset).
     */
    public void clear() {
        idToPattern.clear();
    }
}   