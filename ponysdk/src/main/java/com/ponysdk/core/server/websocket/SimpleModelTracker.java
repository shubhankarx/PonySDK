package com.ponysdk.core.server.websocket;

import com.ponysdk.core.model.ServerToClientModel;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A simple, efficient dictionary that tracks model-value pairs sent through WebSocket.
 */
public class SimpleModelTracker {
    private static final Logger log = Logger.getLogger(SimpleModelTracker.class.getName());
    
    // Maps each model-value pair to its frequency count
    private final Map<ModelValueKey, AtomicInteger> frequencyMap = new ConcurrentHashMap<>();
    private final Map<ModelValueKey, Integer> dictionaryIndices = new ConcurrentHashMap<>();
    private final AtomicInteger nextIndex = new AtomicInteger(0);
    private final Map<ModelValueKey, String> keyMap = new ConcurrentHashMap<>();
    private final Map<String, Object> valueMap = new ConcurrentHashMap<>();
    private final AtomicInteger keyCounter = new AtomicInteger(0);
    
    /**
     * Records a model-value pair in the dictionary.
     * 
     * @param model The ServerToClientModel
     * @param value The value associated with the model
     */
    public void record(ServerToClientModel model, Object value) {
        if (model == null) return;
        
        ModelValueKey key = new ModelValueKey(model, value);
        int frequency = frequencyMap.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
        
        // If frequency crosses threshold, assign a dictionary index
        if (frequency == 100) { // Same threshold as in WebSocket
            dictionaryIndices.putIfAbsent(key, nextIndex.getAndIncrement());
        }
    }
    
    /**
     * Gets the frequency count for a specific model-value pair.
     * 
     * @param model The ServerToClientModel
     * @param value The value associated with the model
     * @return The count of occurrences
     */
    public int getFrequency(ServerToClientModel model, Object value) {
        if (model == null) return 0;
        
        ModelValueKey key = new ModelValueKey(model, value);
        AtomicInteger count = frequencyMap.get(key);
        return count != null ? count.get() : 0;
    }

    /**
     * Gets the dictionary index for a model-value pair.
     * Returns -1 if no index is assigned.
     */
    public int getDictionaryIndex(ServerToClientModel model, Object value) {
        if (model == null) return -1;
        return dictionaryIndices.getOrDefault(new ModelValueKey(model, value), -1);
    }

    /**
     * Gets the model-value pair for a given dictionary index.
     * Returns null if index not found.
     */
    public ModelValueKey getKeyForIndex(int index) {
        for (Map.Entry<ModelValueKey, Integer> entry : dictionaryIndices.entrySet()) {
            if (entry.getValue() == index) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Returns the top N most frequent model‑value entries.
     *
     * @param limit maximum number of entries to return
     * @return a linked map of entries sorted by descending frequency
     */
    public Map<ModelValueKey, Integer> getMostFrequent(final int limit) {
        return frequencyMap.entrySet().stream()
            .sorted((e1, e2) -> Integer.compare(e2.getValue().get(), e1.getValue().get()))
            .limit(limit)
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().get(),
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));
    }
    
    /**
     * Gets the total number of unique model-value pairs in the dictionary.
     */
    public int getUniqueCount() {
        return frequencyMap.size();
    }
    
    /**
     * Clears all entries from the dictionary.
     */
    public void clear() {
        frequencyMap.clear();
        dictionaryIndices.clear();
        keyMap.clear();
        valueMap.clear();
        keyCounter.set(0);
        log.info("SimpleModelTracker dictionary cleared");
    }
    
    /**
     * Gets the full dictionary map for analysis.
     */
    public Map<ModelValueKey, AtomicInteger> getFrequencyMap() {
        return frequencyMap;
    }

    public String recordAndGetKey(ServerToClientModel model, Object value) {
        if (model == null) return null;
        
        ModelValueKey key = new ModelValueKey(model, value);
        String dictionaryKey = keyMap.get(key);
        
        if (dictionaryKey == null) {
            // Generate new key
            dictionaryKey = "k" + keyCounter.getAndIncrement();
            keyMap.put(key, dictionaryKey);
            valueMap.put(dictionaryKey, value);
        }
        
        return dictionaryKey;
    }

    public Object getValueForKey(String key) {
        return valueMap.get(key);
    }
}