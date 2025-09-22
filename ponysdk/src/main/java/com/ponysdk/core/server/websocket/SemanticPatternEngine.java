/*
 * Copyright (c) 2017 PonySDK
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

package com.ponysdk.core.server.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Semantic Pattern Engine for advanced pattern matching and prediction.
 *
 * This engine was extracted from WebSocket.java lines 86-95 and associated methods.
 * It handles semantic pattern buffer management, triplet pattern detection,
 * and prediction comparison logic that were added as AI optimization features
 * after the original 439-line implementation.
 *
 * EXTRACTED FUNCTIONALITY:
 * - Current pattern buffer management
 * - Last prediction tracking and comparison
 * - Thread-safe prediction buffer access
 * - Accumulated patterns collection for machine learning
 */
public class SemanticPatternEngine {

    private static final Logger log = LoggerFactory.getLogger(SemanticPatternEngine.class);
    private static final Logger PRED = LoggerFactory.getLogger("PredictionLogger");

    private final List<String> currentPatternBuffer;
    private final List<List<String>> accumulatedPatterns;
    private final Object predictionLock;

    private String lastPrediction;

    public SemanticPatternEngine() {
        this.currentPatternBuffer = new ArrayList<>();
        this.accumulatedPatterns = Collections.synchronizedList(new ArrayList<>());
        this.predictionLock = new Object();
        this.lastPrediction = null;
    }

    public void addToPatternBuffer(String instruction) {
        if (!WebSocketConfiguration.isCodeT5Enabled() || instruction == null || instruction.trim().isEmpty()) {
            return;
        }

        synchronized (predictionLock) {
            try {
                currentPatternBuffer.add(instruction.trim());

                if (currentPatternBuffer.size() > 3) {
                    currentPatternBuffer.remove(0);
                }

                PRED.debug("Added instruction to pattern buffer: '{}' (buffer size: {})",
                          instruction, currentPatternBuffer.size());

                if (currentPatternBuffer.size() == 3) {
                    processCompleteTriplet();
                }

            } catch (Exception e) {
                log.error("Error adding instruction to pattern buffer: {}", instruction, e);
            }
        }
    }

    private void processCompleteTriplet() {
        try {
            List<String> triplet = new ArrayList<>(currentPatternBuffer);
            accumulatedPatterns.add(triplet);

            PRED.debug("Processed complete triplet pattern: {}", triplet);

            if (accumulatedPatterns.size() > 1000) {
                accumulatedPatterns.remove(0);
            }

        } catch (Exception e) {
            log.error("Error processing complete triplet", e);
        }
    }

    public void setLastPrediction(String prediction) {
        synchronized (predictionLock) {
            this.lastPrediction = prediction;
            PRED.debug("Updated last prediction: '{}'", prediction);
        }
    }

    public String getLastPrediction() {
        synchronized (predictionLock) {
            return lastPrediction;
        }
    }

    public List<String> getCurrentPatternBuffer() {
        synchronized (predictionLock) {
            return Collections.unmodifiableList(new ArrayList<>(currentPatternBuffer));
        }
    }

    public List<String> getCurrentTriplet() {
        synchronized (predictionLock) {
            if (currentPatternBuffer.size() == 3) {
                return Collections.unmodifiableList(new ArrayList<>(currentPatternBuffer));
            }
            return Collections.emptyList();
        }
    }

    public List<List<String>> getAccumulatedPatterns() {
        return Collections.unmodifiableList(new ArrayList<>(accumulatedPatterns));
    }

    public List<List<String>> getRecentPatterns(int count) {
        List<List<String>> patterns = new ArrayList<>(accumulatedPatterns);
        int size = patterns.size();
        int start = Math.max(0, size - count);
        return Collections.unmodifiableList(patterns.subList(start, size));
    }

    public boolean hasCompleteTriplet() {
        synchronized (predictionLock) {
            return currentPatternBuffer.size() == 3;
        }
    }

    public boolean matchesPrediction(String actualPattern) {
        if (!WebSocketConfiguration.isCodeT5Enabled() || actualPattern == null) {
            return false;
        }

        synchronized (predictionLock) {
            if (lastPrediction == null) {
                return false;
            }

            boolean matches = lastPrediction.equals(actualPattern.trim());
            PRED.debug("Prediction match check: predicted='{}', actual='{}', matches={}",
                      lastPrediction, actualPattern, matches);

            return matches;
        }
    }

    public void buildSemanticTrie(WebSocket.TrieNode dictTrie) {
        if (!WebSocketConfiguration.isCodeT5Enabled() || dictTrie == null) {
            return;
        }

        try {
            List<List<String>> patterns = getRecentPatterns(100);
            TrieUtilities.buildSemanticPatternTrieFromStrings(patterns, dictTrie);

            PRED.debug("Built semantic trie from {} recent patterns", patterns.size());

        } catch (Exception e) {
            log.error("Error building semantic trie", e);
        }
    }

    public void clearPatternBuffer() {
        synchronized (predictionLock) {
            currentPatternBuffer.clear();
            PRED.debug("Cleared pattern buffer");
        }
    }

    public void clearLastPrediction() {
        synchronized (predictionLock) {
            lastPrediction = null;
            PRED.debug("Cleared last prediction");
        }
    }

    public void reset() {
        synchronized (predictionLock) {
            currentPatternBuffer.clear();
            lastPrediction = null;
        }
        accumulatedPatterns.clear();
        PRED.debug("Reset semantic pattern engine");
    }

    public SemanticPatternStats getStats() {
        synchronized (predictionLock) {
            return new SemanticPatternStats(
                currentPatternBuffer.size(),
                accumulatedPatterns.size(),
                lastPrediction != null,
                hasCompleteTriplet(),
                WebSocketConfiguration.isCodeT5Enabled()
            );
        }
    }

    public static class SemanticPatternStats {
        public final int bufferSize;
        public final int accumulatedCount;
        public final boolean hasPrediction;
        public final boolean hasCompleteTriplet;
        public final boolean enabled;

        public SemanticPatternStats(int bufferSize, int accumulatedCount, boolean hasPrediction,
                                  boolean hasCompleteTriplet, boolean enabled) {
            this.bufferSize = bufferSize;
            this.accumulatedCount = accumulatedCount;
            this.hasPrediction = hasPrediction;
            this.hasCompleteTriplet = hasCompleteTriplet;
            this.enabled = enabled;
        }

        @Override
        public String toString() {
            return String.format("SemanticPattern[buffer=%d, accumulated=%d, prediction=%s, triplet=%s, enabled=%s]",
                               bufferSize, accumulatedCount, hasPrediction, hasCompleteTriplet, enabled);
        }
    }
}