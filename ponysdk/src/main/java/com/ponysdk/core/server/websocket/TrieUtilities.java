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

import com.ponysdk.core.server.websocket.WebSocket.TrieNode;
import com.ponysdk.core.server.websocket.WebSocket.WidgetTrieNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Utility class for Trie data structure operations.
 *
 * This class contains pure static methods for trie manipulation and debugging
 * that have been extracted from WebSocket.java for better organization and testability.
 *
 * These methods have ZERO dependencies on WebSocket instance state and are completely safe to extract.
 */
public class TrieUtilities {

    private static final Logger PRED = LoggerFactory.getLogger("PredictionLogger");

    /**
     * Builds trie from string patterns representing UI component sequences.
     *
     * ALGORITHM:
     * For each pattern [A, B, C]:
     * 1. Start at root
     * 2. For each element, create/navigate to child node
     * 3. Mark final node as pattern end
     * 4. Store complete pattern for retrieval
     *
     * EXAMPLE:
     * Input: [["PButton", "PLabel", "PCheckBox"],
     *         ["PButton", "PLabel", "PTextBox"],
     *         ["PButton", "PTextArea", "PSubmit"]]
     *
     * Creates trie:
     *           root
     *            |
     *         PButton
     *         /      \
     *     PLabel    PTextArea
     *     /    \        \
     * PCheckBox PTextBox PSubmit
     *   (end)    (end)    (end)
     *
     * EDGE CASES:
     * - Null/empty patterns: Skipped silently
     * - Duplicate patterns: Harmlessly overwrites (idempotent)
     * - Single element patterns: Allowed (though we focus on triplets)
     * - Patterns sharing prefixes: Properly share nodes
     *
     * TIME COMPLEXITY: O(n * k) where n = patterns, k = pattern length
     * SPACE COMPLEXITY: O(total unique nodes) ≤ O(n * k)
     *
     * @param patterns List of string sequences to add to trie
     * @param dictTrie The trie to build patterns into
     */
    public static void buildSemanticPatternTrieFromStrings(final List<List<String>> patterns,
                                                           final TrieNode dictTrie) {
        if (patterns == null || dictTrie == null) return;

        for (final List<String> pattern : patterns) {
            // Skip invalid patterns
            if (pattern == null || pattern.isEmpty()) {
                PRED.debug("Skipping null/empty pattern");
                continue;
            }

            TrieNode currentNode = dictTrie;

            // Build path for this pattern
            for (final String element : pattern) {
                if (element == null) {
                    PRED.warn("Skipping pattern with null element: {}", pattern);
                    break; // Don't add incomplete patterns
                }

                // computeIfAbsent: atomic get-or-create operation
                currentNode = currentNode.stringChildren.computeIfAbsent(
                    element, k -> new TrieNode());
            }

            // Mark pattern end and store complete pattern
            currentNode.isEndOfPattern = true;
            currentNode.completeStringPattern = new ArrayList<>(pattern); // Defensive copy
            currentNode.patternFrequency++; // Track usage for adaptive learning

            PRED.debug("Added string pattern to trie: {} (frequency: {})",
                      pattern, currentNode.patternFrequency);
        }
    }

    /**
     * Recursively dumps trie structure for debugging purposes.
     *
     * This is a pure debugging utility with no side effects on trie structure.
     *
     * @param node Current trie node to dump
     * @param prefix Indentation prefix for pretty printing
     */
    public static void dumpTrie(final TrieNode node, final String prefix) {
        if (node == null) {
            PRED.info("TRIE DEBUG: Node is null at prefix: '{}'", prefix);
            return;
        }

        // Debug root node
        if (prefix.isEmpty()) {
            PRED.info("TRIE DEBUG: Starting trie dump from root");
            PRED.info("TRIE DEBUG: Root has {} string children, {} modelValuePair children",
                     node.stringChildren.size(), node.modelValuePairChildren.size());
        }

        // Print if this is a complete pattern
        if (node.isEndOfPattern) {
            String frequencyInfo = node.patternFrequency > 1 ?
                String.format(" (frequency: %d)", node.patternFrequency) : "";
            PRED.info("{}[PATTERN END]{} - Complete String Pattern: {}",
                     prefix, frequencyInfo, node.completeStringPattern);
        }

        // Dump string children
        if (!node.stringChildren.isEmpty()) {
            PRED.info("{}String children ({}): {}", prefix, node.stringChildren.size(),
                     node.stringChildren.keySet());
            for (Map.Entry<String, TrieNode> entry : node.stringChildren.entrySet()) {
                PRED.info("{}  └─ String: '{}'", prefix, entry.getKey());
                dumpTrie(entry.getValue(), prefix + "    ");
            }
        }

        // Dump ModelValuePair children
        if (!node.modelValuePairChildren.isEmpty()) {
            PRED.info("{}ModelValuePair children ({}): {}", prefix, node.modelValuePairChildren.size(),
                     node.modelValuePairChildren.keySet());
            for (Map.Entry<String, TrieNode> entry : node.modelValuePairChildren.entrySet()) {
                PRED.info("{}  └─ ModelValuePair: '{}'", prefix, entry.getKey());
                dumpTrie(entry.getValue(), prefix + "    ");
            }
        }

        // If leaf node with no patterns
        if (!node.isEndOfPattern && node.stringChildren.isEmpty() && node.modelValuePairChildren.isEmpty()) {
            PRED.info("{}[LEAF] - No pattern stored", prefix);
        }
    }

    /**
     * Dump widget interaction trie for debugging.
     *
     * This method provides debugging visibility into widget interaction sequences
     * learned by the trie system.
     *
     * @param node Current widget trie node to dump
     * @param prefix Indentation prefix for pretty printing
     * @param path Current widget sequence path
     */
    public static void dumpWidgetTrie(final WidgetTrieNode node, final String prefix, final String path) {
        if (node == null) return;

        if (node.isEndOfSequence && node.completeWidgetSequence != null) {
            PRED.info("Widget Sequence: {} [COMPLETE] (frequency: {})",
                     path, node.sequenceFrequency);
            if (node.completeMessagePattern != null) {
                PRED.info("  → Message Pattern: {}", node.completeMessagePattern);
            }
        }

        for (Map.Entry<String, WidgetTrieNode> entry : node.children.entrySet()) {
            String childPath = path.isEmpty() ? entry.getKey() : path + " → " + entry.getKey();
            dumpWidgetTrie(entry.getValue(), prefix + "  ", childPath);
        }
    }
}