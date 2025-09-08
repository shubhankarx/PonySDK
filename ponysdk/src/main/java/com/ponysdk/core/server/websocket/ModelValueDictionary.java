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
    /**
     * Core dictionary method that handles BOTH pattern storage AND retrieval.
     * 
     * COMPLETE FLOW OVERVIEW:
     * =======================
     * Called EVERY time a pattern needs to be sent to client - serves dual purpose!
     * 
     * Pattern Lifecycle (for same pattern X):
     * 1st call → Records occurrence, returns null → WebSocket sends raw frames
     * 2nd call → Records occurrence, returns null → WebSocket sends raw frames  
     * 3rd call → Stores in dictionary, returns NEW ID → WebSocket sends raw frames
     * 4th+ call → Finds in dictionary, returns EXISTING ID → WebSocket sends reference!
     * 
     * Bandwidth savings happen on 4th+ call - just send ID instead of full data.
     * 
     * DECISION TREE:
     * 1. Validate pattern (not empty, has TYPE command)
     * 2. Normalize to ArrayList for HashMap consistency
     * 3. Check if pattern exists in dictionary:
     *    - YES (4th+ time): Return existing ID immediately
     *    - NO: Continue to step 4
     * 4. Track pattern occurrence count
     * 5. Check if count reached threshold (3):
     *    - YES (3rd time): Store pattern, generate ID, return new ID
     *    - NO (1st-2nd time): Just increment counter, return null
     * 
     * @param pattern The UI operation pattern to record/lookup
     * @return null (1st-2nd time), new ID (3rd time), or existing ID (4th+ time)
     */
    public Integer recordPattern(final List<ModelValuePair> pattern) {
        // ==================== VALIDATION PHASE ====================
        // Step 1: Null/empty check - patterns must have content
        if (pattern == null || pattern.isEmpty()) {
            return null;
        }

        // Step 2: Verify pattern contains TYPE_* command (TYPE_CREATE, TYPE_UPDATE, etc.)
        // Required for valid UI operation replay on client side
        boolean hasTypeCommand = false;
        for (ModelValuePair pair : pattern) {
            if (isTypeCommand(pair.getModel())) {
                hasTypeCommand = true;
                break;  // Found required command, validation passes
            }
        }

        // Step 3: Reject incomplete patterns lacking TYPE_* command
        // These cannot be safely replayed and would corrupt client state
        if (!hasTypeCommand) {
            return null;
        }

        // ==================== NORMALIZATION PHASE ====================
        // Step 4: Normalize pattern to ArrayList for consistent HashMap operations
        // CRITICAL: Without this, LinkedList vs ArrayList would never match in HashMap!
        final List<ModelValuePair> normalizedPattern = new ArrayList<>(pattern);
        
        // ==================== DICTIONARY LOOKUP PHASE ====================
        // Step 5: CHECK FIRST if pattern already exists (handles 4th+ occurrences)
        // This is the KEY optimization - reuse existing patterns!
        final Integer existing = patternToId.get(normalizedPattern);
        if (existing != null) {
            // SUCCESS! Pattern found in dictionary (4th+ occurrence)
            // Return existing ID so WebSocket can send reference instead of raw frames
            return existing;  // Pattern already compressed, return its ID
        }

        // ==================== OCCURRENCE TRACKING PHASE ====================
        // Step 6: Pattern not in dictionary yet (1st, 2nd, or 3rd occurrence)
        // Track pattern frequency - computeIfAbsent ensures thread-safe counter creation
        final AtomicInteger count = patternCounts.computeIfAbsent(normalizedPattern, k -> new AtomicInteger(0));
        
        // Step 7: Increment count and check if pattern reached threshold (default: 3)
        if (count.incrementAndGet() >= frequencyThreshold) {
            // ==================== DICTIONARY STORAGE PHASE (3rd occurrence) ====================
            // Step 7a: Pattern has been seen 3 times - promote to dictionary!
            // Generate unique ID for new dictionary entry
            final int id = nextId.getAndIncrement();
            
            // Step 7b: Atomic check-and-set to handle concurrent access
            // Only one thread wins if multiple threads try to store same pattern
            final Integer existingId = patternToId.putIfAbsent(normalizedPattern, id);
            if (existingId != null) {
                // Another thread won the race - return their ID
                return existingId;
            }
            
            // Step 7c: We won! Store reverse mapping for client replay
            // Store immutable copy for external access (maintains encapsulation)
            idToPattern.put(id, Collections.unmodifiableList(new ArrayList<>(normalizedPattern)));
            
            // Step 7d: Cleanup - remove from occurrence tracking (now in dictionary)
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
            
            // Step 7e: Return new ID - pattern is now stored and reusable!
            return id;  // Return new dictionary ID
        }
        
        // ==================== BELOW THRESHOLD (1st or 2nd occurrence) ====================
        // Step 8: Pattern count < threshold - just tracking, not storing yet
        // Return null to indicate pattern not in dictionary
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