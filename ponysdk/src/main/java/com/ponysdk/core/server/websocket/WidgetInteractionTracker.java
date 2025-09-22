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
 * Widget Interaction Tracker for learning UI patterns.
 *
 * This engine was extracted from WebSocket.java lines 96-100 and associated methods.
 * It tracks widget interaction sequences and message patterns that were added
 * as machine learning optimization features after the original 439-line implementation.
 *
 * EXTRACTED FUNCTIONALITY:
 * - Widget interaction sequence tracking
 * - Widget-to-message pattern correlation
 * - Widget type mapping and identification
 * - Trie-based pattern learning integration
 */
public class WidgetInteractionTracker {

    private static final Logger log = LoggerFactory.getLogger(WidgetInteractionTracker.class);
    private static final Logger PRED = LoggerFactory.getLogger("PredictionLogger");

    private final List<String> widgetInteractionSequence;
    private final Map<String, List<ModelValuePair>> widgetMessagePatterns;
    private final Map<Integer, String> widgetTypeById;

    private String currentWidgetKey;
    private String currentWidgetType;
    private Integer currentWidgetId;

    public WidgetInteractionTracker() {
        this.widgetInteractionSequence = new ArrayList<>();
        this.widgetMessagePatterns = new HashMap<>();
        this.widgetTypeById = new HashMap<>();
    }

    public void trackWidgetInteraction(Integer widgetId, String widgetType) {
        if (!WebSocketConfiguration.isTrieEnabled() || widgetId == null || widgetType == null) {
            return;
        }

        try {
            String widgetKey = widgetType + "#" + widgetId;

            widgetInteractionSequence.add(widgetKey);
            widgetTypeById.put(widgetId, widgetType);

            this.currentWidgetKey = widgetKey;
            this.currentWidgetType = widgetType;
            this.currentWidgetId = widgetId;

            if (widgetInteractionSequence.size() > 10) {
                widgetInteractionSequence.remove(0);
            }

            PRED.debug("Tracked widget interaction: {} (sequence size: {})",
                      widgetKey, widgetInteractionSequence.size());

        } catch (Exception e) {
            log.error("Error tracking widget interaction for widget {} type {}", widgetId, widgetType, e);
        }
    }

    public void associateMessagePattern(String widgetKey, List<ModelValuePair> messagePattern) {
        if (!WebSocketConfiguration.isTrieEnabled() || widgetKey == null || messagePattern == null) {
            return;
        }

        try {
            widgetMessagePatterns.computeIfAbsent(widgetKey, k -> new ArrayList<>()).addAll(messagePattern);

            PRED.debug("Associated message pattern with widget {}: {} items",
                      widgetKey, messagePattern.size());

        } catch (Exception e) {
            log.error("Error associating message pattern with widget {}", widgetKey, e);
        }
    }

    public List<String> getWidgetInteractionSequence() {
        return Collections.unmodifiableList(widgetInteractionSequence);
    }

    public List<String> getRecentWidgetSequence(int count) {
        int size = widgetInteractionSequence.size();
        int start = Math.max(0, size - count);
        return Collections.unmodifiableList(widgetInteractionSequence.subList(start, size));
    }

    public List<ModelValuePair> getMessagePatternForWidget(String widgetKey) {
        List<ModelValuePair> pattern = widgetMessagePatterns.get(widgetKey);
        return pattern != null ? Collections.unmodifiableList(pattern) : null;
    }

    public String getWidgetType(Integer widgetId) {
        return widgetTypeById.get(widgetId);
    }

    public String getCurrentWidgetKey() {
        return currentWidgetKey;
    }

    public String getCurrentWidgetType() {
        return currentWidgetType;
    }

    public Integer getCurrentWidgetId() {
        return currentWidgetId;
    }

    public List<String> getWidgetSequenceForTriePrediction() {
        if (!WebSocketConfiguration.isTrieEnabled()) {
            return Collections.emptyList();
        }

        return getRecentWidgetSequence(3);
    }

    public void buildWidgetTrie(WebSocket.WidgetTrieNode widgetTrie) {
        if (!WebSocketConfiguration.isTrieEnabled() || widgetTrie == null) {
            return;
        }

        try {
            if (widgetInteractionSequence.size() >= 3) {
                List<String> sequence = getRecentWidgetSequence(3);

                WebSocket.WidgetTrieNode currentNode = widgetTrie;
                for (String widgetKey : sequence) {
                    currentNode = currentNode.children.computeIfAbsent(widgetKey, k -> new WebSocket.WidgetTrieNode());
                }

                currentNode.isEndOfSequence = true;
                currentNode.completeWidgetSequence = new ArrayList<>(sequence);
                currentNode.sequenceFrequency++;

                List<ModelValuePair> messagePattern = getMessagePatternForWidget(getCurrentWidgetKey());
                if (messagePattern != null) {
                    currentNode.completeMessagePattern = new ArrayList<>(messagePattern);
                }

                PRED.debug("Built widget trie for sequence: {} (frequency: {})",
                          sequence, currentNode.sequenceFrequency);
            }

        } catch (Exception e) {
            log.error("Error building widget trie", e);
        }
    }

    public void clearCurrentWidget() {
        currentWidgetKey = null;
        currentWidgetType = null;
        currentWidgetId = null;
    }

    public void reset() {
        widgetInteractionSequence.clear();
        widgetMessagePatterns.clear();
        widgetTypeById.clear();
        clearCurrentWidget();
    }

    public WidgetTrackerStats getStats() {
        return new WidgetTrackerStats(
            widgetInteractionSequence.size(),
            widgetMessagePatterns.size(),
            widgetTypeById.size(),
            currentWidgetKey,
            WebSocketConfiguration.isTrieEnabled()
        );
    }

    public static class WidgetTrackerStats {
        public final int sequenceSize;
        public final int patternCount;
        public final int widgetTypeCount;
        public final String currentWidget;
        public final boolean enabled;

        public WidgetTrackerStats(int sequenceSize, int patternCount, int widgetTypeCount,
                                String currentWidget, boolean enabled) {
            this.sequenceSize = sequenceSize;
            this.patternCount = patternCount;
            this.widgetTypeCount = widgetTypeCount;
            this.currentWidget = currentWidget;
            this.enabled = enabled;
        }

        @Override
        public String toString() {
            return String.format("WidgetTracker[sequence=%d, patterns=%d, types=%d, current=%s, enabled=%s]",
                               sequenceSize, patternCount, widgetTypeCount, currentWidget, enabled);
        }
    }
}