package com.ponysdk.core.terminal.socket;

//import com.ponysdk.core.server.websocket.ModelValuePair;
import com.ponysdk.core.model.ServerToClientModel;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Client-side dictionary for replaying server-compressed patterns.
 */
public class ClientModelTracker {
    private static final Logger log = Logger.getLogger(ClientModelTracker.class.getName());

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
    
    private final Map<Integer, List<ModelValuePair>> idToPattern = new ConcurrentHashMap<>();
    private final Map<String, Object> valueMap = new ConcurrentHashMap<>();

    /**
     * Record a pattern sent by the server under given ID.
     */
    public void recordPattern(final int id, final List<ModelValuePair> pattern) {
        if (pattern == null || pattern.isEmpty()) {
            log.warning("Attempted to record empty pattern with ID: " + id);
            return;
        }
        
        List<ModelValuePair> patternCopy = new ArrayList<>(pattern);
        idToPattern.put(id, patternCopy);
        log.fine("Recorded pattern ID " + id + " with " + patternCopy.size() + " entries");
    }

    /**
     * Retrieve a recorded pattern by its ID.
     */
    public List<ModelValuePair> getPattern(final int id) {
        List<ModelValuePair> pattern = idToPattern.get(id);
        if (pattern == null) {
            log.warning("Pattern not found for ID: " + id);
        }
        return pattern;
    }

    /**
     * Store a key-value mapping in the dictionary.
     */
    public void storeValue(String key, Object value) {
        valueMap.put(key, value);
    }

    /**
     * Retrieve a value by key.
     */
    public Object getValue(String key) {
        return valueMap.get(key);
    }

    /**
     * Clear all recorded patterns (e.g. on context reset).
     */
    public void clear() {
        idToPattern.clear();
        valueMap.clear();
        log.info("Dictionary cleared");
    }
}   