package com.ponysdk.core.server.websocket;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

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
     */
    public Integer recordPattern(final List<ModelValuePair> pattern) {
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
     * Retrieve stored pattern sequence by ID.
     */
    public List<ModelValuePair> getPattern(final int id) {
        return idToPattern.get(id);
    }

    /**
     * Clear all recorded patterns and reset dictionary state.
     */
    public void clear() {
        patternCounts.clear();
        patternToId.clear();
        idToPattern.clear();
        nextId.set(1);
    }
} 