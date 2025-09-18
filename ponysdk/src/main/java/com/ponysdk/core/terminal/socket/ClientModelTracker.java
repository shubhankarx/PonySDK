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
    
    // STACK_OVERFLOW_FIX_2024_SHUB: Prevent infinite pattern request loops
    private static final int MAX_REQUESTS_PER_PATTERN = 3;
    private static final boolean ENABLE_REQUEST_LIMITING = true;

    // ERROR_RECOVERY_2024_SHUB: Enhanced error recovery mechanisms
    private static final boolean ENABLE_PATTERN_VALIDATION = true;
    private static final boolean ENABLE_GRACEFUL_DEGRADATION = true;

    private final Map<Integer, List<ModelValuePair>> idToPattern = new ConcurrentHashMap<>();
    private final Map<String, Object> valueMap = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> requestCounts = new ConcurrentHashMap<>();
    private final Map<Integer, String> patternErrors = new ConcurrentHashMap<>();

    /**
     * Record a pattern sent by the server under given ID with enhanced error recovery.
     */
    public void recordPattern(final int id, final List<ModelValuePair> pattern) {
        if (pattern == null || pattern.isEmpty()) {
            log.warning("Attempted to record empty pattern with ID: " + id);
            if (ENABLE_GRACEFUL_DEGRADATION) {
                patternErrors.put(id, "Empty pattern received");
            }
            return;
        }

        // ERROR_RECOVERY_2024_SHUB: Validate pattern before storage
        if (ENABLE_PATTERN_VALIDATION) {
            String validationError = validatePattern(pattern);
            if (validationError != null) {
                log.warning("Pattern validation failed for ID " + id + ": " + validationError);
                if (ENABLE_GRACEFUL_DEGRADATION) {
                    patternErrors.put(id, validationError);
                    // Still store the pattern but mark it as problematic
                }
            }
        }

        List<ModelValuePair> patternCopy = new ArrayList<>(pattern);
        idToPattern.put(id, patternCopy);

        // Clear any previous error state
        patternErrors.remove(id);

        log.info("Recorded pattern ID " + id + " with " + patternCopy.size() + " entries");
    }

    /**
     * Retrieve a recorded pattern by its ID with enhanced error recovery.
     */
    public List<ModelValuePair> getPattern(final int id) {
        List<ModelValuePair> pattern = idToPattern.get(id);

        if (pattern == null) {
            // Check if this pattern had errors during storage
            String error = patternErrors.get(id);
            if (error != null) {
                log.warning("Pattern ID " + id + " has known error: " + error);
            }

            if (ENABLE_REQUEST_LIMITING) {
                int count = requestCounts.getOrDefault(id, 0);
                if (count < MAX_REQUESTS_PER_PATTERN) {
                    requestCounts.put(id, count + 1);
                    log.warning("Pattern not found for ID: " + id + " (attempt " + (count + 1) + ")");
                } else {
                    log.severe("STACK_OVERFLOW_FIX_2024_SHUB: Blocked excessive requests for pattern ID: " + id);
                    // ERROR_RECOVERY_2024_SHUB: Return empty pattern to prevent further issues
                    if (ENABLE_GRACEFUL_DEGRADATION) {
                        return new ArrayList<>();
                    }
                }
            } else {
                log.warning("Pattern not found for ID: " + id);
            }
        } else {
            // ERROR_RECOVERY_2024_SHUB: Validate pattern before returning
            if (ENABLE_PATTERN_VALIDATION) {
                String validationError = validatePattern(pattern);
                if (validationError != null) {
                    log.warning("Stored pattern ID " + id + " failed validation: " + validationError);
                    if (ENABLE_GRACEFUL_DEGRADATION) {
                        pattern = sanitizePattern(pattern);
                        log.info("Sanitized pattern ID " + id + " for safe replay");
                    }
                }
            }
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
        if (ENABLE_REQUEST_LIMITING) {
            requestCounts.clear();
        }
        patternErrors.clear();
        log.info("Dictionary cleared");
    }

    /**
     * ERROR_RECOVERY_2024_SHUB: Validate pattern integrity before storage/retrieval.
     *
     * @param pattern Pattern to validate
     * @return null if valid, error message if invalid
     */
    private String validatePattern(final List<ModelValuePair> pattern) {
        if (pattern == null) {
            return "Pattern is null";
        }

        if (pattern.isEmpty()) {
            return "Pattern is empty";
        }

        // Check for null elements
        for (int i = 0; i < pattern.size(); i++) {
            ModelValuePair pair = pattern.get(i);
            if (pair == null) {
                return "Null ModelValuePair at index " + i;
            }
            if (pair.getModel() == null) {
                return "Null model at index " + i;
            }
            // Note: pair.getValue() can be null for some valid models (e.g., END)
        }

        // Check for required TYPE command (similar to server validation)
        boolean hasTypeCommand = false;
        for (ModelValuePair pair : pattern) {
            if (isTypeCommand(pair.getModel())) {
                hasTypeCommand = true;
                break;
            }
        }

        if (!hasTypeCommand) {
            return "Pattern lacks required TYPE command";
        }

        return null; // Pattern is valid
    }

    /**
     * ERROR_RECOVERY_2024_SHUB: Sanitize a problematic pattern for safe replay.
     *
     * @param pattern Original pattern
     * @return Sanitized pattern safe for replay
     */
    private List<ModelValuePair> sanitizePattern(final List<ModelValuePair> pattern) {
        if (pattern == null) {
            return new ArrayList<>();
        }

        List<ModelValuePair> sanitized = new ArrayList<>();

        for (ModelValuePair pair : pattern) {
            if (pair != null && pair.getModel() != null) {
                // Sanitize problematic values
                Object sanitizedValue = pair.getValue();

                // Handle null values for models that shouldn't have them
                if (sanitizedValue == null && requiresNonNullValue(pair.getModel())) {
                    sanitizedValue = getDefaultValue(pair.getModel());
                    log.info("Sanitized null value for " + pair.getModel() + " to: " + sanitizedValue);
                }

                sanitized.add(new ModelValuePair(pair.getModel(), sanitizedValue));
            }
        }

        return sanitized;
    }

    /**
     * Check if a model requires a non-null value for safe replay.
     */
    private boolean requiresNonNullValue(final com.ponysdk.core.model.ServerToClientModel model) {
        // Models that require values for proper client processing
        switch (model) {
            case TYPE_UPDATE:
            case TYPE_CREATE:
            case TYPE_ADD:
            case TYPE_REMOVE:
            case WIDGET_ID:
            case PARENT_OBJECT_ID:
                return true;
            default:
                return false;
        }
    }

    /**
     * Get a safe default value for a model type.
     */
    private Object getDefaultValue(final com.ponysdk.core.model.ServerToClientModel model) {
        switch (model) {
            case TYPE_UPDATE:
            case TYPE_CREATE:
            case TYPE_ADD:
            case TYPE_REMOVE:
            case WIDGET_ID:
            case PARENT_OBJECT_ID:
                return Integer.valueOf(-1); // Safe fallback object ID
            case TEXT:
                return ""; // Empty string
            default:
                return null;
        }
    }

    /**
     * Check if a model is a TYPE command (client-side version of server method).
     */
    private boolean isTypeCommand(final com.ponysdk.core.model.ServerToClientModel model) {
        return model == com.ponysdk.core.model.ServerToClientModel.TYPE_CREATE ||
               model == com.ponysdk.core.model.ServerToClientModel.TYPE_UPDATE ||
               model == com.ponysdk.core.model.ServerToClientModel.TYPE_ADD ||
               model == com.ponysdk.core.model.ServerToClientModel.TYPE_REMOVE ||
               model == com.ponysdk.core.model.ServerToClientModel.TYPE_ADD_HANDLER ||
               model == com.ponysdk.core.model.ServerToClientModel.TYPE_REMOVE_HANDLER ||
               model == com.ponysdk.core.model.ServerToClientModel.TYPE_GC;
    }

    /**
     * Get error information for a pattern ID (for debugging).
     */
    public String getPatternError(final int id) {
        return patternErrors.get(id);
    }

    /**
     * Get the number of request attempts for a pattern ID (for debugging).
     */
    public int getRequestCount(final int id) {
        return requestCounts.getOrDefault(id, 0);
    }
}   