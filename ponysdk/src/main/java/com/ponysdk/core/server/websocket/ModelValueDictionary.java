package com.ponysdk.core.server.websocket;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import com.ponysdk.core.model.ServerToClientModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-safe dictionary to detect and store repeated patterns of ModelValuePair sequences.
 */
public class ModelValueDictionary {
    private static final Logger log = LoggerFactory.getLogger(ModelValueDictionary.class);

    /**
     * PatternKey provides deep equality checking for lists of ModelValuePair
     * to work correctly with ConcurrentHashMap
     */
    private static class PatternKey {
        private final List<ModelValuePair> pattern;
        private final int hash;
        
        public PatternKey(List<ModelValuePair> pattern) {
            this.pattern = pattern;
            this.hash = computeHash(pattern);
        }
        
        private int computeHash(List<ModelValuePair> pattern) {
            int result = 1;
            for (ModelValuePair pair : pattern) {
                result = 31 * result + pair.hashCode();
            }
            return result;
        }
        
        public List<ModelValuePair> getPattern() {
            return pattern;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PatternKey)) return false;
            PatternKey other = (PatternKey) o;
            
            if (pattern.size() != other.pattern.size()) return false;
            
            for (int i = 0; i < pattern.size(); i++) {
                if (!pattern.get(i).equals(other.pattern.get(i))) {
                    return false;
                }
            }
            
            return true;
        }
        
        @Override
        public int hashCode() {
            return hash;
        }
    }

    private final ConcurrentMap<PatternKey, AtomicInteger> patternCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<PatternKey, Integer> patternToId = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, List<ModelValuePair>> idToPattern = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final int frequencyThreshold;

    // Stats tracking
    private long totalFramesSent = 0;
    private long compressedFramesSent = 0;

    public ModelValueDictionary() {
        this(2); // Lower threshold to 2 to detect patterns more quickly
    }

    public ModelValueDictionary(final int frequencyThreshold) {
        this.frequencyThreshold = frequencyThreshold;
    }

    /**
     * Record one occurrence of a pattern. Returns existing or new ID if threshold reached, otherwise null.
     */
    public Integer recordPattern(final List<ModelValuePair> pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return null;
        }

        // Check if pattern has a type command - patterns without type commands can cause issues
        boolean hasTypeCommand = false;
        for (ModelValuePair pair : pattern) {
            if (isTypeCommand(pair.getModel())) {
                hasTypeCommand = true;
                break;
            }
        }

        // Skip patterns that don't have a TYPE_* command as they can't be properly replayed
        if (!hasTypeCommand) {
            return null;
        }

        PatternKey key = new PatternKey(pattern);
        
        // First check if this exact pattern is already known
        Integer existing = patternToId.get(key);
        if (existing != null) {
            log.debug("Using existing pattern ID {} for pattern of size {}", existing, pattern.size());
            return existing;
        }

        // Record new occurrence
        AtomicInteger count = patternCounts.computeIfAbsent(key, k -> new AtomicInteger(0));
        int newCount = count.incrementAndGet();
        log.debug("Pattern occurrence count increased to {} (threshold: {})", newCount, frequencyThreshold);
        
        if (newCount >= frequencyThreshold) {
            List<ModelValuePair> stored = Collections.unmodifiableList(new ArrayList<>(pattern));
            int id = nextId.getAndIncrement();
            PatternKey storedKey = new PatternKey(stored);
            patternToId.put(storedKey, id);
            idToPattern.put(id, stored);
            patternCounts.remove(key);
            log.info("New pattern {} recorded with size {}", id, pattern.size());
            return id;
        }
        return null;
    }

    /**
     * Get pattern ID if exists, otherwise null.
     */
    public Integer getPatternId(final List<ModelValuePair> pattern) {
        PatternKey key = new PatternKey(pattern);
        return patternToId.get(key);
    }

    /**
     * Get pattern by ID if exists, otherwise null.
     */
    public List<ModelValuePair> getPattern(final int id) {
        return idToPattern.get(id);
    }

    /**
     * Get a copy of the pattern map for logging and debugging
     */
    public Map<Integer, List<ModelValuePair>> getPatternMap() {
        return Collections.unmodifiableMap(idToPattern);
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

    /**
     * Increment total frames sent counter
     */
    public void incrementTotalFrames() {
        totalFramesSent++;
    }
    
    /**
     * Increment compressed frames counter
     */
    public void incrementCompressedFrames() {
        compressedFramesSent++;
    }
    
    /**
     * Get total frames sent
     */
    public long getTotalFramesSent() {
        return totalFramesSent;
    }
    
    /**
     * Get compressed frames sent
     */
    public long getCompressedFramesSent() {
        return compressedFramesSent;
    }
} 