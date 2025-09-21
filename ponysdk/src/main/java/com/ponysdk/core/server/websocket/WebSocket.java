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

import com.ponysdk.core.model.ClientToServerModel;
import com.ponysdk.core.model.ServerToClientModel;
import com.ponysdk.core.model.ValueTypeModel;
import com.ponysdk.core.model.WidgetType;
import com.ponysdk.core.server.application.ApplicationConfiguration;
import com.ponysdk.core.server.application.ApplicationManager;
import com.ponysdk.core.server.application.UIContext;
import com.ponysdk.core.server.context.CommunicationSanityChecker;
import com.ponysdk.core.server.stm.TxnContext;
import com.ponysdk.core.ui.basic.PObject;
import org.eclipse.jetty.util.component.Container;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.common.extensions.ExtensionStack;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
//import com.ponysdk.core.server.PScheduler;

public class WebSocket implements WebSocketListener, WebsocketEncoder {

    private static final String MSG_RECEIVED = "Message received from terminal : UIContext #{} on {} : {}";
    private static final Logger log = LoggerFactory.getLogger(WebSocket.class);
    private static final Logger loggerIn = LoggerFactory.getLogger("WebSocket-IN");
    private static final Logger loggerOut = LoggerFactory.getLogger("WebSocket-OUT");
    // A dedicated logger to trace the semantic pattern matching and prediction flow.
    private static final Logger PRED = LoggerFactory.getLogger("PredictionLogger");

    private ServletUpgradeRequest request;
    private WebsocketMonitor monitor;
    private WebSocketPusher websocketPusher;
    private ApplicationManager applicationManager;

    private TxnContext context;
    private Session session;
    private UIContext uiContext;
    private Listener listener;

    private long lastSentPing;
    
    // Dictionary compression settings (enabled by default)
    private final ModelValueDictionary dictionary = new ModelValueDictionary(2);
    private final List<ModelValuePair> currentBatch = new ArrayList<>();
    
    // Track pattern IDs for sequence learning (groups of 3)
    private final List<Integer> patternSequence = new ArrayList<>(3);

    // -- Start of Semantic Pattern Matching for Prediction --

    // A buffer to hold incoming instructions while we check for a matching triplet pattern.
    private final List<String> currentPatternBuffer = new ArrayList<>();
    // Holds the last prediction received from the FastAPI service for comparison.
    private String lastPrediction = null;
    // A lock to ensure thread-safe access to the prediction buffer.
    private final Object predictionLock = new Object();
    private final List<List<String>> accumulatedPatterns = Collections.synchronizedList(new ArrayList<>());
    
    // Widget Interaction Sequence Tracking for Trie Prediction
    // ========================================================
    private final List<String> widgetInteractionSequence = new ArrayList<>(); // Sequence of widget keys: ["PButton#11", "PLabel#22", "PCheckBox#33"]
    private final Map<String, List<ModelValuePair>> widgetMessagePatterns = new HashMap<>(); // Complete message patterns per widget
    private final Map<Integer, String> widgetTypeById = new HashMap<>(); // Widget ID to type mapping
    private String currentWidgetKey = null; // Current widget being processed: "PButton#11"
    private String currentWidgetType = null; // Current widget type: "PButton"
    private Integer currentWidgetId = null; // Current widget ID: 11
    private final List<ModelValuePair> currentWidgetMessages = new ArrayList<>(); // Messages for current widget
    private String lastPredictedWidget = null; // Last widget we predicted for validation
    
    // Thread safety for widget interaction tracking
    private final Object widgetSequenceLock = new Object();


    /**
     * WidgetInteractionTrieNode - Enhanced trie node for widget interaction sequence prediction
     * 
     * ARCHITECTURE CHANGE:
     * - OLD: Stored individual ModelValuePair patterns like [TYPE_UPDATE=11, END_OF_PROCESSING=null]
     * - NEW: Stores widget interaction sequences like ["PButton#11", "PLabel#22", "PCheckBox#33"]
     *        + Complete message patterns for each widget
     * 
     * WHAT THIS STORES:
     * - Widget interaction sequences (keys in trie paths)
     * - Complete message patterns for each widget interaction (values at nodes)
     * - Prediction data for sequence completion
     */
    public static final class WidgetTrieNode {
        // Children map widget keys to next nodes in sequence
        final Map<String, WidgetTrieNode> children = new HashMap<>();
        
        // Terminal node marker
        boolean isEndOfSequence = false;
        
        // Complete message pattern for this widget (what to send when predicting)
        List<ModelValuePair> completeMessagePattern = null;
        
        // Widget interaction sequence that led to this node
        List<String> completeWidgetSequence = null;
        
        // Frequency tracking for learning
        int sequenceFrequency = 0;
    }
    
    // Root of widget interaction trie
    private static final WidgetTrieNode WIDGET_TRIE = new WidgetTrieNode();
    
    /**
     * TrieNode - Fundamental building block of our pattern prediction trie
     * 
     * WHAT IS A TRIE?
     * A trie (prefix tree) is a tree where each node represents a single element in a sequence.
     * Paths from root to leaf represent complete sequences. This enables O(k) operations where
     * k is the sequence length, regardless of how many patterns are stored.
     * 
     * WHY TRIE FOR THIS USE CASE?
     * 1. Prefix Matching: O(k) to check if current UI actions match start of any known pattern
     * 2. Pattern Completion: Given prefix "PButton-&gt;PLabel", instantly find all possible completions
     * 3. Space Efficiency: Common prefixes share nodes (e.g., many patterns starting with "PButton")
     * 4. Dynamic Learning: Add new patterns without restructuring existing data
     * 
     * DESIGN DECISIONS:
     * - Separate maps for String vs ModelValuePair: Prevents type confusion and key collisions
     * - HashMap over TreeMap: We need O(1) child access, not sorted order
     * - No data in nodes: We only care about structure for pattern matching
     * - Store complete patterns at terminals: Enables pattern retrieval for analysis
     * 
     * EXAMPLE STRUCTURE:
     * If we have patterns [PButton-&gt;PLabel-&gt;PCheckBox] and [PButton-&gt;PLabel-&gt;PTextBox]:
     * 
     *              root
     *               |
     *            PButton
     *               |
     *             PLabel
     *            /      \
     *      PCheckBox   PTextBox
     *         (end)      (end)
     */
    public static final class TrieNode {
        // Children nodes - separate maps prevent key collision between types
        final Map<String, TrieNode> stringChildren = new HashMap<>();
        final Map<String, TrieNode> modelValuePairChildren = new HashMap<>();
        
        // Marks terminal nodes (complete patterns)
        boolean isEndOfPattern = false;
        
        // Store complete patterns at terminals for retrieval/analysis
        List<String> completeStringPattern = null;
        List<ModelValuePair> completeModelValuePattern = null;
        
        // Pattern frequency for adaptive learning (optional enhancement)
        int patternFrequency = 0;
    }

    // The root of our Trie, holding all known semantic patterns.
    private static final TrieNode DICT_TRIE = new TrieNode();

    // Statically initializes the Trie with known UI interaction patterns.
    static {
        // These patterns represent common sequences of UI actions we want to predict.
        List<List<String>> initialPatterns = new ArrayList<>();
        // Example: A user often clicks a button, which updates a label, then checks a box.
        initialPatterns.add(Arrays.asList("PButton", "PLabel", "PCheckBox"));
        // Example: A user interacts with a series of form-like elements.
        initialPatterns.add(Arrays.asList("PCheckBox", "PRadioButton", "PTextBox"));
        // Example: A user opens a panel, sees text, and clicks a button inside it.
        initialPatterns.add(Arrays.asList("PFlowPanel", "PLabel", "PButton"));
        initialPatterns.add(Arrays.asList("PWindow", "PSimplePanel", "PLabel"));
        initialPatterns.add(Arrays.asList("PListBox", "PListBox", "PListBox"));
        initialPatterns.add(Arrays.asList("PDateBox", "PDatePicker", "PButton"));
        initialPatterns.add(Arrays.asList("PTree", "PTreeItem", "PTreeItem"));
        initialPatterns.add(Arrays.asList("PScript", "PLabel", "PScript"));
        initialPatterns.add(Arrays.asList("PFileUpload", "PButton", "PLabel"));

        TrieUtilities.buildSemanticPatternTrieFromStrings(initialPatterns, DICT_TRIE);
        // Log the constructed Trie patterns at startup for easy verification.
        PRED.info("=== STARTUP TRIE SYSTEM INITIALIZED ===");
        PRED.info("Legacy String Trie: {} initial patterns loaded", initialPatterns.size());
        PRED.info("Widget Interaction Trie: Ready for learning");
        PRED.info("Dictionary Compression: Enabled (threshold=2)");
        PRED.info("Dumping initial string patterns:");
        TrieUtilities.dumpTrie(DICT_TRIE, "");
        PRED.info("Widget Interaction Trie: (empty - will learn from interactions)");
        TrieUtilities.dumpWidgetTrie(WIDGET_TRIE, "", "");
        PRED.info("=== END STARTUP TRIE SYSTEM DUMP ===");
    }


    /**
     * Legacy method for backward compatibility.
     * Delegates to buildSemanticPatternTrieFromStrings.
     * 
     * @param patterns String patterns to add to trie
     */
    static void buildSemanticPatternTrie(final List<List<String>> patterns) {
        TrieUtilities.buildSemanticPatternTrieFromStrings(patterns, DICT_TRIE);
    }

    /**
     * Builds trie from ModelValuePair patterns for dictionary-based prediction.
     * 
     * ALGORITHM:
     * 1. Validate each pattern (must be exactly 3 elements)
     * 2. Convert each ModelValuePair to string key
     * 3. Build trie path using these keys
     * 4. Mark terminal and store pattern
     * 
     * EXAMPLE:
     * Input: [[ModelValuePair(TYPE_CREATE, 1),
     *          ModelValuePair(WIDGET_TYPE, "PButton"),
     *          ModelValuePair(WIDGET_ID, 123)],
     *         [ModelValuePair(TYPE_CREATE, 1),
     *          ModelValuePair(WIDGET_TYPE, "PButton"),
     *          ModelValuePair(WIDGET_ID, 456)]]
     * 
     * Creates trie with keys:
     *                  root
     *                   |
     *            [TYPE_CREATE:1]
     *                   |
     *           [WIDGET_TYPE:PButton]
     *               /         \
     *     [WIDGET_ID:123]  [WIDGET_ID:456]
     *          (end)           (end)
     * 
     * INTEGRATION WITH DICTIONARY:
     * - Dictionary detects frequent patterns
     * - Those patterns are added here for prediction
     * - Future similar sequences can be predicted/optimized
     * 
     * EDGE CASES:
     * - Non-triplet patterns: Skipped (we only handle size 3)
     * - Null patterns/pairs: Validated and skipped
     * - Key generation failures: Logged and pattern skipped
     * 
     * @param patterns List of ModelValuePair triplets to add
     */
    static void buildSemanticPatternTrieFromPairs(final List<List<ModelValuePair>> patterns) {
        if (patterns == null) return;
        
        for (final List<ModelValuePair> pattern : patterns) {
            // Strict validation - only triplets
            if (pattern == null || pattern.size() != 3) {
                if (pattern != null && pattern.size() > 0) {
                    PRED.debug("Skipping non-triplet pattern of size: {}", pattern.size());
                }
                continue;
            }
            
            // Validate all pairs are non-null
            boolean hasNullPair = false;
            for (ModelValuePair pair : pattern) {
                if (pair == null) {
                    hasNullPair = true;
                    break;
                }
            }
            if (hasNullPair) {
                PRED.warn("Skipping pattern with null ModelValuePair");
                continue;
            }
            
            TrieNode currentNode = DICT_TRIE;
            
            // Build trie path
            try {
                for (final ModelValuePair pair : pattern) {
                    final String key = generateTrieKey(pair);
                    currentNode = currentNode.modelValuePairChildren.computeIfAbsent(
                        key, k -> new TrieNode());
                }
                
                // Mark terminal and store pattern
                currentNode.isEndOfPattern = true;
                currentNode.completeModelValuePattern = new ArrayList<>(pattern); // Defensive copy
                currentNode.patternFrequency++;
                
                PRED.debug("Added ModelValuePair pattern to trie: [{}] (frequency: {})", 
                          pattern.stream()
                                 .map(WebSocket::generateTrieKey)
                                 .collect(Collectors.joining(" -> ")),
                          currentNode.patternFrequency);
                
            } catch (Exception e) {
                PRED.error("Failed to add pattern to trie: {}", pattern, e);
            }
        }
    }

    /**
     * Generates deterministic string key for ModelValuePair trie navigation.
     * 
     * ALGORITHM:
     * 1. Extract model enum and value from pair
     * 2. Create key as "MODEL_NAME:value_string"
     * 3. Handle null values explicitly as "null" string
     * 
     * EXAMPLE:
     * - Input: ModelValuePair(WIDGET_TYPE, "PButton") -> Output: "WIDGET_TYPE:PButton"
     * - Input: ModelValuePair(WIDGET_ID, 12345) -> Output: "WIDGET_ID:12345"
     * - Input: ModelValuePair(TEXT, null) -> Output: "TEXT:null"
     * 
     * EDGE CASES HANDLED:
     * - Null values: Converted to "null" string to avoid NPE
     * - Special characters: Relies on toString() which handles most cases
     * - Empty strings: Preserved as-is ("TEXT:" for empty string value)
     * 
     * OPTIMIZATION:
     * - StringBuilder pre-sized based on typical key length
     * - Single pass construction, no string concatenation
     * - Could cache results for frequently used pairs (future enhancement)
     * 
     * @param pair The ModelValuePair to convert to a trie key
     * @return Deterministic string key for trie navigation
     */
    private static String generateTrieKey(final ModelValuePair pair) {
        if (pair == null) {
            throw new IllegalArgumentException("Cannot generate key for null ModelValuePair");
        }
        
        final ServerToClientModel model = pair.getModel();
        final Object value = pair.getValue();
        
        // Pre-size StringBuilder for efficiency (model name + ":" + typical value)
        final StringBuilder keyBuilder = new StringBuilder(model.name().length() + 20);
        
        // Use model name for human readability in logs
        keyBuilder.append(model.name()).append(':');
        
        // Handle null values explicitly
        if (value == null) {
            keyBuilder.append("null");
        } else if (value instanceof String && ((String) value).isEmpty()) {
            // Preserve empty strings (don't convert to "null")
            // Key will end with ':' which is fine
        } else {
            keyBuilder.append(value.toString());
        }
        
        return keyBuilder.toString();
    }

    /**
     * Checks if string sequence is a prefix of any known pattern.
     * 
     * ALGORITHM:
     * 1. Handle empty prefix (always true)
     * 2. Navigate trie following prefix elements
     * 3. Return false if path breaks, true if complete
     * 
     * EXAMPLE 1 (Valid Prefix):
     * Known pattern: ["PButton", "PLabel", "PCheckBox"]
     * Input: ["PButton", "PLabel"]
     * Process:
     *   - Start at root
     *   - Navigate to root->PButton (exists)
     *   - Navigate to PButton->PLabel (exists)
     *   - Return true (valid prefix)
     * 
     * EXAMPLE 2 (Invalid Prefix):
     * Known patterns: Same as above
     * Input: ["PButton", "PTextArea"]
     * Process:
     *   - Start at root
     *   - Navigate to root->PButton (exists)
     *   - Navigate to PButton->PTextArea (doesn't exist)
     *   - Return false (invalid prefix)
     * 
     * USE CASE: Decide whether to buffer more elements or send immediately
     * 
     * EDGE CASES:
     * - Null input: Treated as empty (returns true)
     * - Empty list: Returns true (prefix of everything)
     * - Null elements: Returns false at that point
     * - Prefix longer than any pattern: Will return false
     * 
     * TIME COMPLEXITY: O(k) where k = prefix length
     * SPACE COMPLEXITY: O(1) - no additional space
     * 
     * @param prefix Sequence to check
     * @return true if prefix of any known pattern
     */
    boolean isPrefixOfKnownTriplet(final List<String> prefix) {
        final long startNanos = System.nanoTime();
        boolean result = false;
        
        try {
            // Empty prefix matches all patterns
            if (prefix == null || prefix.isEmpty()) {
                result = true;
                return result;
            }
        
        TrieNode currentNode = DICT_TRIE;
        
        // Navigate trie following prefix
        for (final String element : prefix) {
            // Handle null elements
            if (element == null) {
                PRED.debug("Null element in prefix at position {}", prefix.indexOf(element));
                return false;
            }
            
            // Try to navigate to child
            currentNode = currentNode.stringChildren.get(element);
            if (currentNode == null) {
                // Path doesn't exist
                result = false;
                return result;
            }
        }
        
        // Successfully navigated entire prefix
        result = true;
        return result;
        
        } finally {
            // Measure trie query latency
            final long latencyNanos = System.nanoTime() - startNanos;
            if (listener instanceof LatencyTracker) {
                ((LatencyTracker) listener).onTrieQuery("prefix_lookup", result, latencyNanos);
            }
        }
    }

    /**
     * Checks if ModelValuePair sequence is a prefix of any known pattern.
     * 
     * ALGORITHM: Same as string version but with key generation step
     * 
     * EXAMPLE:
     * Known pattern: [TYPE_CREATE:1 -> WIDGET_TYPE:PButton -> WIDGET_ID:123]
     * Input: [ModelValuePair(TYPE_CREATE, 1), ModelValuePair(WIDGET_TYPE, "PButton")]
     * Process:
     *   - Generate key "TYPE_CREATE:1", navigate (exists)
     *   - Generate key "WIDGET_TYPE:PButton", navigate (exists)  
     *   - Return true (valid prefix)
     * 
     * INTEGRATION: Used by dictionary system to predict if current batch
     * will match a known pattern, enabling proactive optimization.
     * 
     * EDGE CASES:
     * - Null pair in sequence: Returns false
     * - Key generation failure: Returns false
     * - Empty/null input: Returns true
     * 
     * @param prefix ModelValuePair sequence to check
     * @return true if prefix of any known pattern
     */
    boolean isPrefixOfKnownTripletFromPairs(final List<ModelValuePair> prefix) {
        // Empty prefix matches all patterns
        if (prefix == null || prefix.isEmpty()) {
            return true;
        }
        
        TrieNode currentNode = DICT_TRIE;
        
        // Navigate trie using generated keys
        for (final ModelValuePair pair : prefix) {
            // Validate pair
            if (pair == null) {
                PRED.debug("Null ModelValuePair in prefix");
                return false;
            }
            
            try {
                final String key = generateTrieKey(pair);
                currentNode = currentNode.modelValuePairChildren.get(key);
                
                if (currentNode == null) {
                    // Path doesn't exist
                    return false;
                }
            } catch (Exception e) {
                PRED.error("Failed to generate key for pair: {}", pair, e);
                return false;
            }
        }
        
        return true;
    }

    /**
     * Checks if a complete string triplet exists in the trie.
     * 
     * ALGORITHM:
     * 1. Validate exactly 3 elements
     * 2. Navigate complete path in trie
     * 3. Check if final node is marked as pattern end
     * 
     * EXAMPLE 1 (Known Pattern):
     * Trie contains: ["PButton", "PLabel", "PCheckBox"]
     * Input: ["PButton", "PLabel", "PCheckBox"]
     * Result: true (exact match found)
     * 
     * EXAMPLE 2 (Unknown Pattern):
     * Trie contains: ["PButton", "PLabel", "PCheckBox"]
     * Input: ["PButton", "PLabel", "PTextBox"]
     * Result: false (path exists but not marked as complete pattern)
     * 
     * EXAMPLE 3 (Partial Path):
     * Trie contains: ["PButton", "PLabel", "PCheckBox"]
     * Input: ["PButton", "PLabel", "PNonExistent"]
     * Result: false (path breaks at third element)
     * 
     * USE CASE: When receiving third element, check if pattern is known
     * to decide whether to learn it as new pattern.
     * 
     * EDGE CASES:
     * - Null/empty input: Returns false
     * - Non-triplet size: Returns false immediately
     * - Null elements: Returns false when encountered
     * - Path exists but not terminal: Returns false
     * 
     * TIME COMPLEXITY: O(3) = O(1) constant for triplets
     * 
     * @param seq String sequence to check (must be size 3)
     * @return true if exact triplet exists in trie
     */
    boolean isKnownTriplet(final List<String> seq) {
        final long startNanos = System.nanoTime();
        boolean result = false;
        
        try {
            // Input validation
            if (seq == null || seq.size() != 3) {
                PRED.debug("Invalid triplet size: {}", seq == null ? "null" : seq.size());
                result = false;
                return result;
            }
        
            // Check for null elements
            for (int i = 0; i < 3; i++) {
                if (seq.get(i) == null) {
                    PRED.debug("Null element at position {} in triplet", i);
                    result = false;
                    return result;
                }
            }
            
            PRED.debug("Checking if triplet is known: {}", seq);
            TrieNode currentNode = DICT_TRIE;
            
            // Navigate the complete triplet path
            for (final String element : seq) {
                currentNode = currentNode.stringChildren.get(element);
                if (currentNode == null) {
                    PRED.debug("Path broken at element: {}", element);
                    result = false;
                    return result;
                }
            }
            
            // Check if this node represents a complete pattern
            final boolean isKnown = currentNode.isEndOfPattern;
            PRED.debug("Triplet {} {}", seq, isKnown ? "EXISTS" : "NOT FOUND");
            
            result = isKnown;
            return result;
            
        } finally {
            // Measure trie triplet lookup latency
            final long latencyNanos = System.nanoTime() - startNanos;
            if (listener instanceof LatencyTracker) {
                ((LatencyTracker) listener).onTrieQuery("triplet_lookup", result, latencyNanos);
            }
        }
    }

    /**
     * Checks if a complete ModelValuePair triplet exists in the trie.
     * 
     * ALGORITHM: Same as string version but with key generation
     * 
     * EXAMPLE:
     * Trie contains pattern from dictionary ID #5:
     * [TYPE_CREATE:1 -> WIDGET_TYPE:PButton -> WIDGET_ID:123]
     * 
     * Input: [ModelValuePair(TYPE_CREATE, 1),
     *         ModelValuePair(WIDGET_TYPE, "PButton"),
     *         ModelValuePair(WIDGET_ID, 123)]
     * Result: true (pattern exists)
     * 
     * DICTIONARY INTEGRATION:
     * - Dictionary records frequent patterns with IDs
     * - Those patterns are added to trie
     * - This method checks if new pattern already exists
     * - Prevents duplicate entries in prediction system
     * 
     * EDGE CASES:
     * - Null pairs: Returns false
     * - Key generation errors: Caught and returns false
     * - Wrong size: Returns false immediately
     * 
     * @param seq ModelValuePair triplet to check
     * @return true if exact triplet exists in trie
     */
    boolean isKnownTripletFromPairs(final List<ModelValuePair> seq) {
        // Size validation
        if (seq == null || seq.size() != 3) {
            PRED.debug("Invalid ModelValuePair triplet size: {}", 
                      seq == null ? "null" : seq.size());
            return false;
        }
        
        // Null element validation
        for (int i = 0; i < 3; i++) {
            if (seq.get(i) == null) {
                PRED.debug("Null ModelValuePair at position {}", i);
                return false;
            }
        }
        
        PRED.debug("Checking if ModelValuePair triplet is known");
        TrieNode currentNode = DICT_TRIE;
        
        // Navigate using generated keys
        for (final ModelValuePair pair : seq) {
            try {
                final String key = generateTrieKey(pair);
                currentNode = currentNode.modelValuePairChildren.get(key);
                
                if (currentNode == null) {
                    PRED.debug("Path broken at key: {}", key);
                    return false;
                }
            } catch (Exception e) {
                PRED.error("Key generation failed for pair: {}", pair, e);
                return false;
            }
        }
        
        return currentNode.isEndOfPattern;
    }


    // -- End of Semantic Pattern Matching for Prediction --

    public WebSocket() {
    }

    @Override
    public void onWebSocketConnect(final Session session) {
        try {
            if (!session.isOpen()) throw new IllegalStateException("Session already closed");
            this.session = session;

            // 1K for max chunk size and 1M for total buffer size
            // Don't set max chunk size > 8K because when using Jetty Websocket compression, the chunks are limited to 8K

            this.websocketPusher = new WebSocketPusher(session, 1 << 20, 1 << 12, TimeUnit.SECONDS.toMillis(60));
            
            // Set the listener on websocketPusher if it was set early
            if (this.listener != null) {
                this.websocketPusher.setWebSocketListener(this.listener);
            }
            
            uiContext = new UIContext(this, context, applicationManager.getConfiguration(), request);
            log.info("Creating a new {}", uiContext);

            // Initialize Trie and CodeT5 features from ApplicationConfiguration
            final ApplicationConfiguration config = uiContext.getConfiguration();
            // WebSocket.WebSocketConfiguration.isTrieEnabled() = true; // Original hardcoded - kept for reference
            // WebSocket.WebSocketConfiguration.isCodeT5Enabled() = true; // Original hardcoded - kept for reference
            WebSocketConfiguration.setTrieEnabled(config.isTriePatternPredictionEnabled());
            WebSocketConfiguration.setCodeT5Enabled(config.isCodeT5SemanticAnalysisEnabled());
            log.info("Trie prediction {} | CodeT5 analysis {} for UIContext #{}",
                    WebSocketConfiguration.isTrieEnabled() ? "enabled" : "disabled",
                    WebSocketConfiguration.isCodeT5Enabled() ? "enabled" : "disabled",
                    uiContext.getID());

            final CommunicationSanityChecker communicationSanityChecker = new CommunicationSanityChecker(uiContext);
            context.registerUIContext(uiContext);
            java.util.List<java.util.List<String>> knownPatterns = new java.util.ArrayList<>();
            
            // Temporarily disable dictionary until initial UI setup is complete
            setDictionaryEnabled(false);

            uiContext.acquire();
            try {
                beginObject();
                final ApplicationConfiguration configuration = uiContext.getConfiguration();
                final boolean enableClientToServerHeartBeat = configuration.isEnableClientToServerHeartBeat();
                final TimeUnit heartBeatPeriodTimeUnit = configuration.getHeartBeatPeriodTimeUnit();
                final int heartBeatPeriod = enableClientToServerHeartBeat
                        ? (int) heartBeatPeriodTimeUnit.toSeconds(configuration.getHeartBeatPeriod())
                        : 0;

                encode(ServerToClientModel.CREATE_CONTEXT, uiContext.getID()); // TODO nciaravola integer ?
                encode(ServerToClientModel.OPTION_FORMFIELD_TABULATION, configuration.isTabindexOnlyFormField());
                encode(ServerToClientModel.HEARTBEAT_PERIOD, heartBeatPeriod);
                endObject();
                if (isAlive()) flush0();
            } catch (final Throwable e) {
                log.error("Cannot send initial setup to client", e);
            } finally {
                uiContext.release();
            }

            applicationManager.startApplication(uiContext);
            communicationSanityChecker.start();
            

            // Enable dictionary compression after UI is completely loaded and stable (15 seconds)
            // This delay ensures all widgets are correctly initialized before enabling compression
            new java.util.Timer(true).schedule(new java.util.TimerTask() {
                @Override
                public void run() {
                    if (isAlive()) {
                        uiContext.acquire();
                        try {
                            // setDictionaryEnabled(true); // Original hardcoded - kept for reference
                            setDictionaryEnabled(uiContext.getConfiguration().isDictionaryCompressionEnabled());
                            log.info("Dictionary compression {} for UIContext #{}",
                                    uiContext.getConfiguration().isDictionaryCompressionEnabled() ? "enabled" : "disabled",
                                    uiContext.getID());
                        } finally {
                            uiContext.release();
                        }
                    }
                }
            }, 2000); // 2 seconds delay - reduced for testing pattern learning
            /*
            // Schedule a periodic task to dump the Trie every 30 seconds for debugging.
            PScheduler.scheduleAtFixedRate(() -> {
                if (isAlive()) {
                    uiContext.acquire();
                    try {
                        PRED.info("Dumping registered prediction patterns (periodic check):");
                        TrieUtilities.dumpTrie(DICT_TRIE, "");
                    } finally {
                        uiContext.release();
                    }
                }
            }, Duration.ofSeconds(30)); // Dump every 30 seconds
            */
            
            // Schedule a periodic task to dump the Trie every 30 seconds for debugging.
            // Using java.util.Timer
            log.info("Setting up periodic trie dump timer for UIContext #{} (30s interval)", uiContext.getID());
            new java.util.Timer("TrieDumpTimer-UIContext" + uiContext.getID(), true).scheduleAtFixedRate(new java.util.TimerTask() {
                @Override
                public void run() {
                    if (isAlive()) {
                        uiContext.acquire();
                        try {
                            //for (int id : dictionary.getPatternIds()) {
                            log.info("=== PERIODIC TRIE DUMP STARTING (Timer-0 thread) ===");
                            PRED.info("DEBUG: Running periodic dictionary dump...");
                            Set<Integer> patternIds = dictionary.getPatternIds();
                            PRED.info("DEBUG: Found {} patterns in dictionary", patternIds.size());
                            
                            if (patternIds.isEmpty()) {
                                PRED.info("DEBUG: Dictionary is empty! Check if patterns are being recorded.");
                                PRED.info("DEBUG: Dictionary enabled: {}", WebSocketConfiguration.isDictionaryEnabled());
                                PRED.info("DEBUG: Current batch size: {}", currentBatch.size());
                            }
                            
                            for (int id : patternIds) {
                                List<ModelValuePair> pattern = dictionary.getPattern(id);
                                String seq = pattern.stream()
                                    .map(p -> p.getModel().name())
                                    .collect(Collectors.joining(" -> "));
                                PRED.info("Pattern #{}: {}", id, seq);
                            }
                            
                            // Always dump both tries regardless of dictionary contents
                            PRED.info("=== PERIODIC TRIE SYSTEM DUMP ===");
                            PRED.info("=== Legacy String Patterns ===");
                            TrieUtilities.dumpTrie(DICT_TRIE, "");
                            PRED.info("=== Widget Interaction Sequences ===");
                            TrieUtilities.dumpWidgetTrie(WIDGET_TRIE, "", "");
                            PRED.info("=== Widget Sequence Statistics ===");
                            synchronized (widgetSequenceLock) {
                                PRED.info("Current sequence length: {}", widgetInteractionSequence.size());
                                PRED.info("Widget patterns stored: {}", widgetMessagePatterns.size());
                                if (!widgetInteractionSequence.isEmpty()) {
                                    PRED.info("Recent interactions: {}", 
                                             widgetInteractionSequence.stream()
                                                 .skip(Math.max(0, widgetInteractionSequence.size() - 5))
                                                 .collect(Collectors.toList()));
                                }
                            }
                            PRED.info("=== END PERIODIC TRIE SYSTEM DUMP ===");
                        } finally {
                            uiContext.release();
                        }
                    }
                }
            }, 30000, 30000); // Delay and period in milliseconds

        } catch (final Exception e) {
            log.error("Error in onWebSocketConnect for UIContext #{}", uiContext != null ? uiContext.getID() : "null", e);
        }
    }

    

    @Override
    public void onWebSocketError(final Throwable throwable) {
        log.error("WebSocket Error on UIContext #{}", uiContext.getID(), throwable);
        uiContext.onDestroy();
    }

    @Override
    public void onWebSocketClose(final int statusCode, final String reason) {
        if (log.isInfoEnabled())
            log.info("WebSocket closed on UIContext #{} : {}, reason : {}", uiContext.getID(),
                    NiceStatusCode.getMessage(statusCode), Objects.requireNonNullElse(reason, ""));
        // Before the session is destroyed, send any instructions that were waiting in the buffer.
        flushBufferedInstructions();
        uiContext.onDestroy();
    }

    /**
     * Receive from the terminal
     */

    @Override
    public void onWebSocketText(final String message) {
        if (this.listener != null) listener.onIncomingText(message);
        if (isAlive()) {
            try {
                uiContext.onMessageReceived();
                if (monitor != null) monitor.onMessageReceived(WebSocket.this, message);

                final JsonObject jsonObject;
                try (final JsonReader reader = uiContext.getJsonProvider().createReader(new StringReader(message))) {
                    jsonObject = reader.readObject();
                }

                if (jsonObject.containsKey(ClientToServerModel.HEARTBEAT_REQUEST.toStringValue())) {
                    sendHeartbeat();
                } else if (jsonObject.containsKey(ClientToServerModel.TERMINAL_LATENCY.toStringValue())) {
                    processRoundtripLatency(jsonObject);
                } else if (jsonObject.containsKey(ClientToServerModel.APPLICATION_INSTRUCTIONS.toStringValue())) {
                    processInstructions(jsonObject);
                } else if (jsonObject.containsKey(ClientToServerModel.ERROR_MSG.toStringValue())) {
                    processTerminalLog(jsonObject, ClientToServerModel.ERROR_MSG);
                } else if (jsonObject.containsKey(ClientToServerModel.WARN_MSG.toStringValue())) {
                    processTerminalLog(jsonObject, ClientToServerModel.WARN_MSG);
                } else if (jsonObject.containsKey(ClientToServerModel.INFO_MSG.toStringValue())) {
                    processTerminalLog(jsonObject, ClientToServerModel.INFO_MSG);
                } else if (jsonObject.containsKey(ClientToServerModel.DICTIONARY_REQUEST.toStringValue())) {
                    // Handle dictionary pattern request
                    final int patternId = jsonObject.getJsonNumber(ClientToServerModel.DICTIONARY_REQUEST.toStringValue()).intValue();
                    handleDictionaryRequest(patternId);
                } else {
                    log.error("Unknown message from terminal #{} : {}", uiContext.getID(), message);
                }

                if (monitor != null) monitor.onMessageProcessed(this, message);
            } catch (final Throwable e) {
                log.error("Cannot process message from terminal  #{} : {}", uiContext.getID(), message, e);
            } finally {
                if (monitor != null) monitor.onMessageUnprocessed(this, message);
            }
        } else {
            log.info("UI Context #{} is destroyed, message dropped from terminal : {}", uiContext != null ? uiContext.getID() : -1,
                    message);
        }
    }

    private void processRoundtripLatency(final JsonObject jsonObject) {
        final long roundtripLatency = TimeUnit.MILLISECONDS.convert(System.nanoTime() - lastSentPing, TimeUnit.NANOSECONDS);
        log.trace("Roundtrip measurement : {} ms from terminal #{}", roundtripLatency, uiContext.getID());
        uiContext.addRoundtripLatencyValue(roundtripLatency);

        final long terminalLatency = jsonObject.getJsonNumber(ClientToServerModel.TERMINAL_LATENCY.toStringValue()).longValue();
        log.trace("Terminal measurement : {} ms from terminal #{}", terminalLatency, uiContext.getID());
        uiContext.addTerminalLatencyValue(terminalLatency);

        final long networkLatency = roundtripLatency - terminalLatency;
        log.trace("Network measurement : {} ms from terminal #{}", networkLatency, uiContext.getID());
        uiContext.addNetworkLatencyValue(networkLatency);
    }

    private void processInstructions(final JsonObject jsonObject) {
        System.out.println("DEBUG: processInstructions entered.");
        final String applicationInstructions = ClientToServerModel.APPLICATION_INSTRUCTIONS.toStringValue();
        loggerIn.trace("UIContext #{} : {}", this.uiContext.getID(), jsonObject);
        
        // Handle dictionary request directly and bypass normal processing
        if (jsonObject.containsKey(ClientToServerModel.DICTIONARY_REQUEST.toStringValue())) {
            final int patternId = jsonObject.getJsonNumber(ClientToServerModel.DICTIONARY_REQUEST.toStringValue()).intValue();
            handleDictionaryRequest(patternId);
            return;
        }
        
        // Handle dictionary enabled/disabled status
        if (jsonObject.containsKey("X")) { // "X" is the value for DICTIONARY_ENABLED
            final boolean enabled = jsonObject.getBoolean("X");
            setDictionaryEnabled(enabled);
            log.info("Client requested dictionary mode: {}", enabled ? "enabled" : "disabled");
            return;
        }
        
        uiContext.execute(() -> {
            final JsonArray appInstructions = jsonObject.getJsonArray(applicationInstructions);
            for (int i = 0; i < appInstructions.size(); i++) {
                final JsonObject currentInstructionJson = appInstructions.getJsonObject(i);
                final String fullInstructionString = currentInstructionJson.toString();

                // Log every instruction received
                //loggerIn.info("Received Instruction: {}", fullInstructionString);

                // Extract the component type to check against our semantic patterns.
                final String componentType = extractComponentType(fullInstructionString);

                // If a component type can be found, process it for pattern matching.
                if (componentType != null) {
                    processInstructionForPrediction(componentType, fullInstructionString);
                } else {
                    // If no type is found, it's an unknown format; flush buffer and send it alone.
                    log.warn("Unknown instruction format, cannot extract component type: {}. Sending individually.", fullInstructionString);
                    flushBufferedInstructions();

                    sendJsonPostRequestUsingHttpURLConnection(Arrays.asList(fullInstructionString));
                }

                // Forward the original instruction to the UIContext for normal processing.
                uiContext.fireClientData(currentInstructionJson);
            }
        });
    }

    private void processTerminalLog(final JsonObject json, final ClientToServerModel level) {
        final String message = json.getJsonString(level.toStringValue()).getString();
        String objectInformation = "";

        if (json.containsKey(ClientToServerModel.OBJECT_ID.toStringValue())) {
            final PObject object = uiContext.getObject(json.getJsonNumber(ClientToServerModel.OBJECT_ID.toStringValue()).intValue());
            objectInformation = object == null ? "NA" : object.toString();
        }

        switch (level) {
            case INFO_MSG:
                if (log.isInfoEnabled())
                    log.info(MSG_RECEIVED, uiContext.getID(), objectInformation, message); 
                break;
            case WARN_MSG:
                if (log.isWarnEnabled())
                    log.warn(MSG_RECEIVED, uiContext.getID(), objectInformation, message);
                break;
            case ERROR_MSG:
                if (log.isErrorEnabled())
                    log.error(MSG_RECEIVED, uiContext.getID(), objectInformation, message);
                break;
            default:
                log.error("Unknown log level during terminal log processing : {}", level);
        }
    }

    /**
     * Receive from the terminal
     */
    @Override
    public void onWebSocketBinary(final byte[] payload, final int offset, final int len) {
        // Can't receive binary data from terminal (GWT limitation)
    }

    /**
     * Send round trip to the client
     */
    public void sendRoundTrip() {
        if (isAlive() && isSessionOpen()) {
            lastSentPing = System.nanoTime();
            beginObject();
            encode(ServerToClientModel.ROUNDTRIP_LATENCY, null);
            endObject();
            flush0();
        }
    }

    private void sendHeartbeat() {
        if (!isAlive() || !isSessionOpen()) return;
        uiContext.acquire();
        try {
            beginObject();
            encode(ServerToClientModel.HEARTBEAT, null);
            endObject();
            flush0();
        } finally {
            uiContext.release();
        }
    }

    public void flush() {
        if (isAlive() && isSessionOpen()) flush0();
    }

    void flush0() {
        try {
            websocketPusher.flush();
        } catch (final IOException e) {
            log.error("Can't write on the websocket for #{}, so we destroy the application", uiContext.getID(), e);
            uiContext.onDestroy();
        }
    }

    public void close() {
        if (isSessionOpen()) {
            final UIContext context = this.uiContext;
            log.info("Closing websocket programmatically for UIContext #{}", context == null ? null : context.getID());
            session.close();
        }
    }

    public void disconnect() {
        if (isSessionOpen()) {
            final UIContext context = this.uiContext;
            log.info("Disconnecting websocket programmatically for UIContext #{}", context == null ? null : context.getID());
            try {
                session.disconnect();
            } catch (final IOException e) {
                log.error("Unable to disconnect session for UIContext #{}", context == null ? null : context.getID(), e);
            }
        }
    }

    private boolean isAlive() {
        return uiContext != null && uiContext.isAlive();
    }

    private boolean isSessionOpen() {
        return session != null && session.isOpen();
    }

    @Override
    public void beginObject() {
        // Nothing to do
    }

    @Override
    public void endObject() {
        encode(ServerToClientModel.END, null);
    }

    @Override
    public void encode(final ServerToClientModel model, final Object value) {
        Integer ref = null; // Move ref declaration here
        PRED.info("Model S2C {} {}", model, value);
        
        // Stage 1: Intercept message for latency tracking
        if (listener instanceof LatencyTracker) {
            ((LatencyTracker)listener).onInterceptMessage(model.name(), value);
        }
        if (UIContext.get() == null) {
            log.warn("encode in websocket without current ui context acquired", new Exception());
            uiContext.acquire();
            try {
                PRED.info("Model S2C {} {}", model, value);
                encode(model, value);
            } finally {
                uiContext.release();
            }
            return;
        }

        // For END frames, flush any pending batch first
        if (WebSocketConfiguration.isDictionaryEnabled() && !currentBatch.isEmpty()) {
            if (model == ServerToClientModel.END_OF_PROCESSING && currentBatch.size() == 2) {
                PRED.info("Flushing batch with END_OF_PROCESSING");
            }   
            flushCurrentBatch();
            
            // FIX: Prevent double END encoding - dictionary already sent END
            if (model == ServerToClientModel.END) {
                return;
            }
        }

        // Skip dictionary for critical protocol frames
        if (!WebSocketConfiguration.isDictionaryEnabled() || isControlFrame(model)) {
            try {
                if (loggerOut.isTraceEnabled())
                    loggerOut.trace("UIContext #{} : {} {}", this.uiContext.getID(), model, value);
                // Stage 4: Track encoding
                if (listener instanceof LatencyTracker) {
                    ((LatencyTracker)listener).onEncode(model, value);
                }
                websocketPusher.encode(model, value);
                if (listener != null) listener.onOutgoingPonyFrame(model, value);
            } catch (final IOException e) {
                log.error("Can't write on the websocket for UIContext #{}, so we destroy the application", uiContext.getID(), e);
                uiContext.destroy();
            }
            return;
        }

        // Process with dictionary for application frames
        try {
            final ModelValuePair pair = new ModelValuePair(model, value);
            
            // Track widget interaction data BEFORE dictionary processing
            trackWidgetInteractionData(model, value);
            
            // Complete widget interaction on END_OF_PROCESSING
            if (model == ServerToClientModel.END_OF_PROCESSING) {
                completeCurrentWidgetInteraction();
            }
            
            // Skip dictionary specific frames like DICTIONARY_*
            if (model == ServerToClientModel.DICTIONARY_PATTERN_START ||
                model == ServerToClientModel.DICTIONARY_PATTERN_END ||
                model == ServerToClientModel.DICTIONARY_REFERENCE) {
                
                // Stage 4: Track encoding (alternative path)
                if (listener instanceof LatencyTracker) {
                    ((LatencyTracker)listener).onEncode(model, value);
                }
                websocketPusher.encode(model, value);
                if (listener != null) listener.onOutgoingPonyFrame(model, value);
                return;
            }
            
            // Process TYPE commands specially (start new dictionary contexts for this frame)
            if (isTypeCommand(model)) {
                // Flush any pending batches to ensure clean command sequence
                if (!currentBatch.isEmpty()) {
                    flushCurrentBatch();
                }
                
                // Check if this single TYPE command exists in dictionary
                List<ModelValuePair> singlePattern = Collections.singletonList(pair);
                PRED.debug("SINGLE TYPE: Looking for single TYPE command pattern in dictionary: {}", singlePattern);
                Integer patternId = dictionary.getPatternId(singlePattern);
                PRED.debug("SINGLE TYPE: Dictionary returned patternId: {}", patternId);
                
                // Track single message dictionary lookup
                if (listener instanceof LatencyTracker) {
                    ((LatencyTracker)listener).onDictionaryLookup("type_pattern", patternId != null);
                }
                
                if (patternId != null) {
                    // Use existing pattern reference for single TYPE command
                    PRED.info("Found existing TYPE pattern #{} - sending reference instead of full TYPE command", patternId);
                    if (loggerOut.isTraceEnabled())
                        loggerOut.trace("UIContext #{} : DICTIONARY_REFERENCE {}", this.uiContext.getID(), patternId);
                    // Track hash reference
                    if (listener instanceof LatencyTracker) {
                        ((LatencyTracker)listener).onHashCompute("REF#" + patternId, new byte[0]);
                    }
                    websocketPusher.encode(ServerToClientModel.DICTIONARY_REFERENCE, patternId);
                    if (listener != null) listener.onOutgoingPonyFrame(ServerToClientModel.DICTIONARY_REFERENCE, patternId);
                    
                    // Close and flush to ensure latency tracking completes
                    websocketPusher.encode(ServerToClientModel.END, null);
                    if (listener != null) listener.onOutgoingPonyFrame(ServerToClientModel.END, null);
                    flush0();
                    return; // Pattern found and sent, don't add to batch
                }
                
                // No pattern found, start a new batch with this type command
                currentBatch.add(pair);
                return;
            }
            
            // For WIDGET_TYPE commands, always include them in the current batch
            // as they're critical for proper widget initialization
            if (model == ServerToClientModel.WIDGET_TYPE) {
                currentBatch.add(pair);
                return;
            }
            
            // Check if we already have a batch in progress
            PRED.debug("BATCH: currentBatch.size()={}, processing model={}, value={}", currentBatch.size(), model, value);
            if (!currentBatch.isEmpty()) {
                // First try to find the complete pattern including this pair
                List<ModelValuePair> testPattern = new ArrayList<>(currentBatch);
                testPattern.add(pair);
                
                // DEBUG: Log what we're looking up
                PRED.debug("LOOKUP: Looking for pattern of size {} in dictionary: {}", testPattern.size(), testPattern);
                Integer patternId = dictionary.getPatternId(testPattern);
                PRED.debug("LOOKUP: Dictionary returned patternId: {}", patternId);
                // Stage 2: Track dictionary lookup
                if (listener instanceof LatencyTracker) {
                    ((LatencyTracker)listener).onDictionaryLookup("pattern_" + testPattern.size(), patternId != null);
                }
                if (patternId != null) {
                    // Use existing pattern reference
                    PRED.info("Found existing pattern #{} for batch of {} elements - sending reference", patternId, testPattern.size());
                    if (loggerOut.isTraceEnabled())
                        loggerOut.trace("UIContext #{} : DICTIONARY_REFERENCE {}", this.uiContext.getID(), patternId);
                    // Stage 3: Track hash reference
                    if (listener instanceof LatencyTracker) {
                        ((LatencyTracker)listener).onHashCompute("REF#" + patternId, new byte[0]);
                    }
                    websocketPusher.encode(ServerToClientModel.DICTIONARY_REFERENCE, patternId);
                    if (listener != null) listener.onOutgoingPonyFrame(ServerToClientModel.DICTIONARY_REFERENCE, patternId);
                    
                    // Close and flush to ensure latency tracking completes
                    websocketPusher.encode(ServerToClientModel.END, null);
                    if (listener != null) listener.onOutgoingPonyFrame(ServerToClientModel.END, null);
                    flush0();
                    
                    currentBatch.clear();
                    return;
                } else {
                    PRED.debug("No existing pattern found for current batch of {} elements", testPattern.size());
                }
                
                // Add to current batch and check threshold
                currentBatch.add(pair);
                if (currentBatch.size() >= WebSocketConfiguration.getBatchThreshold()) {
                    flushCurrentBatch();
                }
                return;
            }
            
            // No batch in progress, start a new one (typically this is for single frame updates)
            currentBatch.add(pair);
        } catch (final IOException e) {
            log.error("Can't write on the websocket for UIContext #{}, so we destroy the application", uiContext.getID(), e);
            uiContext.destroy();
        }
    }

    /**
     * Check if a model is a critical control frame that should bypass dictionary
     */
    private boolean isControlFrame(final ServerToClientModel model) {
        // Only specific critical protocol frames should bypass dictionary
        // DO NOT blanket exclude all UINT31 types as this prevents widget tracking!
        switch (model) {
            // Core protocol frames
            case CREATE_CONTEXT:
            case OPTION_FORMFIELD_TABULATION:
            case HEARTBEAT_PERIOD:
            case HEARTBEAT:
            case ROUNDTRIP_LATENCY:
            
            // Handler management (but allow TYPE_CREATE/TYPE_UPDATE/etc.)
            case TYPE_ADD_HANDLER:
            case TYPE_REMOVE_HANDLER:
            case HANDLER_TYPE:
            
            // Window/Frame management
            case WINDOW_ID:
            case FRAME_ID:
            
            // Function calls
            case FUNCTION_ID:
            
            // Dictionary protocol (ironically these bypass dictionary)
            case DICTIONARY_PATTERN_START:
            case DICTIONARY_REFERENCE:
            
            // End markers
            case END:
                return true;
                
            // IMPORTANT: Allow these UINT31 types through for dictionary/widget tracking:
            // - TYPE_CREATE, TYPE_UPDATE, TYPE_ADD, TYPE_REMOVE (widget tracking)
            // - WIDGET_ID, PARENT_OBJECT_ID (widget identification)
            // - TYPE_GC (garbage collection patterns)
            default:
                return false;
        }
    }
    
    /**
     * Handle dictionary pattern request from client
     */
    public void handleDictionaryRequest(final int patternId) {
        List<ModelValuePair> pattern = dictionary.getPattern(patternId);
        if (pattern != null) {
            if (log.isDebugEnabled()) {
                log.debug("Sending dictionary pattern {} to client (size: {})", patternId, pattern.size());
            }
            
            try {
                uiContext.acquire();
                try {
                    beginObject();
                    // encode(ServerToClientModel.DICTIONARY_PATTERN_START, patternId); // Causes recursion
                    websocketPusher.encode(ServerToClientModel.DICTIONARY_PATTERN_START, patternId);
                    for (ModelValuePair pair : pattern) {
                        websocketPusher.encode(pair.getModel(), pair.getValue());
                    }
                    // encode(ServerToClientModel.DICTIONARY_PATTERN_END, null); // Causes recursion
                    websocketPusher.encode(ServerToClientModel.DICTIONARY_PATTERN_END, null);
                    endObject();
                    flush0();
                } finally {
                    uiContext.release();
                }
            } catch (Exception e) {
                log.error("Error sending dictionary pattern to client", e);
            }
        } else {
            log.warn("Client requested unknown dictionary pattern: {}", patternId);
        }
    }

    /**
     * Enable or disable dictionary compression
     */
    public void setDictionaryEnabled(boolean enabled) {
        if (WebSocketConfiguration.isDictionaryEnabled() != enabled) {
            log.info("Dictionary compression {} for UIContext #{}", 
                    enabled ? "enabled" : "disabled", 
                    uiContext != null ? uiContext.getID() : "?");
            
            WebSocketConfiguration.setDictionaryEnabled(enabled);
            if (!enabled) {
                // Clear current batch and dictionary when disabling
                currentBatch.clear();
                dictionary.clear();
            }
        }
    }
    
    /**
     * Enable or disable widget trie prediction feature.
     * When disabled, widget interaction sequences are not tracked and no predictions are made.
     */
    public void setTrieEnabled(boolean enabled) {
        if (WebSocketConfiguration.isTrieEnabled() != enabled) {
            log.info("Widget Trie prediction {} for UIContext #{}", 
                    enabled ? "enabled" : "disabled", 
                    uiContext != null ? uiContext.getID() : "?");
            
            WebSocketConfiguration.setTrieEnabled(enabled);
            if (!enabled) {
                // Clear widget interaction tracking when disabling
                synchronized (widgetSequenceLock) {
                    widgetInteractionSequence.clear();
                    widgetMessagePatterns.clear();
                    currentWidgetMessages.clear();
                    lastPredictedWidget = null;
                }
            }
        }
    }
    
    /**
     * Enable or disable CodeT5/FastAPI prediction feature.
     * When disabled, no HTTP calls are made to the prediction service.
     */
    public void setCodeT5Enabled(boolean enabled) {
        if (WebSocketConfiguration.isCodeT5Enabled() != enabled) {
            log.info("CodeT5/FastAPI prediction {} for UIContext #{}", 
                    enabled ? "enabled" : "disabled", 
                    uiContext != null ? uiContext.getID() : "?");
            
            WebSocketConfiguration.setCodeT5Enabled(enabled);
            if (!enabled) {
                // Clear pattern buffer when disabling
                synchronized (predictionLock) {
                    currentPatternBuffer.clear();
                    lastPrediction = null;
                }
            }
        }
    }
    
    /**
     * Static method for global dictionary control from UI
     */
    public static void setDictionaryEnabledGlobally(boolean enabled) {
        log.info("Dictionary compression {} globally", enabled ? "ENABLED" : "DISABLED");
        WebSocketConfiguration.setDictionaryEnabled(enabled);
    }
    
    /**
     * Static method for global trie control from UI
     */
    public static void setTrieEnabledGlobally(boolean enabled) {
        log.info("Widget Trie prediction {} globally", enabled ? "ENABLED" : "DISABLED");
        WebSocketConfiguration.setTrieEnabled(enabled);
    }
    
    /**
     * Static method for global CodeT5 control from UI
     */
    public static void setCodeT5EnabledGlobally(boolean enabled) {
        log.info("CodeT5/FastAPI prediction {} globally", enabled ? "ENABLED" : "DISABLED");
        WebSocketConfiguration.setCodeT5Enabled(enabled);
    }

    public void sendUIComponent(String componentType, String componentId, String componentText) {
        try {
            beginObject();
            encode(ServerToClientModel.UI_COMPONENT_TYPE, componentType);
            encode(ServerToClientModel.UI_COMPONENT_ID, componentId);
            encode(ServerToClientModel.UI_COMPONENT_TEXT, componentText);
            endObject();
            flush();
        } catch (Exception e) {
            log.error("Cannot send UI component to client for UIContext #{}", uiContext.getID(), e);
        }
    }

    private enum NiceStatusCode {

        NORMAL(StatusCode.NORMAL, "Normal closure"),
        SHUTDOWN(StatusCode.SHUTDOWN, "Shutdown"),
        PROTOCOL(StatusCode.PROTOCOL, "Protocol error"),
        BAD_DATA(StatusCode.BAD_DATA, "Received bad data"),
        UNDEFINED(StatusCode.UNDEFINED, "Undefined"),
        NO_CODE(StatusCode.NO_CODE, "No code present"),
        NO_CLOSE(StatusCode.NO_CLOSE, "Abnormal connection closed"),
        ABNORMAL(StatusCode.ABNORMAL, "Abnormal connection closed"),
        BAD_PAYLOAD(StatusCode.BAD_PAYLOAD, "Not consistent message"),
        POLICY_VIOLATION(StatusCode.POLICY_VIOLATION, "Received message violates policy"),
        MESSAGE_TOO_LARGE(StatusCode.MESSAGE_TOO_LARGE, "Message too big"),
        REQUIRED_EXTENSION(StatusCode.REQUIRED_EXTENSION, "Required extension not sent"),
        SERVER_ERROR(StatusCode.SERVER_ERROR, "Server error"),
        SERVICE_RESTART(StatusCode.SERVICE_RESTART, "Server restart"),
        TRY_AGAIN_LATER(StatusCode.TRY_AGAIN_LATER, "Server overload"),
        FAILED_TLS_HANDSHAKE(StatusCode.POLICY_VIOLATION, "Failure handshake");

        private final int statusCode;
        private final String message;

        NiceStatusCode(final int statusCode, final String message) {
            this.statusCode = statusCode;
            this.message = message;
        }

        public static String getMessage(final int statusCode) {
            final List<NiceStatusCode> codes = Arrays.stream(values())
                    .filter(niceStatusCode -> niceStatusCode.statusCode == statusCode).collect(Collectors.toList());
            if (!codes.isEmpty()) {
                return codes.get(0).toString();
            } else {
                log.error("No matching status code found for {}", statusCode);
                return String.valueOf(statusCode);
            }
        }

        @Override
        public String toString() {
            return message + " (" + statusCode + ")";
        }

    }

    public ServletUpgradeRequest getRequest() {
        return request;
    }

    public void setRequest(final ServletUpgradeRequest request) {
        this.request = request;
    }

    public void setApplicationManager(final ApplicationManager applicationManager) {
        this.applicationManager = applicationManager;
    }

    public void setMonitor(final WebsocketMonitor monitor) {
        this.monitor = monitor;
    }
    
    /**
     * Get latency metrics if LatencyTracker is enabled
     */
    public LatencyTracker.LatencyStats getLatencyMetrics() {
        if (listener instanceof LatencyTracker) {
            return ((LatencyTracker)listener).getStageMetrics();
        }
        return null;
    }

    public void setContext(final TxnContext context) {
        this.context = context;
    }

    public void setListener(final Listener listener) {
        this.listener = listener;
        if (this.websocketPusher != null) {
            this.websocketPusher.setWebSocketListener(listener);
        }
        if (!(session instanceof Container)) {
            log.warn("Unrecognized session type {} for {}", session == null ? null : session.getClass(), uiContext);
            return;
        }
        final ExtensionStack extensionStack = ((Container) session).getBean(ExtensionStack.class);
        if (extensionStack == null) {
            log.warn("No Extension Stack for {}", uiContext);
            return;
        }
        final PonyPerMessageDeflateExtension extension = extensionStack.getBean(PonyPerMessageDeflateExtension.class);
        if (extension == null) {
            log.warn("Missing PonyPerMessageDeflateExtension from Extension Stack for {}", uiContext);
            return;
        }
        extension.setWebSocketListener(listener);
    }

    

    public interface Listener {

        void onOutgoingPonyFrame(ServerToClientModel model, Object value);

        void onOutgoingPonyFramesBytes(int bytes);

        void onOutgoingWebSocketFrame(int headerLength, int payloadLength);

        void onIncomingText(String text);

        void onIncomingWebSocketFrame(int headerLength, int payloadLength);
        
        default void onFrameWriteSuccess() {
            // Called when WebSocket frames are successfully acknowledged by the network layer
        }
        
        default void onFrameWriteFailure(Throwable cause) {
            // Called when WebSocket frame writing fails
        }

    }

    /**
     * Flush current batch before handling critical frames like END
     * to ensure proper UI response cycle completion
     */
    private void flushCurrentBatch() {
        Integer ref = null; // Declare ref here
        if (currentBatch.isEmpty()) return;
        
        // CRITICAL FIX: Dictionary Client-Server Synchronization Issue
        // PROBLEM: Server was recording patterns even when dictionary was disabled (during 0-2s UI setup),
        // but client never received these pattern definitions. Later, server would send DICTIONARY_REFERENCE #1
        // but client would respond "Unknown instruction type" because it never got pattern #1's definition.
        // This caused UI breakage: buttons stopped working, "Unknown instruction" errors flooded console.
        // ROOT CAUSE: flushCurrentBatch() called dictionary.recordPattern() regardless of WebSocketConfiguration.isDictionaryEnabled() state.
        // SOLUTION: Only use dictionary logic when enabled, ensuring perfect client-server pattern synchronization.
        if (!WebSocketConfiguration.isDictionaryEnabled()) {
            PRED.debug("Dictionary disabled - sending raw messages (batch size: {})", currentBatch.size());
            try {
                for (ModelValuePair p : currentBatch) {
                    websocketPusher.encode(p.getModel(), p.getValue());
                    if (listener != null) listener.onOutgoingPonyFrame(p.getModel(), p.getValue());
                }
            } catch (final Exception e) {
                log.error("Error sending raw batch for UIContext #{}", uiContext.getID(), e);
            } finally {
                currentBatch.clear();
            }
            return; // Skip dictionary logic entirely
        }
        
        try {
            if (loggerOut.isTraceEnabled())
                loggerOut.trace("UIContext #{} : Flushing batch of size {}", this.uiContext.getID(), currentBatch.size());
            
            // Check if batch has any TYPE_* instructions to ensure valid pattern
            /*
            boolean hasTypeCommand = false;
            ServerToClientModel foundType = null;
            for (ModelValuePair pair : currentBatch) {
                if (isTypeCommand(pair.getModel())) {
                    hasTypeCommand = true;
                    foundType = pair.getModel();
                    break;
                }
            }
            */
            
            // Only try to save patterns that have a TYPE_* command to ensure proper rexplay
            //Integer newId = null;
            /*
            if (hasTypeCommand) {
                newId = dictionary.recordPattern(currentBatch);
                PRED.debug("Recorded pattern {}: {}", newId, currentBatch);
            } */
            // Create a consistent copy for dictionary operations
            List<ModelValuePair> snapshot = new ArrayList<>(currentBatch);   // <-- mutable copy for consistent equals()
            PRED.debug("Trying to record pattern of size {}: {}", snapshot.size(), snapshot);
            Integer newId = dictionary.recordPattern(snapshot);
            
            if (newId != null) {
                PRED.info("Successfully recorded new pattern #{} with {} elements", newId, snapshot.size());
                
                // Track pattern in current triplet
                patternSequence.add(newId);
                
                // When we have 3 patterns, feed to trie and reset
                if (patternSequence.size() == 3) {
                    List<String> triplet = patternSequence.stream()
                        .map(id -> "Pattern#" + id)
                        .collect(Collectors.toList());
                    TrieUtilities.buildSemanticPatternTrieFromStrings(Collections.singletonList(triplet), DICT_TRIE);
                    PRED.info("Trie fed with NEW pattern triplet: {}", triplet);
                    patternSequence.clear(); // Reset for next triplet
                }
            } else {
                PRED.debug("Pattern not recorded - doesn't meet criteria");
            }
            // Widget Interaction Learning (SEPARATE from dictionary compression)
            processWidgetInteraction(snapshot);
            
            if (newId != null) {
                // FIX: Client-Server Dictionary Synchronization (Sep 8 2025)
                // PROBLEM: Server was storing patterns but never sending definitions to client
                // RESULT: Client received DICTIONARY_REFERENCE but had no patterns stored locally
                // SOLUTION: Send DICTIONARY_PATTERN_START + pattern + END to client on first storage
                // This allows client to store pattern locally before we send references
                
                PRED.debug("Dictionary recorded pattern #{} with {} elements - sending definition to client", newId, snapshot.size());
                if (loggerOut.isTraceEnabled())
                    loggerOut.trace("UIContext #{} : Recording new pattern {} (size: {})", 
                            this.uiContext.getID(), newId, snapshot.size());
                
                // CRITICAL: Send pattern definition to client so it can store it locally
                // 
                // PROTOCOL COMPLIANCE FIX (Sept 2025):
                // PROBLEM: Server-client dictionary sync failure causing "Pattern not found" errors
                // ROOT CAUSE: This inline pattern transmission bypassed the beginObject()/endObject() 
                //            contract that UIBuilder.java expects (see sendDictionaryPattern() method)
                // SYMPTOMS: Client logs "Unknown instruction type: TEXT/END", UI objects fail to create
                // COMPETITIVE ANALYSIS: Protocol violations create O(n²) debugging complexity due to 
                //                      cascading failures - one broken message corrupts all subsequent
                // 
                // Send the pattern inline without protocol wrappers
                websocketPusher.encode(ServerToClientModel.DICTIONARY_PATTERN_START, newId);
                if (listener != null) listener.onOutgoingPonyFrame(ServerToClientModel.DICTIONARY_PATTERN_START, newId);
                
                // Send each ModelValuePair in the pattern
                for (ModelValuePair p : snapshot) {
                    websocketPusher.encode(p.getModel(), p.getValue());
                    if (listener != null) listener.onOutgoingPonyFrame(p.getModel(), p.getValue());
                }
                
                // End pattern definition
                websocketPusher.encode(ServerToClientModel.DICTIONARY_PATTERN_END, null);
                if (listener != null) listener.onOutgoingPonyFrame(ServerToClientModel.DICTIONARY_PATTERN_END, null);
                websocketPusher.encode(ServerToClientModel.END, null);
                if (listener != null) listener.onOutgoingPonyFrame(ServerToClientModel.END, null);
                flush0();
                
                PRED.info("SYNC FIX: Sent pattern definition #{} to client - future references will work", newId);
                
                // Continue to execute the actual message after sending pattern definition
                // The pattern definition was just to teach the client, now execute the actual command
            }
            else if ((ref = dictionary.getPatternId(snapshot)) != null) {

            
                // Track existing pattern in triplet sequence
                /*
                patternSequence.add(ref);
               
                // When we have 3 patterns, feed to trie and reset
                if (patternSequence.size() == 3) {
                    List<String> triplet = patternSequence.stream()
                        .map(id -> "Pattern#" + id)
                        .collect(Collectors.toList());
                    TrieUtilities.buildSemanticPatternTrieFromStrings(Collections.singletonList(triplet), DICT_TRIE);
                    PRED.info("Trie fed with EXISTING pattern triplet: {}", triplet);
                    patternSequence.clear(); // Reset for next triplet
                }
                */
                // Tell the client "replay pattern #ref"
                websocketPusher.encode(ServerToClientModel.DICTIONARY_REFERENCE, ref);
                if (listener != null) listener.onOutgoingPonyFrame(ServerToClientModel.DICTIONARY_REFERENCE, ref);
                
                // Then close the message and flush it so the client actually applies it
                websocketPusher.encode(ServerToClientModel.END, null);
                if (listener != null) listener.onOutgoingPonyFrame(ServerToClientModel.END, null);
                flush0();

                currentBatch.clear();
                return;
            }
            
            // Send the actual frames (either after definition or as raw frames)
            for (ModelValuePair p : snapshot) {
                websocketPusher.encode(p.getModel(), p.getValue());
                if (listener != null) listener.onOutgoingPonyFrame(p.getModel(), p.getValue());
            }
        } catch (final Exception e) {
            log.error("Error in flushCurrentBatch for UIContext #{}", uiContext.getID(), e);
        } finally {
            currentBatch.clear();
        }
    }
    
    /**
     * Process widget interaction for trie-based prediction.
     * This is SEPARATE from dictionary compression.
     * 
     * ALGORITHM:
     * 1. Detect widget events from message stream
     * 2. Build complete message patterns for each widget
     * 3. Track widget interaction sequences 
     * 4. Learn sequences in trie when we have triplets
     * 5. Predict next widget when we see pairs
     */
    private void processWidgetInteraction(final List<ModelValuePair> messageSequence) {
        if (messageSequence == null || messageSequence.isEmpty()) return;
        
        synchronized (widgetSequenceLock) {
            try {
                // Complete current widget interaction if we have data
                if (currentWidgetKey != null && !currentWidgetMessages.isEmpty()) {
                    completeCurrentWidgetInteraction();
                }
                
                PRED.debug("Processing widget interaction with {} messages", messageSequence.size());
                
            } catch (Exception e) {
                PRED.error("Error processing widget interaction: {}", e.getMessage(), e);
            }
        }
    }
    
    /**
     * Track widget interaction data from individual encode() calls.
     * Builds up widget information across multiple messages.
     */
    private void trackWidgetInteractionData(final ServerToClientModel model, final Object value) {
        synchronized (widgetSequenceLock) {
            try {
                // Detect widget ID from TYPE_UPDATE or TYPE_CREATE
                if (model == ServerToClientModel.TYPE_UPDATE && value instanceof Integer) {
                    currentWidgetId = (Integer) value;
                    PRED.debug("Detected widget ID: {}", currentWidgetId);
                }
                else if (model == ServerToClientModel.TYPE_CREATE && value instanceof Integer) {
                    currentWidgetId = (Integer) value;
                    PRED.debug("Detected new widget ID: {}", currentWidgetId);
                }
                
                // Detect widget type (handle both Integer ordinals and String values)
                else if (model == ServerToClientModel.WIDGET_TYPE && value != null) {
                    if (value instanceof Integer) {
                        // Convert enum ordinal to widget type name
                        try {
                            int ordinal = (Integer) value;
                            currentWidgetType = WidgetType.fromRawValue(ordinal).name();
                            PRED.debug("Detected widget type from ordinal {}: {}", ordinal, currentWidgetType);
                        } catch (Exception e) {
                            // Fallback for unknown ordinals
                            currentWidgetType = "WIDGET_TYPE_" + value;
                            PRED.debug("Unknown widget type ordinal {}, using fallback: {}", value, currentWidgetType);
                        }
                    } else {
                        // Handle string values directly
                        currentWidgetType = value.toString();
                        PRED.debug("Detected widget type from string: {}", currentWidgetType);
                    }
                    
                    // Store widget type mapping for future reference
                    if (currentWidgetId != null) {
                        widgetTypeById.put(currentWidgetId, currentWidgetType);
                        currentWidgetKey = currentWidgetType + "#" + currentWidgetId;
                        PRED.debug("Built widget key: {} and stored type mapping", currentWidgetKey);
                    }
                }
                
                // Add all messages to current widget (they'll be used for prediction)
                currentWidgetMessages.add(new ModelValuePair(model, value));
                
            } catch (Exception e) {
                PRED.error("Error tracking widget interaction data: {}", e.getMessage(), e);
            }
        }
    }
    
    /**
     * Complete the current widget interaction and add it to the sequence.
     * This happens when we finish processing all messages for a widget.
     */
    private void completeCurrentWidgetInteraction() {
        if (currentWidgetKey == null || currentWidgetMessages.isEmpty()) {
            return;
        }
        
        try {
            // Store complete message pattern for this widget (only if trie is enabled)
            if (WebSocketConfiguration.isTrieEnabled()) {
                widgetMessagePatterns.put(currentWidgetKey, new ArrayList<>(currentWidgetMessages));
                
                // Add widget to interaction sequence
                widgetInteractionSequence.add(currentWidgetKey);
                
                PRED.info("Completed widget interaction: {} with {} messages", 
                         currentWidgetKey, currentWidgetMessages.size());
                
                // Try to predict next widget (if we have 2 in sequence)
                if (widgetInteractionSequence.size() >= 2) {
                    tryPredictNextWidget();
                }
            } else {
                PRED.debug("Widget Trie disabled - skipping interaction tracking for {}", currentWidgetKey);
            }
            
            // Validate previous prediction (if we had one)
            if (lastPredictedWidget != null) {
                validatePrediction();
            }
            
            // Learn sequence pattern in trie (if we have 3 in sequence)
            if (widgetInteractionSequence.size() >= 3) {
                learnWidgetSequenceInTrie();
            }
            
            // Reset current widget tracking
            resetCurrentWidgetTracking();
            
        } catch (Exception e) {
            PRED.error("Error completing widget interaction: {}", e.getMessage(), e);
            resetCurrentWidgetTracking();
        }
    }
    
    /**
     * Try to predict the next widget based on the current sequence.
     */
    private void tryPredictNextWidget() {
        if (!WebSocketConfiguration.isTrieEnabled()) return;  // Feature control check
        if (widgetInteractionSequence.size() < 2) return;
        
        try {
            // Get last two widget interactions
            int size = widgetInteractionSequence.size();
            String widget1 = widgetInteractionSequence.get(size - 2);
            String widget2 = widgetInteractionSequence.get(size - 1);
            
            // Look up prediction in trie
            WidgetTrieNode node = WIDGET_TRIE.children.get(widget1);
            if (node != null) {
                node = node.children.get(widget2);
                if (node != null && !node.children.isEmpty()) {
                    // Found potential predictions - pick the most frequent one
                    String prediction = node.children.entrySet().stream()
                        .max((e1, e2) -> Integer.compare(e1.getValue().sequenceFrequency, e2.getValue().sequenceFrequency))
                        .map(Map.Entry::getKey)
                        .orElse(null);
                    
                    if (prediction != null) {
                        lastPredictedWidget = prediction;
                        PRED.info("PREDICTION: After [{}] -> [{}], predict [{}]", 
                                 widget1, widget2, prediction);
                        
                        // Get complete message pattern for predicted widget
                        List<ModelValuePair> predictedMessages = widgetMessagePatterns.get(prediction);
                        if (predictedMessages != null) {
                            PRED.info("Predicted message pattern: {}", predictedMessages);
                            // TODO: Send prediction to client early
                        }
                        
                        // Measure successful widget prediction
                        if (listener instanceof LatencyTracker) {
                            ((LatencyTracker) listener).onWidgetPrediction(prediction, true);
                        }
                    } else {
                        // Measure failed widget prediction
                        if (listener instanceof LatencyTracker) {
                            ((LatencyTracker) listener).onWidgetPrediction("none", false);
                        }
                    }
                } else {
                    // Measure widget prediction miss (no patterns found)
                    if (listener instanceof LatencyTracker) {
                        ((LatencyTracker) listener).onWidgetPrediction("none", false);
                    }
                }
            }
            
        } catch (Exception e) {
            PRED.error("Error predicting next widget: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Validate our previous prediction against the actual widget.
     */
    private void validatePrediction() {
        if (!WebSocketConfiguration.isTrieEnabled()) return;  // Feature control check
        if (lastPredictedWidget == null || currentWidgetKey == null) {
            return;
        }
        
        try {
            if (lastPredictedWidget.equals(currentWidgetKey)) {
                PRED.info("PREDICTION SUCCESS: {} == {} ✓", lastPredictedWidget, currentWidgetKey);
                
                // Measure prediction validation success
                if (listener instanceof LatencyTracker) {
                    ((LatencyTracker) listener).onWidgetPrediction(currentWidgetKey, true);
                }
            } else {
                PRED.info("PREDICTION FAILED: {} != {} ✗", lastPredictedWidget, currentWidgetKey);
                
                // Measure prediction validation failure
                if (listener instanceof LatencyTracker) {
                    ((LatencyTracker) listener).onWidgetPrediction(currentWidgetKey, false);
                }
            }
            
            lastPredictedWidget = null;
            
        } catch (Exception e) {
            PRED.error("Error validating prediction: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Learn widget sequence pattern in trie.
     */
    private void learnWidgetSequenceInTrie() {
        if (widgetInteractionSequence.size() < 3) return;
        
        try {
            // Get last three widget interactions
            int size = widgetInteractionSequence.size();
            List<String> triplet = widgetInteractionSequence.subList(size - 3, size);
            
            // Navigate/create trie path
            WidgetTrieNode current = WIDGET_TRIE;
            for (String widget : triplet) {
                current = current.children.computeIfAbsent(widget, k -> new WidgetTrieNode());
            }
            
            // Mark as end of sequence and store data
            current.isEndOfSequence = true;
            current.completeWidgetSequence = new ArrayList<>(triplet);
            current.sequenceFrequency++;
            
            // Store complete message pattern for all widgets in the triplet sequence
            List<ModelValuePair> completeSequencePattern = new ArrayList<>();
            for (String widget : triplet) {
                List<ModelValuePair> widgetPattern = widgetMessagePatterns.get(widget);
                if (widgetPattern != null) {
                    completeSequencePattern.addAll(widgetPattern);
                }
            }
            current.completeMessagePattern = completeSequencePattern;
            
            PRED.info("LEARNED SEQUENCE: {} (frequency: {})", triplet, current.sequenceFrequency);
            
        } catch (Exception e) {
            PRED.error("Error learning widget sequence in trie: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Reset current widget tracking state.
     */
    private void resetCurrentWidgetTracking() {
        currentWidgetKey = null;
        currentWidgetType = null;
        currentWidgetId = null;
        currentWidgetMessages.clear();
    }
    
//12#26 → 12#27 → 12#28

    /**
     * Helper method to check if a model is a TYPE_* command
     */
    private boolean isTypeCommand(final ServerToClientModel model) {
        return model == ServerToClientModel.TYPE_CREATE || 
               model == ServerToClientModel.TYPE_UPDATE || 
               model == ServerToClientModel.TYPE_ADD || 
               model == ServerToClientModel.TYPE_REMOVE || 
               model == ServerToClientModel.TYPE_ADD_HANDLER || 
               model == ServerToClientModel.TYPE_REMOVE_HANDLER || 
               model == ServerToClientModel.TYPE_GC;
    }

    /**
     * Demonstrates a JSON POST request using HttpURLConnection.
     * This method is placed on the server-side within WebSocket.java.
     * It executes the network request in a separate thread to avoid
     * blocking the main WebSocket processing thread.
     */
    private void sendJsonPostRequestUsingHttpURLConnection(List<String> instructionsToSend) {
        if (!WebSocketConfiguration.isCodeT5Enabled()) {
            PRED.debug("CodeT5/FastAPI disabled - skipping HTTP call for {} instructions", instructionsToSend.size());
            return;  // Feature control check
        }
        
        // Wrap the network call in a new thread to avoid blocking the current thread.
        new Thread(() -> {
            final long startNanos = System.nanoTime();
            HttpURLConnection con = null;
            boolean success = false;
            
            try {
                // Define the URL of your FastAPI server endpoint.
                // Ensure your FastAPI server is running on http://127.0.0.1:8000/.
                URL url = new URL("http://127.0.0.1:8000/generate"); 
                
                // Open a connection to the URL.
                con = (HttpURLConnection) url.openConnection();
                
                // Set the request method to POST.
                con.setRequestMethod("POST");
                
                // Set Content-Type header to "application/json".
                con.setRequestProperty("Content-Type", "application/json");
                
                // Set Accept header to "application/json".
                con.setRequestProperty("Accept", "application/json");
                
                // 5. Enable output for the connection.
                //    This tells the connection that we intend to write data to the output stream.
                con.setDoOutput(true);

                // 6. Define the JSON string to send.
                //    This is the payload that your FastAPI server expects.
                String instructionsContent = instructionsToSend.stream()
                .map(Object::toString)
                .collect(Collectors.joining("\n"));

                // Use JsonObjectBuilder to properly format the JSON string,
                // which will handle escaping of special characters like newlines and quotes.
                JsonObjectBuilder jsonBuilder = Json.createObjectBuilder()
                    .add("input_text", instructionsContent)
                    .add("max_length", 64);
                
                String jsonInputString = jsonBuilder.build().toString();
                
                //String jsonInputString = "{\"input_text\": \"PButton#6, text=Send Custom UI Component, html=null : {\\\"0\\\":6,\\\"g\\\":2,\\\"f\\\":[116,67,106,15,1,false,false,false,false],\\\"d\\\":[10,52,23,182]}\\nPButton#7, text=Create Dynamic Components, html=null : {\\\"0\\\":7,\\\"g\\\":2,\\\"f\\\":[242,57,51,5,1,false,false,false,false],\\\"d\\\":[191,52,23,186]}\\nPButton#8, text=Static Component, html=null : {\\\"0\\\":8,\\\"g\\\":2,\\\"f\\\":[405,59,28,7,1,false,false,false,false],\\\"d\\\":[377,52,23,119]}\", \"max_length\": 64}";
                
                // 7. Write the JSON string to the connection's output stream.
                //    The data is converted to bytes using UTF-8 encoding.
                try (OutputStream os = con.getOutputStream()) {
                    byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                // Read the response from the server.
                try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    // Log the HTTP response code and the received response body.
                    log.info("FastAPI HTTP Response Code: " + con.getResponseCode() + ", Body: " + response);

                    String prediction = null;
                    try{
                        // Parse the JSON response to extract the 'instruction' field
                        try (JsonReader jsonResponseReader = Json.createReader(new StringReader(response.toString()))) {
                            JsonObject jsonResponse = jsonResponseReader.readObject();
                            if (jsonResponse.containsKey("generated_text")) {
                                prediction = jsonResponse.getString("generated_text");
                            }
                        }

                        log.info("FastAPI HTTP Response Code: " + con.getResponseCode() + ", Body: " + response);

                        // BEGIN : Prediction Comparison Logic (FastAPI Response)
                        if (prediction != null && !instructionsToSend.isEmpty()) {
                            String actualInstruction = instructionsToSend.get(instructionsToSend.size() - 1); // Get the last instruction sent
                            String predictedInstruction = prediction;

                            log.info("--- Instruction Comparison (FastAPI Response) ---");
                            log.info("Actual (Client) : {}", actualInstruction);
                            log.info("Predicted (FastAPI): {}", predictedInstruction);

                            double similarityScore = calculateSimilarity(actualInstruction, predictedInstruction);
                            log.info("Similarity Score: {}", String.format("%.2f", similarityScore));

                            if (actualInstruction.equals(predictedInstruction)) {
                                log.info("Result: Prediction EXACTLY MATCHED the actual instruction.");
                            } else if (similarityScore >= 0.8) { // You can adjust this threshold
                                log.warn("Result: Prediction is SIMILAR (score >= 0.8) but not exact.");
                                int diffIndex = findFirstDifferenceIndex(actualInstruction, predictedInstruction);
                                if (diffIndex != -1) {
                                    log.warn("First difference at index {}: Actual='{}', Predicted='{}'",
                                             diffIndex,
                                             (diffIndex < actualInstruction.length() ? actualInstruction.charAt(diffIndex) : "EOF"),
                                             (diffIndex < predictedInstruction.length() ? predictedInstruction.charAt(diffIndex) : "EOF"));
                                }
                            } else {
                                log.warn("Result: Prediction MISMATCH! Low similarity score.");
                                int diffIndex = findFirstDifferenceIndex(actualInstruction, predictedInstruction);
                                if (diffIndex != -1) {
                                    log.warn("First difference at index {}: Actual='{}', Predicted='{}'",
                                             diffIndex,
                                             (diffIndex < actualInstruction.length() ? actualInstruction.charAt(diffIndex) : "EOF"),
                                             (diffIndex < predictedInstruction.length() ? predictedInstruction.charAt(diffIndex) : "EOF"));
                                }
                            }
                        }
                        // END : Prediction Comparison Logic

                        synchronized (predictionLock) {
                            lastPrediction = prediction; // Update lastPrediction with the current prediction
                        }
                        
                        success = true; // Mark as successful
                        
                    } catch (Exception e) {
                        log.error("Error parsing FastAPI response or performing comparison: ", e);
                    }

                    con.disconnect();
                }
            } catch (IOException e) {
                log.error("Error sending POST request to FastAPI: ", e);
            } finally {
                // Measure CodeT5/FastAPI request latency
                final long latencyNanos = System.nanoTime() - startNanos;
                if (listener instanceof LatencyTracker) {
                    ((LatencyTracker) listener).onCodeT5Query(success, latencyNanos, "generate");
                }
            }
        }).start(); // Start the new thread for the network call.
    }

    /**
     * Calculates a simple character-by-character similarity ratio between two strings.
     * This is a basic implementation and not equivalent to Python's difflib.SequenceMatcher.ratio().
     * Returns a value between 0.0 (no similarity) and 1.0 (exact match).
     */
    private static double calculateSimilarity(String a, String b) {
        if (a == null || b == null) {
            return 0.0;
        }
        if (a.equals(b)) {
            return 1.0;
        }

        int maxLength = Math.max(a.length(), b.length());
        if (maxLength == 0) {
            return 1.0; // Both empty strings, considered similar
        }

        int matchingCharacters = 0;
        int minLength = Math.min(a.length(), b.length());

        for (int i = 0; i < minLength; i++) {
            if (a.charAt(i) == b.charAt(i)) {
                matchingCharacters++;
            }
        }
        // Ratio based on matching characters over the longest string length
        return (double) matchingCharacters / maxLength;
    }

    private static int findFirstDifferenceIndex(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return 0; // Or -1, depending on desired behavior for nulls
        }
        int minLength = Math.min(s1.length(), s2.length());
        for (int i = 0; i < minLength; i++) {
            if (s1.charAt(i) != s2.charAt(i)) {
                return i;
            }
        }
        if (s1.length() != s2.length()) {
            return minLength; // One string is a prefix of the other
        }
        return -1; // Strings are identical
    }

    // -- Start of Semantic Pattern Matching for Prediction --

    // The core logic for the triplet-only prediction algorithm.
    private void processInstructionForPrediction(final String componentType, final String fullInstruction) {
        if (!WebSocketConfiguration.isCodeT5Enabled()) {
            PRED.debug("CodeT5/FastAPI disabled - skipping pattern processing for component: {}", componentType);
            return;  // Feature control check
        }
        
        // Synchronize to ensure that buffer modifications and checks are atomic.
        synchronized (predictionLock) {
            // Add the new instruction to the buffer for pattern evaluation.
            currentPatternBuffer.add(fullInstruction);
            PRED.debug("Buffer now: {}", currentPatternBuffer);
            // Get the list of component types currently in the buffer.
            final List<String> componentTypes = currentPatternBuffer.stream().map(this::extractComponentType).collect(Collectors.toList());
            

            switch (componentTypes.size()) {
                case 1:
                    // If the first item cannot start any known triplet, flush it immediately.
                    if (!isPrefixOfKnownTriplet(componentTypes)) {
                        PRED.debug("'{}' is not a valid prefix. Flushing.", componentTypes.get(0));
                        sendJsonPostRequestUsingHttpURLConnection(new ArrayList<>(currentPatternBuffer));
                        currentPatternBuffer.clear();
                    }
                    // Otherwise, we wait for the second item.
                    break;

                case 2:
                    // If the two items form a valid prefix, send them for prediction.
                    if (isPrefixOfKnownTriplet(componentTypes)) {
                        PRED.debug("Sending prefix for prediction: {}", componentTypes);
                        sendJsonPostRequestUsingHttpURLConnection(new ArrayList<>(currentPatternBuffer));
                    } else {
                        // If the prefix is invalid, the first item was a dead end. Flush it.
                        PRED.debug("'{}' is not a valid prefix. Flushing first item.", componentTypes);
                        sendJsonPostRequestUsingHttpURLConnection(Arrays.asList(currentPatternBuffer.remove(0)));
                        // Re-evaluate the buffer, which now contains only the second item as a new potential start.
                        if (!currentPatternBuffer.isEmpty()) {
                            processInstructionForPrediction(extractComponentType(currentPatternBuffer.get(0)), currentPatternBuffer.get(0));
                        }
                    }
                    break;

                case 3:
                    // With the third item, we don't predict again. We verify the previous prediction.
                    final String actualThirdInstruction = fullInstruction;
                    synchronized(predictionLock) {
                        PRED.info("Comparing prediction. Predicted: '{}', Actual: '{}'", lastPrediction, actualThirdInstruction);
                        // NOTE: Add your comparison and logging logic here.
                    }


                    // Only add **new** triplets into the static trie
                    //
                    //    accumulatedPatterns.add(new ArrayList<>(componentTypes));
                    //    buildSemanticPatternTrie(accumulatedPatterns);
                    if (!isKnownTriplet(componentTypes)) {
                        // directly register *this* 3-element pattern
                        TrieUtilities.buildSemanticPatternTrieFromStrings(Collections.singletonList(componentTypes), DICT_TRIE);
                        PRED.info("Learned new triplet: {}", componentTypes);
                        TrieUtilities.dumpTrie(DICT_TRIE, "");
                    }
                    // Reset for the next round by clearing the buffer.
                    currentPatternBuffer.clear();
                    // Re-inject the third item as the potential start of a new triplet.
                    PRED.debug("Resetting buffer and starting new cycle with: {}", actualThirdInstruction);
                    processInstructionForPrediction(extractComponentType(actualThirdInstruction), actualThirdInstruction);
                    break;
            }
        }
    }

    // Sends any buffered instructions individually to the prediction service.
    private void flushBufferedInstructions() {
        if (!WebSocketConfiguration.isCodeT5Enabled()) return;  // Feature control check
        
        synchronized (predictionLock) {
            // Check if there are any instructions left in the buffer.
            if (!currentPatternBuffer.isEmpty()) {
                log.info("Flushing {} remaining buffered instruction(s).", currentPatternBuffer.size());
                // Send each buffered instruction as a separate, individual request.
                for (final String bufferedInstruction : currentPatternBuffer) {
                    
                    sendJsonPostRequestUsingHttpURLConnection(Arrays.asList(bufferedInstruction));
                }
                // Clear the buffer after flushing.
                currentPatternBuffer.clear();
            }
        }
    }

    // A utility to parse the PonySDK component type (e.g., "PButton") from a raw instruction string.
    private String extractComponentType(final String instruction) {
        if (instruction == null || instruction.isEmpty()) return null;
        // The component type is typically before the first '#' character.
        final int hashIndex = instruction.indexOf('#');
        if (hashIndex != -1) return instruction.substring(0, hashIndex);

        // Fallback for cases where there might not be an object ID.
        final int firstSpace = instruction.indexOf(' ');
        if (firstSpace != -1) return instruction.substring(0, firstSpace);
        final int firstComma = instruction.indexOf(',');
        if (firstComma != -1) return instruction.substring(0, firstComma);

        return instruction.trim();
    }

    // -- End of Semantic Pattern Matching for Prediction --
}

