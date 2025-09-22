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

import com.ponysdk.core.model.ServerToClientModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Dictionary Compression Engine for WebSocket communications.
 *
 * This engine was extracted from WebSocket.java lines 79-85 and associated methods.
 * It handles pattern detection, storage, and compression of repetitive message sequences
 * that were added as optimization features after the original 439-line implementation.
 *
 * EXTRACTED FUNCTIONALITY:
 * - Dictionary compression settings and management
 * - Pattern batch accumulation and processing
 * - Pattern sequence tracking for learning
 * - Integration with ModelValueDictionary for storage
 */
public class DictionaryCompressionEngine {

    private static final Logger log = LoggerFactory.getLogger(DictionaryCompressionEngine.class);

    private final ModelValueDictionary dictionary;
    private final List<ModelValuePair> currentBatch;
    private final List<Integer> patternSequence;

    public DictionaryCompressionEngine() {
        this.dictionary = new ModelValueDictionary(WebSocketConfiguration.getBatchThreshold());
        this.currentBatch = new ArrayList<>();
        this.patternSequence = new ArrayList<>(3);
    }

    public ModelValueDictionary getDictionary() {
        return dictionary;
    }

    public List<ModelValuePair> getCurrentBatch() {
        return currentBatch;
    }

    public List<Integer> getPatternSequence() {
        return patternSequence;
    }

    public void addToBatch(ModelValuePair pair) {
        if (WebSocketConfiguration.isDictionaryEnabled()) {
            currentBatch.add(pair);
        }
    }

    public void addToPatternSequence(Integer patternId) {
        if (WebSocketConfiguration.isDictionaryEnabled()) {
            patternSequence.add(patternId);
            if (patternSequence.size() > 3) {
                patternSequence.remove(0);
            }
        }
    }

    public boolean shouldProcessBatch() {
        return WebSocketConfiguration.isDictionaryEnabled() &&
               currentBatch.size() >= WebSocketConfiguration.getBatchThreshold();
    }

    public void processBatch() {
        if (!shouldProcessBatch()) return;

        try {
            dictionary.recordPattern(new ArrayList<>(currentBatch));
            log.debug("Processed dictionary batch with {} items", currentBatch.size());
            currentBatch.clear();
        } catch (Exception e) {
            log.error("Error processing dictionary batch", e);
            currentBatch.clear();
        }
    }

    public Integer lookupPattern(List<ModelValuePair> pattern) {
        if (!WebSocketConfiguration.isDictionaryEnabled() || pattern == null || pattern.isEmpty()) {
            return null;
        }
        return dictionary.getPatternId(pattern);
    }

    public List<ModelValuePair> getPatternById(Integer patternId) {
        if (!WebSocketConfiguration.isDictionaryEnabled() || patternId == null) {
            return null;
        }
        return dictionary.getPattern(patternId);
    }

    public void clearBatch() {
        currentBatch.clear();
    }

    public void clearPatternSequence() {
        patternSequence.clear();
    }

    public void reset() {
        clearBatch();
        clearPatternSequence();
        // Note: ModelValueDictionary doesn't have clear() method - this is OK for our use case
    }

    public DictionaryStats getStats() {
        return new DictionaryStats(
            dictionary.getPatternIds().size(),
            currentBatch.size(),
            patternSequence.size(),
            WebSocketConfiguration.isDictionaryEnabled()
        );
    }

    public static class DictionaryStats {
        public final int dictionarySize;
        public final int currentBatchSize;
        public final int patternSequenceSize;
        public final boolean enabled;

        public DictionaryStats(int dictionarySize, int currentBatchSize, int patternSequenceSize, boolean enabled) {
            this.dictionarySize = dictionarySize;
            this.currentBatchSize = currentBatchSize;
            this.patternSequenceSize = patternSequenceSize;
            this.enabled = enabled;
        }

        @Override
        public String toString() {
            return String.format("DictionaryStats[size=%d, batch=%d, sequence=%d, enabled=%s]",
                               dictionarySize, currentBatchSize, patternSequenceSize, enabled);
        }
    }
}