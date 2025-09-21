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

/**
 * Configuration management for WebSocket optimization features.
 *
 * This class centralizes all feature flags and configuration settings that were
 * previously scattered throughout WebSocket.java. Extracted for better organization,
 * testability, and runtime control.
 *
 * Thread Safety: All methods are synchronized for safe concurrent access.
 */
public class WebSocketConfiguration {

    // Feature control flags (thread-safe static state)
    private static boolean dictionaryEnabled = true;        // Dictionary compression enabled by default
    private static boolean trieEnabled = true;             // Widget trie prediction enabled by default
    private static boolean codeT5Enabled = true;           // CodeT5/FastAPI prediction enabled by default

    // Configuration constants
    private static final int BATCH_THRESHOLD = 2;          // Messages per batch for dictionary compression

    /**
     * Check if dictionary compression is enabled.
     * @return true if dictionary compression should be used
     */
    public static synchronized boolean isDictionaryEnabled() {
        return dictionaryEnabled;
    }

    /**
     * Enable or disable dictionary compression globally.
     * @param enabled true to enable dictionary compression
     */
    public static synchronized void setDictionaryEnabled(boolean enabled) {
        dictionaryEnabled = enabled;
    }

    /**
     * Check if widget trie prediction is enabled.
     * @return true if trie prediction should be used
     */
    public static synchronized boolean isTrieEnabled() {
        return trieEnabled;
    }

    /**
     * Enable or disable widget trie prediction globally.
     * @param enabled true to enable trie prediction
     */
    public static synchronized void setTrieEnabled(boolean enabled) {
        trieEnabled = enabled;
    }

    /**
     * Check if CodeT5/FastAPI prediction is enabled.
     * @return true if CodeT5 prediction should be used
     */
    public static synchronized boolean isCodeT5Enabled() {
        return codeT5Enabled;
    }

    /**
     * Enable or disable CodeT5/FastAPI prediction globally.
     * @param enabled true to enable CodeT5 prediction
     */
    public static synchronized void setCodeT5Enabled(boolean enabled) {
        codeT5Enabled = enabled;
    }

    /**
     * Get the batch threshold for dictionary compression.
     * @return number of messages required before attempting compression
     */
    public static int getBatchThreshold() {
        return BATCH_THRESHOLD;
    }

    /**
     * Enable all optimization features.
     * Convenience method for testing and UI controls.
     */
    public static synchronized void enableAllOptimizations() {
        dictionaryEnabled = true;
        trieEnabled = true;
        codeT5Enabled = true;
    }

    /**
     * Disable all optimization features.
     * Convenience method for testing and debugging.
     */
    public static synchronized void disableAllOptimizations() {
        dictionaryEnabled = false;
        trieEnabled = false;
        codeT5Enabled = false;
    }

    /**
     * Get current optimization status summary.
     * @return string describing current feature states
     */
    public static synchronized String getOptimizationStatus() {
        return String.format("Dictionary: %s, Trie: %s, CodeT5: %s",
                           dictionaryEnabled ? "ON" : "OFF",
                           trieEnabled ? "ON" : "OFF",
                           codeT5Enabled ? "ON" : "OFF");
    }

    /**
     * Reset all settings to default values.
     * Useful for testing and initialization.
     */
    public static synchronized void resetToDefaults() {
        dictionaryEnabled = true;
        trieEnabled = true;
        codeT5Enabled = true;
    }
}