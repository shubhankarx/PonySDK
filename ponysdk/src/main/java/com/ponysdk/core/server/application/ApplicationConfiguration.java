/*
 * Copyright (c) 2011 PonySDK
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

package com.ponysdk.core.server.application;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.ponysdk.core.ui.main.EntryPoint;

public class ApplicationConfiguration {

    public static final String APPLICATION_ID = "ponysdk.application.id";
    public static final String APPLICATION_NAME = "ponysdk.application.name";
    public static final String APPLICATION_DESCRIPTION = "ponysdk.application.description";
    public static final String APPLICATION_CONTEXT_NAME = "ponysdk.application.context.name";
    public static final String STYLESHEETS = "ponysdk.application.stylesheets";
    public static final String JAVASCRIPTS = "ponysdk.application.javascripts";
    public static final String POINTCLASS = "ponysdk.entry.point.class";

    private String applicationID;
    private String applicationName;
    private String applicationDescription;
    private String applicationContextName = "sample";
    private long heartBeatPeriod = 5000;// ms
    private TimeUnit heartBeatPeriodTimeUnit = TimeUnit.MILLISECONDS;
    private boolean enableClientToServerHeartBeat = true;

    private int sessionTimeout = 15; // minutes

    private Set<String> meta;
    private Map<String, String> style;
    private Set<String> javascript;

    private Class<? extends EntryPoint> entryPointClass;

    private String clientConfigFile;

    private boolean debugMode;

    private boolean tabindexOnlyFormField;

    // ========== WebSocket Optimization Feature Flags for A/B Testing ==========
    // These constants define system property names for runtime configuration
    public static final String DICTIONARY_ENABLED = "ponysdk.websocket.dictionary.enabled";
    public static final String TRIE_ENABLED = "ponysdk.websocket.trie.enabled";
    public static final String CODET5_ENABLED = "ponysdk.websocket.codet5.enabled";
    public static final String LATENCY_TRACKING_ENABLED = "ponysdk.websocket.latency.enabled";
    public static final String CODET5_SERVICE_URL = "ponysdk.websocket.codet5.url";
    public static final String DICTIONARY_FREQUENCY_THRESHOLD = "ponysdk.websocket.dictionary.threshold";
    public static final String EXPERIMENT_RUN_ID = "ponysdk.experiment.run.id";
    public static final String EXPERIMENT_RESULTS_DIR = "ponysdk.experiment.results.dir";

    // Dictionary compression: reduces WebSocket payload size by replacing repeated patterns with IDs
    // Links to: ModelValueDictionary.java, WebSocket.java:80-84
    // Purpose: Enable/disable pattern-based compression for performance comparison
    private boolean dictionaryCompressionEnabled;
    
    // Trie-based pattern prediction: uses widget interaction sequences to predict next actions
    // Links to: WebSocket.java:1946-1999, WidgetTrieNode structure
    // Purpose: Enable/disable predictive UI loading based on user interaction patterns
    private boolean triePatternPredictionEnabled;
    
    // CodeT5 semantic analysis: uses external FastAPI service for AI-based pattern analysis
    // Links to: WebSocket.java:2162-2355, HTTP client integration
    // Purpose: Enable/disable AI-powered semantic pattern matching via external service
    private boolean codeT5SemanticAnalysisEnabled;
    
    // Latency tracking: comprehensive metrics collection for performance measurement
    // Links to: LatencyTracker.java, 5-stage pipeline measurement
    // Purpose: Enable/disable detailed performance metrics collection for analysis
    private boolean latencyTrackingEnabled;
    
    // CodeT5 service endpoint for semantic pattern analysis
    // Default: "http://127.0.0.1:8000/generate" (local FastAPI service)
    // Purpose: Configure external AI service URL for flexible deployment scenarios
    private String codeT5ServiceUrl;
    
    // Minimum frequency for dictionary pattern promotion (default: 2)
    // Links to: ModelValueDictionary frequency-based promotion logic
    // Purpose: Control sensitivity of pattern detection (lower = more aggressive compression)
    private int dictionaryFrequencyThreshold;
    
    // Experiment run identifier for reproducible testing
    // Format: "run_<timestamp>" or custom identifier like "run_A", "run_B"
    // Purpose: Unique identifier for each test run to organize results and ensure reproducibility
    // Choice: String type allows both auto-generated timestamps and meaningful names (A/B/C/D)
    private String experimentRunId;
    
    // Directory for storing experiment results and metrics
    // Default: "./results" - relative to application working directory
    // Purpose: Centralized location for JSON output files, manifests, and performance data
    // Choice: String type provides flexible path configuration (absolute/relative/network paths)
    private String experimentResultsDir;

    public ApplicationConfiguration() {
        applicationID = System.getProperty(APPLICATION_ID);
        applicationName = System.getProperty(APPLICATION_NAME);
        applicationDescription = System.getProperty(APPLICATION_DESCRIPTION);
        
        // Initialize WebSocket optimization flags from system properties with sensible defaults
        // These can be overridden at runtime via JVM args: -Dponysdk.websocket.dictionary.enabled=false
        dictionaryCompressionEnabled = Boolean.parseBoolean(System.getProperty(DICTIONARY_ENABLED, "true"));
        triePatternPredictionEnabled = Boolean.parseBoolean(System.getProperty(TRIE_ENABLED, "true"));
        codeT5SemanticAnalysisEnabled = Boolean.parseBoolean(System.getProperty(CODET5_ENABLED, "true"));
        latencyTrackingEnabled = Boolean.parseBoolean(System.getProperty(LATENCY_TRACKING_ENABLED, "true"));
        codeT5ServiceUrl = System.getProperty(CODET5_SERVICE_URL, "http://127.0.0.1:8000/generate");
        dictionaryFrequencyThreshold = Integer.parseInt(System.getProperty(DICTIONARY_FREQUENCY_THRESHOLD, "2"));
        experimentRunId = System.getProperty(EXPERIMENT_RUN_ID, generateDefaultRunId());
        experimentResultsDir = System.getProperty(EXPERIMENT_RESULTS_DIR, "./results");
    }

    /**
     * Generates a unique run identifier using current timestamp for experiment tracking.
     * 
     * Purpose: Ensures each test run has a unique identifier for result organization.
     * Format: "run_<epochMilliseconds>" - e.g., "run_1703123456789"
     * 
     * Why timestamp-based:
     * - Guarantees uniqueness across multiple test runs
     * - Naturally sortable chronologically  
     * - Human-readable when converted to date
     * - Lightweight compared to UUID
     * 
     * Alternative: Users can override with meaningful names via system property:
     * -Dponysdk.experiment.run.id=run_A_dictionary_only
     * 
     * @return Unique run identifier string
     */
    private String generateDefaultRunId() {
        return "run_" + System.currentTimeMillis();
    }

    public String getApplicationID() {
        return applicationID;
    }

    public void setApplicationID(final String applicationID) {
        this.applicationID = applicationID;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(final String applicationName) {
        this.applicationName = applicationName;
    }

    public String getApplicationDescription() {
        return applicationDescription;
    }

    public void setApplicationDescription(final String applicationDescription) {
        this.applicationDescription = applicationDescription;
    }

    public String getApplicationContextName() {
        return applicationContextName;
    }

    public void setApplicationContextName(final String applicationContextName) {
        this.applicationContextName = applicationContextName;
    }

    public void setHeartBeatPeriod(final long heartBeatPeriod, final TimeUnit heartBeatPeriodTimeUnit) {
        this.heartBeatPeriod = heartBeatPeriod;
        this.heartBeatPeriodTimeUnit = heartBeatPeriodTimeUnit;
    }

    public long getHeartBeatPeriod() {
        return heartBeatPeriod;
    }

    public void setHeartBeatPeriod(final long heartBeatPeriod) {
        setHeartBeatPeriod(heartBeatPeriod, TimeUnit.MILLISECONDS);
    }

    public TimeUnit getHeartBeatPeriodTimeUnit() {
        return heartBeatPeriodTimeUnit;
    }

    public int getSessionTimeout() {
        return sessionTimeout;
    }

    public void setSessionTimeout(final int sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
    }

    public Set<String> getJavascript() {
        return javascript;
    }

    public void setJavascript(final Set<String> javascript) {
        this.javascript = javascript;
    }

    public Map<String, String> getStyle() {
        return style;
    }

    public void setStyle(final Map<String, String> style) {
        this.style = style;
    }

    public Set<String> getMeta() {
        return meta;
    }

    public void setMeta(final Set<String> meta) {
        this.meta = meta;
    }

    public Class<? extends EntryPoint> getEntryPointClass() {
        return entryPointClass;
    }

    public void setEntryPointClass(final Class<? extends EntryPoint> entryPointClass) {
        this.entryPointClass = entryPointClass;
    }

    public String getClientConfigFile() {
        return clientConfigFile;
    }

    public void setClientConfigFile(final String clientConfigFile) {
        this.clientConfigFile = clientConfigFile;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public void setDebugMode(final boolean debugMode) {
        this.debugMode = debugMode;
    }

    public boolean isTabindexOnlyFormField() {
        return tabindexOnlyFormField;
    }

    public void setTabindexOnlyFormField(final boolean tabindexOnlyFormField) {
        this.tabindexOnlyFormField = tabindexOnlyFormField;
    }

    @Override
    public String toString() {
        return "ApplicationManagerOption [heartBeatPeriod=" + heartBeatPeriod + " " + heartBeatPeriodTimeUnit + "]";
    }

    public boolean isEnableClientToServerHeartBeat() {
        return enableClientToServerHeartBeat;
    }

    public void setEnableClientToServerHeartBeat(final boolean enableClientToServerHeartBeat) {
        this.enableClientToServerHeartBeat = enableClientToServerHeartBeat;
    }

    // ========== WebSocket Optimization Configuration Getters/Setters ==========

    /**
     * @return true if dictionary compression is enabled for WebSocket communication
     */
    public boolean isDictionaryCompressionEnabled() {
        return dictionaryCompressionEnabled;
    }

    public void setDictionaryCompressionEnabled(final boolean dictionaryCompressionEnabled) {
        this.dictionaryCompressionEnabled = dictionaryCompressionEnabled;
    }

    /**
     * @return true if trie-based pattern prediction is enabled
     */
    public boolean isTriePatternPredictionEnabled() {
        return triePatternPredictionEnabled;
    }

    public void setTriePatternPredictionEnabled(final boolean triePatternPredictionEnabled) {
        this.triePatternPredictionEnabled = triePatternPredictionEnabled;
    }

    /**
     * @return true if CodeT5 semantic analysis is enabled
     */
    public boolean isCodeT5SemanticAnalysisEnabled() {
        return codeT5SemanticAnalysisEnabled;
    }

    public void setCodeT5SemanticAnalysisEnabled(final boolean codeT5SemanticAnalysisEnabled) {
        this.codeT5SemanticAnalysisEnabled = codeT5SemanticAnalysisEnabled;
    }

    /**
     * @return true if latency tracking is enabled
     */
    public boolean isLatencyTrackingEnabled() {
        return latencyTrackingEnabled;
    }

    public void setLatencyTrackingEnabled(final boolean latencyTrackingEnabled) {
        this.latencyTrackingEnabled = latencyTrackingEnabled;
    }

    /**
     * @return CodeT5 service URL for semantic analysis
     */
    public String getCodeT5ServiceUrl() {
        return codeT5ServiceUrl;
    }

    public void setCodeT5ServiceUrl(final String codeT5ServiceUrl) {
        this.codeT5ServiceUrl = codeT5ServiceUrl;
    }

    /**
     * @return minimum frequency threshold for dictionary pattern promotion
     */
    public int getDictionaryFrequencyThreshold() {
        return dictionaryFrequencyThreshold;
    }

    public void setDictionaryFrequencyThreshold(final int dictionaryFrequencyThreshold) {
        this.dictionaryFrequencyThreshold = dictionaryFrequencyThreshold;
    }

    /**
     * @return current experiment run identifier
     */
    public String getExperimentRunId() {
        return experimentRunId;
    }

    public void setExperimentRunId(final String experimentRunId) {
        this.experimentRunId = experimentRunId;
    }

    /**
     * @return directory path for experiment results storage
     */
    public String getExperimentResultsDir() {
        return experimentResultsDir;
    }

    public void setExperimentResultsDir(final String experimentResultsDir) {
        this.experimentResultsDir = experimentResultsDir;
    }

}
