package com.ponysdk.core.server.websocket;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * A dictionary that tracks and compresses repetitive model-value patterns
 * in WebSocket communications to reduce bandwidth usage.
 */
public class ModelValueDictionary {
    
    private static final Logger log = Logger.getLogger(ModelValueDictionary.class.getName());
    
    // Dictionary maps patterns to IDs
    private final Map<PatternKey, Integer> patternToId = new ConcurrentHashMap<>();
    
    // Reverse lookup for decoding
    private final Map<Integer, List<ModelValuePair>> idToPattern = new ConcurrentHashMap<>();
    
    // Frequency tracking for optimization
    private final Map<PatternKey, AtomicInteger> patternFrequency = new ConcurrentHashMap<>();
    
    // Counter for generating pattern IDs
    private final AtomicInteger nextPatternId = new AtomicInteger(1);
    
    // Threshold for dictionary inclusion
    private static final int PATTERN_FREQUENCY_THRESHOLD = 3;
    
    /**
     * Records a sequence of model-value pairs for potential dictionary entry
     * @param pattern List of model-value pairs to record
     */
    public void recordPattern(List<ModelValuePair> pattern) {
        if (pattern == null || pattern.size() < 2) return; // Only record multi-value patterns
        
        PatternKey key = new PatternKey(pattern);
        patternFrequency
            .computeIfAbsent(key, k -> new AtomicInteger(0))
            .incrementAndGet();
            
        // Add to dictionary if used enough times and not already present
        if (!patternToId.containsKey(key) && 
            patternFrequency.get(key).get() >= PATTERN_FREQUENCY_THRESHOLD) {
            
            int id = nextPatternId.getAndIncrement();
            patternToId.put(key, id);
            idToPattern.put(id, new ArrayList<>(pattern));
            
            if (log.isLoggable(java.util.logging.Level.FINE)) {
                log.fine("Added pattern to dictionary with ID " + id + ": " + pattern);
            }
        }
    }
    
    /**
     * Get pattern ID if it exists in dictionary
     * @param pattern The pattern to look up
     * @return The ID of the pattern, or null if not found
     */
    public Integer getPatternId(List<ModelValuePair> pattern) {
        return pattern.size() < 2 ? null : patternToId.get(new PatternKey(pattern));
    }
    
    /**
     * Get pattern for a given ID
     * @param id The pattern ID
     * @return The list of model-value pairs, or null if not found
     */
    public List<ModelValuePair> getPattern(int id) {
        return idToPattern.get(id);
    }
    
    /**
     * Clear the dictionary
     */
    public void clear() {
        patternToId.clear();
        idToPattern.clear();
        patternFrequency.clear();
    }
    
    /**
     * Get the number of patterns in the dictionary
     * @return The dictionary size
     */
    public int size() {
        return patternToId.size();
    }
    
    /**
     * PatternKey to handle proper equality comparison for patterns
     */
    private static class PatternKey {
        private final List<ModelValuePair> pattern;
        
        public PatternKey(List<ModelValuePair> pattern) {
            this.pattern = pattern;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PatternKey)) return false;
            PatternKey that = (PatternKey) o;
            if (pattern.size() != that.pattern.size()) return false;
            
            for (int i = 0; i < pattern.size(); i++) {
                if (!pattern.get(i).equals(that.pattern.get(i))) return false;
            }
            return true;
        }
        
        @Override
        public int hashCode() {
            int result = 1;
            for (ModelValuePair pair : pattern) {
                result = 31 * result + pair.hashCode();
            }
            return result;
        }
    }
}