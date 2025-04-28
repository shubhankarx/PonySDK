/*
 * Copyright (c) 2024 PonySDK
 *  Owners:
 *  Luciano Broussal  <luciano.broussal AT gmail.com>
 *  Mathieu Barbier   <mathieu.barbier AT gmail.com>
 *  Nicolas Ciaravola <nicolas.ciaravola.pro AT gmail.com>
 *
 *  WebSite:
 *  http://code.google.com/p/pony-sdk/
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.ponysdk.core.terminal.socket;

import com.ponysdk.core.model.ServerToClientModel;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Tracks and analyzes model-value pairs received from the server to identify patterns
 * and optimize communication.
 */
public class ClientModelTracker {
    private static final Logger log = Logger.getLogger(ClientModelTracker.class.getName());
    
    private final Map<ModelValueKey, Integer> dictionary = new ConcurrentHashMap<>();
    private final int frequencyThreshold;
    private final Map<ModelValueKey, String> keyMap = new ConcurrentHashMap<>();
    private final Map<String, Object> valueMap = new ConcurrentHashMap<>();
    private final AtomicInteger keyCounter = new AtomicInteger(0);
    // New: Added this field with other fields (we already have valueMap)
    private final Map<Integer, String> indexToKeyMap = new ConcurrentHashMap<>();

    
    public ClientModelTracker() {
        this(100); // Default threshold of 100
    }
    
    public ClientModelTracker(int frequencyThreshold) {
        if (frequencyThreshold <= 0) {
            throw new IllegalArgumentException("Frequency threshold must be positive");
        }
        this.frequencyThreshold = frequencyThreshold;
    }
    
    /**
     * Represents a unique model-value pair in the dictionary
     */
    public static class ModelValueKey {
        private final ServerToClientModel model;
        private final Object value;
        
        public ModelValueKey(ServerToClientModel model, Object value) {
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
    }
    
    /**
     * Records a model-value pair in the dictionary.
     * 
     * @param model The ServerToClientModel
     * @param value The value associated with the model
     */
    public void record(ServerToClientModel model, Object value) {
        if (model == null) {
            log.warning("Attempted to record null model");
            return;
        }
        
        ModelValueKey key = new ModelValueKey(model, value);
        int newFrequency = dictionary.merge(key, 1, Integer::sum);
        
        if (newFrequency > 0 && newFrequency % frequencyThreshold == 0) {
            log.info("Frequent pattern detected: " + key + " (frequency: " + newFrequency + ")");
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
        if (model == null) {
            return 0;
        }
        return dictionary.getOrDefault(new ModelValueKey(model, value), 0);
    }
    
    /**
     * Returns the top N most frequent model-value entries.
     *
     * @param limit maximum number of entries to return
     * @return a list of entries sorted by descending frequency
     */
    public List<Map.Entry<ModelValueKey, Integer>> getMostFrequent(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be positive");
        }
        return dictionary.entrySet().stream()
            .sorted(Map.Entry.<ModelValueKey, Integer>comparingByValue().reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    /**
     * Gets the total number of unique model-value pairs in the dictionary.
     */
    public int getUniqueCount() {
        return dictionary.size();
    }
    
    /**
     * Clears all entries from the dictionary.
     */
    public void clear() {
        dictionary.clear();
        keyMap.clear();
        valueMap.clear();
        keyCounter.set(0);
        log.info("Dictionary cleared");
    }
    
    /**
     * Gets the full dictionary map for analysis.
     */
    public Map<ModelValueKey, Integer> getDictionary() {
        return Collections.unmodifiableMap(dictionary);
    }
    
    /**
     * Prints the current state of the dictionary to the log.
     * 
     * @param limit Maximum number of entries to print
     */
    public void printState(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be positive");
        }
        
        log.info("=== Dictionary State ===");
        log.info("Total entries: " + dictionary.size());
        
        List<Map.Entry<ModelValueKey, Integer>> frequent = getMostFrequent(limit);
        if (!frequent.isEmpty()) {
            log.info("Top " + limit + " most frequent entries:");
            for (int i = 0; i < frequent.size(); i++) {
                Map.Entry<ModelValueKey, Integer> entry = frequent.get(i);
                log.info((i + 1) + ". " + entry.getKey() + " (frequency: " + entry.getValue() + ")");
            }
        }
        log.info("=======================");
    }

    public Map<ModelValueKey, Integer> getAllTrackedPatterns() {
        return new HashMap<>(dictionary);
    }

    public Object getValue(int index) {
        for (Map.Entry<ModelValueKey, Integer> entry : dictionary.entrySet()) {
            if (entry.getValue() == index) {
                return entry.getKey().getValue();
            }
        }
        return null;
    }

    public String recordAndGetKey(ServerToClientModel model, Object value) {
        if (model == null) return null;
        
        ModelValueKey key = new ModelValueKey(model, value);
        String dictionaryKey = keyMap.get(key);
        
        if (dictionaryKey == null) {
            // Generate new key using same format as server
            dictionaryKey = "k" + keyCounter.getAndIncrement();
            keyMap.put(key, dictionaryKey);
            valueMap.put(dictionaryKey, value);
        }
        
        return dictionaryKey;
    }

    /**
     * Handle dictionary update from server
     */
    public void handleDictUpdate(int index, String dictKey, Object value) {
        if (value == null) return;
        
        valueMap.put(dictKey, value);
        indexToKeyMap.put(index, dictKey);
    }

    /**
     * Get value by dictionary index
     */
    public Object getValueByIndex(int index) {
        String key = indexToKeyMap.get(index);
        return key == null ? null : valueMap.get(key);
    }


    public Object getValueForKey(String key) {
        return valueMap.get(key);
    }
}