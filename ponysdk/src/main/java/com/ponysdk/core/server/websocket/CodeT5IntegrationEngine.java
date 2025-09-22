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

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * CodeT5 AI Integration Engine for semantic predictions.
 *
 * This engine was extracted from WebSocket.java HTTP and AI integration methods.
 * It handles communication with FastAPI/CodeT5 prediction service and response
 * processing that were added as AI optimization features after the original
 * 439-line implementation.
 *
 * EXTRACTED FUNCTIONALITY:
 * - HTTP communication with FastAPI prediction service
 * - JSON request/response handling for CodeT5 queries
 * - Prediction comparison and validation logic
 * - Error handling and latency measurement for AI calls
 */
public class CodeT5IntegrationEngine {

    private static final Logger log = LoggerFactory.getLogger(CodeT5IntegrationEngine.class);
    private static final Logger PRED = LoggerFactory.getLogger("PredictionLogger");

    private static final String FAST_API_URL = "http://127.0.0.1:8000/generate";
    private static final int CONNECTION_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 10000;

    private final Object httpLock = new Object();

    public void sendPredictionRequest(List<String> instructionsToSend, WebSocket.Listener listener) {
        if (!WebSocketConfiguration.isCodeT5Enabled()) {
            PRED.debug("CodeT5/FastAPI disabled - skipping HTTP call for {} instructions",
                      instructionsToSend != null ? instructionsToSend.size() : 0);
            return;
        }

        if (instructionsToSend == null || instructionsToSend.isEmpty()) {
            PRED.debug("No instructions to send to CodeT5 prediction service");
            return;
        }

        synchronized (httpLock) {
            sendJsonPostRequestUsingHttpURLConnection(instructionsToSend, listener);
        }
    }

    private void sendJsonPostRequestUsingHttpURLConnection(List<String> instructionsToSend, WebSocket.Listener listener) {
        long startTime = System.nanoTime();
        boolean success = false;

        try {
            if (!WebSocketConfiguration.isCodeT5Enabled()) {
                PRED.debug("CodeT5/FastAPI disabled - skipping HTTP call for {} instructions", instructionsToSend.size());
                return;
            }

            if (instructionsToSend.isEmpty()) {
                PRED.debug("No instructions to send to FastAPI");
                return;
            }

            // Serialize the input instructions to create the request body
            String jsonInputString = createJsonPayload(instructionsToSend);

            // Define the URL of your FastAPI server endpoint
            URL url = new URL(FAST_API_URL);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();

            // Set up the connection properties
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json; utf-8");
            con.setRequestProperty("Accept", "application/json");
            con.setDoOutput(true);
            con.setConnectTimeout(CONNECTION_TIMEOUT);
            con.setReadTimeout(READ_TIMEOUT);

            // Send the JSON data to the server
            try (OutputStream os = con.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Read the response from the server
            int responseCode = con.getResponseCode();
            String response = readResponse(con);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                success = true;
                log.info("FastAPI HTTP Response Code: {}, Body: {}", responseCode, response);

                String prediction = extractPredictionFromResponse(response);

                if (prediction != null && !instructionsToSend.isEmpty()) {
                    processPredictionResult(prediction, instructionsToSend);
                }

            } else {
                log.error("FastAPI HTTP Error - Response Code: {}, Body: {}", responseCode, response);
            }

        } catch (Exception e) {
            log.error("Error sending POST request to FastAPI", e);
        } finally {
            // Measure CodeT5/FastAPI request latency
            long latencyNanos = System.nanoTime() - startTime;
            if (listener instanceof LatencyTracker) {
                ((LatencyTracker) listener).onCodeT5Query(success, latencyNanos, "generate");
            }
        }
    }

    private String createJsonPayload(List<String> instructionsToSend) {
        // Create JSON payload for FastAPI service
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{\"inputs\":[");

        for (int i = 0; i < instructionsToSend.size(); i++) {
            if (i > 0) jsonBuilder.append(",");
            jsonBuilder.append("\"").append(escapeJson(instructionsToSend.get(i))).append("\"");
        }

        jsonBuilder.append("]}");
        return jsonBuilder.toString();
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    private String readResponse(HttpURLConnection connection) throws IOException {
        InputStream inputStream = connection.getResponseCode() == HttpURLConnection.HTTP_OK
            ? connection.getInputStream()
            : connection.getErrorStream();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            return response.toString();
        }
    }

    private String extractPredictionFromResponse(String response) {
        try {
            JsonReader jsonReader = Json.createReader(new StringReader(response));
            JsonObject jsonResponse = jsonReader.readObject();

            if (jsonResponse.containsKey("generated_text")) {
                return jsonResponse.getString("generated_text");
            } else {
                PRED.warn("FastAPI response missing 'generated_text' field: {}", response);
                return null;
            }

        } catch (Exception e) {
            log.error("Error parsing FastAPI response: {}", response, e);
            return null;
        }
    }

    private void processPredictionResult(String prediction, List<String> originalInstructions) {
        try {
            String predictedInstruction = prediction.trim();

            log.info("--- Instruction Comparison (FastAPI Response) ---");
            log.info("Predicted (FastAPI): {}", predictedInstruction);
            log.info("Original instructions: {}", originalInstructions);

            // Store prediction for later validation
            // Note: This would typically be handled by SemanticPatternEngine
            PRED.info("Received CodeT5 prediction: '{}'", predictedInstruction);

        } catch (Exception e) {
            log.error("Error processing prediction result", e);
        }
    }

    public boolean isServiceAvailable() {
        try {
            URL url = new URL(FAST_API_URL.replace("/generate", "/health"));
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setConnectTimeout(1000);
            con.setReadTimeout(1000);

            int responseCode = con.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK;

        } catch (Exception e) {
            PRED.debug("CodeT5 FastAPI service not available: {}", e.getMessage());
            return false;
        }
    }

    public CodeT5Stats getStats() {
        return new CodeT5Stats(
            WebSocketConfiguration.isCodeT5Enabled(),
            isServiceAvailable(),
            FAST_API_URL
        );
    }

    public static class CodeT5Stats {
        public final boolean enabled;
        public final boolean serviceAvailable;
        public final String serviceUrl;

        public CodeT5Stats(boolean enabled, boolean serviceAvailable, String serviceUrl) {
            this.enabled = enabled;
            this.serviceAvailable = serviceAvailable;
            this.serviceUrl = serviceUrl;
        }

        @Override
        public String toString() {
            return String.format("CodeT5[enabled=%s, available=%s, url=%s]",
                               enabled, serviceAvailable, serviceUrl);
        }
    }
}