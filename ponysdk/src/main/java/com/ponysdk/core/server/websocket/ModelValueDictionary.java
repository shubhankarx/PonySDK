package com.ponysdk.core.server.websocket;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import com.ponysdk.core.model.ServerToClientModel;

/**
 * Thread-safe dictionary to detect and store repeated patterns of ModelValuePair sequences.
 */
public class ModelValueDictionary {

    private final ConcurrentMap<List<ModelValuePair>, AtomicInteger> patternCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<List<ModelValuePair>, Integer> patternToId = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, List<ModelValuePair>> idToPattern = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final int frequencyThreshold;

    public ModelValueDictionary() {
        this(3);
    }

    public ModelValueDictionary(final int frequencyThreshold) {
        this.frequencyThreshold = frequencyThreshold;
    }

    /**
     * Record one occurrence of a pattern. Returns existing or new ID if threshold reached, otherwise null.
     * Added validation to ensure we don't store invalid patterns.
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

        final Integer existing = patternToId.get(pattern);
        if (existing != null) return existing;

        final AtomicInteger count = patternCounts.computeIfAbsent(pattern, k -> new AtomicInteger(0));
        if (count.incrementAndGet() >= frequencyThreshold) {
            final List<ModelValuePair> stored = Collections.unmodifiableList(new ArrayList<>(pattern));
            final int id = nextId.getAndIncrement();
            patternToId.put(stored, id);
            idToPattern.put(id, stored);
            patternCounts.remove(pattern);
            return id;
        }
        return null;
    }

    /**
     * Get pattern ID if exists, otherwise null.
     */
    public Integer getPatternId(final List<ModelValuePair> pattern) {
        return patternToId.get(pattern);
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