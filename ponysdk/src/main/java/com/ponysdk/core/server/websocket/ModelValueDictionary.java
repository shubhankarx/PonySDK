package com.ponysdk.core.server.websocket;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import com.ponysdk.core.model.ServerToClientModel;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Thread-safe dictionary to detect and store repeated patterns of ModelValuePair sequences.
 */
public class ModelValueDictionary {

    private final ConcurrentMap<List<ModelValuePair>, AtomicInteger> patternCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<List<ModelValuePair>, Integer> patternToId = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, List<ModelValuePair>> idToPattern = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final int frequencyThreshold;
    private static final Logger PRED = LoggerFactory.getLogger("PredictionLogger");
    
    public ModelValueDictionary() {
        this(3);
    }

    public ModelValueDictionary(final int frequencyThreshold) {
        this.frequencyThreshold = frequencyThreshold;
    }

    /**
     * Records pattern occurrences for WebSocket dictionary compression optimization.
     * 
     * This method tracks UI operation patterns (sequences of ModelValuePair) and promotes
     * frequently-occurring patterns into the dictionary when they meet the frequency threshold.
     * Dictionary entries receive unique IDs for efficient network compression between server and client.
     *
     * CRITICAL BUG FIX (HashMap Key Type Mismatch):
     * Previous implementation stored patterns as Collections.unmodifiableList() in the HashMap
     * but performed lookups with regular ArrayList instances, causing all lookups to fail.
     * Now uses normalized ArrayList consistently for both storage and retrieval operations.
     *
     * @param pattern List of ModelValuePair representing a UI operation sequence. 
     *                Must contain at least one TYPE_* command to be valid.
     * @return Pattern ID if already in dictionary or just promoted; null if still accumulating occurrences
     */
    public Integer recordPattern(final List<ModelValuePair> pattern) {
        // Null/empty validation - patterns must have content
        if (pattern == null || pattern.isEmpty()) {
            return null;
        }

        // Verify pattern contains TYPE_* command (TYPE_CREATE, TYPE_UPDATE, etc.)
        // Required for valid UI operation replay on client side
        boolean hasTypeCommand = false;
        for (ModelValuePair pair : pattern) {
            if (isTypeCommand(pair.getModel())) {
                hasTypeCommand = true;
                break;  // Found required command, validation passes
            }
        }

        // Reject incomplete patterns lacking TYPE_* command
        // These cannot be safely replayed and would corrupt client state
        if (!hasTypeCommand) {
            return null;
        }

        // FIX: Normalize pattern to ArrayList for consistent HashMap operations
        // This ensures the same List implementation type for all HashMap interactions
        final List<ModelValuePair> normalizedPattern = new ArrayList<>(pattern);
        
        // Check if pattern already exists in dictionary using normalized key
        final Integer existing = patternToId.get(normalizedPattern);
        if (existing != null) {
            return existing;  // Pattern already compressed, return its ID
        }

        // Track pattern frequency - computeIfAbsent ensures thread-safe counter creation
        final AtomicInteger count = patternCounts.computeIfAbsent(normalizedPattern, k -> new AtomicInteger(0));
        
        // Check if pattern has reached promotion threshold
        if (count.incrementAndGet() >= frequencyThreshold) {
            // Generate unique ID for new dictionary entry
            final int id = nextId.getAndIncrement();
            
            // Atomic check-and-set: only store if no other thread recorded this pattern
            // This follows the established putIfAbsent pattern used throughout the codebase
            final Integer existingId = patternToId.putIfAbsent(normalizedPattern, id);
            if (existingId != null) {
                // Another thread won the race - return their ID
                return existingId;
            }
            
            // We successfully recorded the pattern - complete the setup
            // Store immutable copy for external access (maintains encapsulation)
            idToPattern.put(id, Collections.unmodifiableList(new ArrayList<>(normalizedPattern)));
            
            // Remove from tracking - pattern is now in dictionary
            patternCounts.remove(normalizedPattern);
            
            // Log pattern promotion for monitoring/debugging
            PRED.info("Pattern #{} stored in dictionary: {} operations, threshold={}", 
                     id, normalizedPattern.size(), frequencyThreshold);
            
            // Log the actual pattern contents for debugging
            StringBuilder patternDetails = new StringBuilder();
            patternDetails.append("Pattern #").append(id).append(" contents: [");
            for (int i = 0; i < normalizedPattern.size(); i++) {
                ModelValuePair pair = normalizedPattern.get(i);
                patternDetails.append(pair.getModel().name()).append("=").append(pair.getValue());
                if (i < normalizedPattern.size() - 1) {
                    patternDetails.append(", ");
                }
            }
            patternDetails.append("]");
            PRED.info(patternDetails.toString());
            
            return id;  // Return new dictionary ID
        }
        
        // Pattern occurrence recorded but threshold not yet met
        return null;
    }

    /**
     * Get pattern ID if exists, otherwise null.
     */
    public Integer getPatternId(final List<ModelValuePair> pattern) {
        if (pattern == null) return null;
        // FIX: Normalize pattern to ArrayList for consistent HashMap lookup
        final List<ModelValuePair> normalizedPattern = new ArrayList<>(pattern);
        return patternToId.get(normalizedPattern);
    }

    /**
     * Get pattern by ID if exists, otherwise null.
     */
    public List<ModelValuePair> getPattern(final int id) {
        return idToPattern.get(id);
    }

    /**
     * Clear all patterns and counts.
     */
    public void clear() {
        patternCounts.clear();
        patternToId.clear();
        idToPattern.clear();
        nextId.set(1);
    }

    /**
     * Expose the set of all recorded pattern IDs.
     */
    public Set<Integer> getPatternIds() {
        return Collections.unmodifiableSet(idToPattern.keySet());
    }

    /**
     * Check if a model is a TYPE_* command (used for update routing)
     */
    private boolean isTypeCommand(ServerToClientModel model) {
        return model == ServerToClientModel.TYPE_CREATE ||
               model == ServerToClientModel.TYPE_UPDATE ||
               model == ServerToClientModel.TYPE_ADD ||
               model == ServerToClientModel.TYPE_REMOVE ||
               model == ServerToClientModel.TYPE_ADD_HANDLER ||
               model == ServerToClientModel.TYPE_REMOVE_HANDLER ||
               model == ServerToClientModel.TYPE_GC;
    }
} 