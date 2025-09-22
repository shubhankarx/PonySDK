import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Simple table generator for MetricsExporter JSON files.
 * Reads JSON exports from MetricsExporter and creates comparison tables.
 * 
 * Usage from project root:
 *   javac LatencyTableGenerator.java
 *   java LatencyTableGenerator results-dir/
 *   java LatencyTableGenerator results-dir/ --limit 10
 *   java LatencyTableGenerator results-dir/ --latest 5 --config dictionary_only
 *   java LatencyTableGenerator results-dir/run_123_dictionary_only_export_1.json
 */
public class LatencyTableGenerator {
    
    /**
     * Extract JSON value (simple parser for the specific MetricsExporter format)
     */
    private static String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) return null;
        
        int valueStart = keyIndex + searchKey.length();
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }
        
        char firstChar = json.charAt(valueStart);
        if (firstChar == '"') {
            // String value
            int valueEnd = json.indexOf('"', valueStart + 1);
            return json.substring(valueStart + 1, valueEnd);
        } else if (firstChar == 't' || firstChar == 'f') {
            // Boolean value
            int valueEnd = valueStart;
            while (valueEnd < json.length() && Character.isLetter(json.charAt(valueEnd))) {
                valueEnd++;
            }
            return json.substring(valueStart, valueEnd);
        } else {
            // Number value
            int valueEnd = valueStart;
            while (valueEnd < json.length() && 
                   (Character.isDigit(json.charAt(valueEnd)) || 
                    json.charAt(valueEnd) == '.' || 
                    json.charAt(valueEnd) == '-' ||
                    json.charAt(valueEnd) == 'E' ||
                    json.charAt(valueEnd) == 'e')) {
                valueEnd++;
            }
            if (valueEnd > valueStart && 
                (json.charAt(valueEnd) == ',' || json.charAt(valueEnd) == '}' || 
                 json.charAt(valueEnd) == ']' || Character.isWhitespace(json.charAt(valueEnd)))) {
                return json.substring(valueStart, valueEnd);
            }
        }
        return null;
    }
    
    /**
     * Extract nested JSON value
     */
    private static String extractNestedValue(String json, String... keys) {
        String current = json;
        for (int i = 0; i < keys.length - 1; i++) {
            String key = keys[i];
            String searchKey = "\"" + key + "\":";
            int keyIndex = current.indexOf(searchKey);
            if (keyIndex == -1) return null;
            
            // Find the object start
            int objectStart = current.indexOf('{', keyIndex);
            if (objectStart == -1) return null;
            
            // Find matching closing brace
            int braceCount = 1;
            int pos = objectStart + 1;
            while (pos < current.length() && braceCount > 0) {
                if (current.charAt(pos) == '{') braceCount++;
                else if (current.charAt(pos) == '}') braceCount--;
                pos++;
            }
            
            current = current.substring(objectStart, pos);
        }
        
        return extractJsonValue(current, keys[keys.length - 1]);
    }
    
    /**
     * Session data loaded from MetricsExporter JSON
     */
    static class SessionData {
        String runId;
        String configurationType;
        String timestamp;
        
        // Latency metrics
        double avgLatencyMs = 0.0;
        double p95LatencyMs = 0.0;
        double minLatencyMs = 0.0;
        double maxLatencyMs = 0.0;
        
        // End-to-end metrics
        long endToEndCount = 0;
        double avgEndToEndMs = 0.0;
        double minEndToEndMs = 0.0;
        double maxEndToEndMs = 0.0;
        
        // Dictionary metrics
        boolean dictionaryEnabled = false;
        long dictionaryHits = 0;
        long dictionaryMisses = 0;
        double dictionaryHitRate = 0.0;
        
        // Trie metrics
        boolean trieEnabled = false;
        long trieQueries = 0;
        long trieHits = 0;
        double trieHitRate = 0.0;
        double trieAvgLatencyMs = 0.0;
        
        // CodeT5 metrics
        boolean codet5Enabled = false;
        long codet5Queries = 0;
        long codet5Success = 0;
        long codet5Errors = 0;
        double codet5SuccessRate = 0.0;
        double codet5AvgLatencyMs = 0.0;
        
        // Throughput metrics
        double messagesPerSecond = 0.0;
        long totalTransmissions = 0;
        long totalBytes = 0;
        double avgMessageSizeBytes = 0.0;
    }
    
    /**
     * Load session data from MetricsExporter JSON file
     */
    public static SessionData loadSessionData(String filePath) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(filePath)));
        SessionData session = new SessionData();
        
        // Basic metadata
        session.runId = extractNestedValue(content, "run_metadata", "run_id");
        session.configurationType = extractNestedValue(content, "run_metadata", "configuration_type");
        session.timestamp = extractNestedValue(content, "run_metadata", "timestamp");
        
        if (session.runId == null) session.runId = "unknown";
        if (session.configurationType == null) session.configurationType = "unknown";
        if (session.timestamp == null) session.timestamp = "unknown";
        
        // Latency metrics from L1_end_to_end
        String avgLatency = extractNestedValue(content, "latency_boundaries", "L1_end_to_end", "avg_ms");
        String p95Latency = extractNestedValue(content, "latency_boundaries", "L1_end_to_end", "p95_ms");
        String minLatency = extractNestedValue(content, "latency_boundaries", "L1_end_to_end", "min_ms");
        String maxLatency = extractNestedValue(content, "latency_boundaries", "L1_end_to_end", "max_ms");
        
        try {
            if (avgLatency != null) session.avgLatencyMs = Double.parseDouble(avgLatency);
            if (p95Latency != null) session.p95LatencyMs = Double.parseDouble(p95Latency);
            if (minLatency != null) session.minLatencyMs = Double.parseDouble(minLatency);
            if (maxLatency != null) session.maxLatencyMs = Double.parseDouble(maxLatency);
        } catch (NumberFormatException e) {
            // Use defaults
        }
        
        // End-to-end metrics
        String endToEndCountStr = extractNestedValue(content, "client_latency_details", "roundtrip_measurements", "count");
        String avgEndToEndStr = extractNestedValue(content, "client_latency_details", "roundtrip_measurements", "avg_ms");
        String minEndToEndStr = extractNestedValue(content, "client_latency_details", "roundtrip_measurements", "min_ms");
        String maxEndToEndStr = extractNestedValue(content, "client_latency_details", "roundtrip_measurements", "max_ms");
        
        try {
            if (endToEndCountStr != null) session.endToEndCount = Long.parseLong(endToEndCountStr);
            if (avgEndToEndStr != null) session.avgEndToEndMs = Double.parseDouble(avgEndToEndStr);
            if (minEndToEndStr != null) session.minEndToEndMs = Double.parseDouble(minEndToEndStr);
            if (maxEndToEndStr != null) session.maxEndToEndMs = Double.parseDouble(maxEndToEndStr);
        } catch (NumberFormatException e) {
            // Use defaults
        }
        
        // Dictionary metrics
        String dictEnabled = extractNestedValue(content, "feature_metrics", "dictionary", "enabled");
        String dictHits = extractNestedValue(content, "feature_metrics", "dictionary", "hit_count");
        String dictMisses = extractNestedValue(content, "feature_metrics", "dictionary", "miss_count");
        String dictHitRate = extractNestedValue(content, "feature_metrics", "dictionary", "hit_rate_percent");
        
        try {
            session.dictionaryEnabled = "true".equals(dictEnabled);
            if (dictHits != null) session.dictionaryHits = Long.parseLong(dictHits);
            if (dictMisses != null) session.dictionaryMisses = Long.parseLong(dictMisses);
            if (dictHitRate != null) session.dictionaryHitRate = Double.parseDouble(dictHitRate);
        } catch (NumberFormatException e) {
            // Use defaults
        }
        
        // Trie metrics
        String trieEnabled = extractNestedValue(content, "feature_metrics", "trie", "enabled");
        String trieQueries = extractNestedValue(content, "feature_metrics", "trie", "query_count");
        String trieHits = extractNestedValue(content, "feature_metrics", "trie", "hit_count");
        String trieHitRate = extractNestedValue(content, "feature_metrics", "trie", "hit_rate_percent");
        String trieAvgLatency = extractNestedValue(content, "feature_metrics", "trie", "avg_query_latency_ms");
        
        try {
            session.trieEnabled = "true".equals(trieEnabled);
            if (trieQueries != null) session.trieQueries = Long.parseLong(trieQueries);
            if (trieHits != null) session.trieHits = Long.parseLong(trieHits);
            if (trieHitRate != null) session.trieHitRate = Double.parseDouble(trieHitRate);
            if (trieAvgLatency != null) session.trieAvgLatencyMs = Double.parseDouble(trieAvgLatency);
        } catch (NumberFormatException e) {
            // Use defaults
        }
        
        // CodeT5 metrics
        String codet5Enabled = extractNestedValue(content, "feature_metrics", "codet5", "enabled");
        String codet5Queries = extractNestedValue(content, "feature_metrics", "codet5", "query_count");
        String codet5Success = extractNestedValue(content, "feature_metrics", "codet5", "success_count");
        String codet5Errors = extractNestedValue(content, "feature_metrics", "codet5", "error_count");
        String codet5SuccessRate = extractNestedValue(content, "feature_metrics", "codet5", "success_rate_percent");
        String codet5AvgLatency = extractNestedValue(content, "feature_metrics", "codet5", "avg_latency_ms");
        
        try {
            session.codet5Enabled = "true".equals(codet5Enabled);
            if (codet5Queries != null) session.codet5Queries = Long.parseLong(codet5Queries);
            if (codet5Success != null) session.codet5Success = Long.parseLong(codet5Success);
            if (codet5Errors != null) session.codet5Errors = Long.parseLong(codet5Errors);
            if (codet5SuccessRate != null) session.codet5SuccessRate = Double.parseDouble(codet5SuccessRate);
            if (codet5AvgLatency != null) session.codet5AvgLatencyMs = Double.parseDouble(codet5AvgLatency);
        } catch (NumberFormatException e) {
            // Use defaults
        }
        
        // Throughput metrics
        String messagesPerSec = extractNestedValue(content, "throughput_metrics", "messages_per_second");
        String totalTrans = extractNestedValue(content, "throughput_metrics", "total_transmissions");
        String totalBytesStr = extractNestedValue(content, "throughput_metrics", "total_bytes_transmitted");
        String avgMsgSize = extractNestedValue(content, "throughput_metrics", "avg_message_size_bytes");
        
        try {
            if (messagesPerSec != null) session.messagesPerSecond = Double.parseDouble(messagesPerSec);
            if (totalTrans != null) session.totalTransmissions = Long.parseLong(totalTrans);
            if (totalBytesStr != null) session.totalBytes = Long.parseLong(totalBytesStr);
            if (avgMsgSize != null) session.avgMessageSizeBytes = Double.parseDouble(avgMsgSize);
        } catch (NumberFormatException e) {
            // Use defaults
        }
        
        return session;
    }
    
    /**
     * Generate individual session table
     */
    public static void generateSessionTable(SessionData session) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("                PonySDK LATENCY SESSION REPORT");
        System.out.println("=".repeat(80));
        System.out.printf("Run ID          : %s%n", session.runId);
        System.out.printf("Configuration   : %s%n", session.configurationType);
        System.out.printf("Timestamp       : %s%n", session.timestamp);
        System.out.println();
        
        // Configuration summary
        System.out.println("OPTIMIZATION CONFIGURATION");
        System.out.println("-".repeat(40));
        System.out.printf("Dictionary      : %s%n", session.dictionaryEnabled ? "ENABLED" : "DISABLED");
        System.out.printf("Trie            : %s%n", session.trieEnabled ? "ENABLED" : "DISABLED");
        System.out.printf("CodeT5          : %s%n", session.codet5Enabled ? "ENABLED" : "DISABLED");
        System.out.println();
        
        // Latency summary
        System.out.println("LATENCY METRICS");
        System.out.println("-".repeat(40));
        System.out.printf("Average Latency : %.2f ms%n", session.avgLatencyMs);
        System.out.printf("P95 Latency     : %.2f ms%n", session.p95LatencyMs);
        System.out.printf("Min Latency     : %.2f ms%n", session.minLatencyMs);
        System.out.printf("Max Latency     : %.2f ms%n", session.maxLatencyMs);
        
        if (session.endToEndCount > 0) {
            System.out.println();
            System.out.println("END-TO-END LATENCY (Client Roundtrip)");
            System.out.println("-".repeat(40));
            System.out.printf("Measurements    : %d%n", session.endToEndCount);
            System.out.printf("Average         : %.2f ms%n", session.avgEndToEndMs);
            System.out.printf("Min             : %.2f ms%n", session.minEndToEndMs);
            System.out.printf("Max             : %.2f ms%n", session.maxEndToEndMs);
        }
        System.out.println();
        
        // Optimization effectiveness
        if (session.dictionaryEnabled && (session.dictionaryHits + session.dictionaryMisses) > 0) {
            System.out.println("DICTIONARY COMPRESSION");
            System.out.println("-".repeat(40));
            System.out.printf("Hit Count       : %d%n", session.dictionaryHits);
            System.out.printf("Miss Count      : %d%n", session.dictionaryMisses);
            System.out.printf("Hit Rate        : %.1f%%%n", session.dictionaryHitRate);
            System.out.println();
        }
        
        if (session.trieEnabled && session.trieQueries > 0) {
            System.out.println("TRIE PREDICTION");
            System.out.println("-".repeat(40));
            System.out.printf("Queries         : %d%n", session.trieQueries);
            System.out.printf("Hits            : %d%n", session.trieHits);
            System.out.printf("Hit Rate        : %.1f%%%n", session.trieHitRate);
            System.out.printf("Avg Latency     : %.2f ms%n", session.trieAvgLatencyMs);
            System.out.println();
        }
        
        if (session.codet5Enabled && session.codet5Queries > 0) {
            System.out.println("CODET5 SEMANTIC ANALYSIS");
            System.out.println("-".repeat(40));
            System.out.printf("Queries         : %d%n", session.codet5Queries);
            System.out.printf("Success         : %d%n", session.codet5Success);
            System.out.printf("Errors          : %d%n", session.codet5Errors);
            System.out.printf("Success Rate    : %.1f%%%n", session.codet5SuccessRate);
            System.out.printf("Avg Latency     : %.2f ms%n", session.codet5AvgLatencyMs);
            System.out.println();
        }
        
        // Throughput
        System.out.println("THROUGHPUT METRICS");
        System.out.println("-".repeat(40));
        System.out.printf("Messages/sec    : %.1f%n", session.messagesPerSecond);
        System.out.printf("Total Messages  : %d%n", session.totalTransmissions);
        System.out.printf("Total Bytes     : %,d%n", session.totalBytes);
        System.out.printf("Avg Message Size: %.1f bytes%n", session.avgMessageSizeBytes);
        
        System.out.println("=".repeat(80));
    }
    
    /**
     * Consolidation strategies for handling duplicate configurations
     */
    public enum ConsolidationStrategy {
        AVERAGE, LATEST, BEST
    }
    
    /**
     * Consolidate sessions with same configuration type using specified strategy
     * Time complexity: O(n log n) where n = number of sessions
     * Space complexity: O(n)
     */
    public static List<SessionData> consolidateDuplicateConfigurations(List<SessionData> sessions, ConsolidationStrategy strategy) {
        if (sessions == null || sessions.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Group by configuration type - O(n)
        Map<String, List<SessionData>> configGroups = new HashMap<>();
        for (SessionData session : sessions) {
            configGroups.computeIfAbsent(session.configurationType, k -> new ArrayList<>()).add(session);
        }
        
        List<SessionData> consolidated = new ArrayList<>();
        
        // Process each configuration group - O(n log n) total
        for (Map.Entry<String, List<SessionData>> entry : configGroups.entrySet()) {
            String configType = entry.getKey();
            List<SessionData> groupSessions = entry.getValue();
            
            if (groupSessions.size() == 1) {
                // Single session - no consolidation needed
                consolidated.add(groupSessions.get(0));
            } else {
                // Multiple sessions - apply consolidation strategy
                SessionData consolidatedSession = applyConsolidationStrategy(groupSessions, strategy);
                if (consolidatedSession != null) {
                    consolidated.add(consolidatedSession);
                }
                
                System.out.printf("⚠️  Consolidated %d sessions for '%s' using %s strategy%n", 
                                 groupSessions.size(), configType, strategy.name().toLowerCase());
            }
        }
        
        return consolidated;
    }
    
    /**
     * Apply consolidation strategy to a group of sessions with same configuration
     * Time complexity: O(n log n) for sorting, O(n) for averaging
     */
    private static SessionData applyConsolidationStrategy(List<SessionData> sessions, ConsolidationStrategy strategy) {
        if (sessions == null || sessions.isEmpty()) return null;
        
        switch (strategy) {
            case LATEST:
                // Find session with most recent timestamp - O(n)
                return sessions.stream()
                    .filter(s -> s.timestamp != null)
                    .max(Comparator.comparing(s -> s.timestamp))
                    .orElse(sessions.get(0));
                    
            case BEST:
                // Find session with lowest average latency - O(n)
                return sessions.stream()
                    .min(Comparator.comparingDouble(s -> s.avgLatencyMs))
                    .orElse(sessions.get(0));
                    
            case AVERAGE:
            default:
                // Create mathematically averaged session - O(n)
                return computeAveragedSession(sessions);
        }
    }
    
    /**
     * Compute averaged metrics across multiple sessions
     * Time complexity: O(n), Space complexity: O(1)
     */
    private static SessionData computeAveragedSession(List<SessionData> sessions) {
        if (sessions == null || sessions.isEmpty()) return null;
        
        int count = sessions.size();
        SessionData averaged = new SessionData();
        SessionData first = sessions.get(0);
        
        // Metadata from first session + aggregation info
        averaged.runId = first.runId + "_avg" + count;
        averaged.configurationType = first.configurationType;
        averaged.timestamp = first.timestamp + "_averaged";
        
        // Configuration flags (should be same across all sessions)
        averaged.dictionaryEnabled = first.dictionaryEnabled;
        averaged.trieEnabled = first.trieEnabled;
        averaged.codet5Enabled = first.codet5Enabled;
        
        // Compute averages using streams for precision - O(n)
        averaged.avgLatencyMs = sessions.stream().mapToDouble(s -> s.avgLatencyMs).average().orElse(0.0);
        averaged.p95LatencyMs = sessions.stream().mapToDouble(s -> s.p95LatencyMs).average().orElse(0.0);
        averaged.avgEndToEndMs = sessions.stream().mapToDouble(s -> s.avgEndToEndMs).average().orElse(0.0);
        averaged.messagesPerSecond = sessions.stream().mapToDouble(s -> s.messagesPerSecond).average().orElse(0.0);
        averaged.avgMessageSizeBytes = sessions.stream().mapToDouble(s -> s.avgMessageSizeBytes).average().orElse(0.0);
        
        // Min/Max across all sessions
        averaged.minLatencyMs = sessions.stream().mapToDouble(s -> s.minLatencyMs).min().orElse(0.0);
        averaged.maxLatencyMs = sessions.stream().mapToDouble(s -> s.maxLatencyMs).max().orElse(0.0);
        averaged.minEndToEndMs = sessions.stream().mapToDouble(s -> s.minEndToEndMs).min().orElse(0.0);
        averaged.maxEndToEndMs = sessions.stream().mapToDouble(s -> s.maxEndToEndMs).max().orElse(0.0);
        
        // Sum counts for aggregated totals
        averaged.endToEndCount = sessions.stream().mapToLong(s -> s.endToEndCount).sum();
        averaged.dictionaryHits = sessions.stream().mapToLong(s -> s.dictionaryHits).sum();
        averaged.dictionaryMisses = sessions.stream().mapToLong(s -> s.dictionaryMisses).sum();
        averaged.trieQueries = sessions.stream().mapToLong(s -> s.trieQueries).sum();
        averaged.trieHits = sessions.stream().mapToLong(s -> s.trieHits).sum();
        averaged.codet5Queries = sessions.stream().mapToLong(s -> s.codet5Queries).sum();
        averaged.codet5Success = sessions.stream().mapToLong(s -> s.codet5Success).sum();
        averaged.codet5Errors = sessions.stream().mapToLong(s -> s.codet5Errors).sum();
        averaged.totalTransmissions = sessions.stream().mapToLong(s -> s.totalTransmissions).sum();
        averaged.totalBytes = sessions.stream().mapToLong(s -> s.totalBytes).sum();
        
        // Recalculate derived rates from aggregated data
        long totalDictLookups = averaged.dictionaryHits + averaged.dictionaryMisses;
        averaged.dictionaryHitRate = totalDictLookups > 0 ? (averaged.dictionaryHits * 100.0) / totalDictLookups : 0.0;
        averaged.trieHitRate = averaged.trieQueries > 0 ? (averaged.trieHits * 100.0) / averaged.trieQueries : 0.0;
        averaged.codet5SuccessRate = averaged.codet5Queries > 0 ? (averaged.codet5Success * 100.0) / averaged.codet5Queries : 0.0;
        
        return averaged;
    }
    
    /**
     * Display session consolidation summary
     */
    private static void displayConsolidationSummary(List<SessionData> originalSessions, List<SessionData> consolidatedSessions) {
        if (originalSessions.size() == consolidatedSessions.size()) {
            return; // No consolidation occurred
        }
        
        System.out.printf("📊 Consolidated %d sessions into %d unique configurations%n%n", 
                         originalSessions.size(), consolidatedSessions.size());
        
        // Show count by configuration type
        Map<String, Long> configCounts = originalSessions.stream()
            .collect(Collectors.groupingBy(s -> s.configurationType, Collectors.counting()));
        
        System.out.println("📈 RAW SESSION COUNT BY CONFIGURATION:");
        configCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .forEach(entry -> System.out.printf("  %s: %d session(s)%n", entry.getKey(), entry.getValue()));
        System.out.println();
    }
    
    /**
     * Generate comparison table for multiple sessions with consolidation
     */
    public static void generateComparisonTable(List<SessionData> sessions) {
        generateComparisonTableWithStrategy(sessions, ConsolidationStrategy.AVERAGE);
    }
    
    /**
     * Generate comparison table with specified consolidation strategy
     */
    public static void generateComparisonTableWithStrategy(List<SessionData> sessions, ConsolidationStrategy strategy) {
        generateComparisonTableWithStrategy(sessions, strategy, null);
    }
    
    public static void generateComparisonTableWithStrategy(List<SessionData> sessions, ConsolidationStrategy strategy, Config config) {
        // Apply consolidation strategy to handle duplicate configurations
        List<SessionData> consolidatedSessions = consolidateDuplicateConfigurations(sessions, strategy);
        
        // Display consolidation summary
        displayConsolidationSummary(sessions, consolidatedSessions);
        
        // Generate comparison table
        System.out.println("\n" + "=".repeat(130));
        System.out.printf("                   OPTIMIZATION COMPARISON TABLE (%s strategy)%n", strategy.name());
        System.out.println("=".repeat(130));
        
        System.out.printf("%-15s | %-10s | %-10s | %-10s | %-12s | %-12s | %-12s | %-15s%n",
                         "Configuration", "Avg (ms)", "P95 (ms)", "Min (ms)", "Messages/sec", "Dict Hit%", "Trie Hit%", "CodeT5 Success%");
        System.out.println("-".repeat(15) + "|" + "-".repeat(12) + "|" + "-".repeat(12) + "|" + 
                         "-".repeat(12) + "|" + "-".repeat(14) + "|" + "-".repeat(14) + "|" + 
                         "-".repeat(14) + "|" + "-".repeat(16));
        
        // Sort by average latency for comparison - O(n log n)
        consolidatedSessions.stream()
            .sorted(Comparator.comparingDouble(s -> s.avgLatencyMs))
            .forEach(session -> {
                System.out.printf("%-15s | %10.2f | %10.2f | %10.2f | %12.1f | %11.1f%% | %11.1f%% | %14.1f%%%n",
                                 session.configurationType,
                                 session.avgLatencyMs,
                                 session.p95LatencyMs,
                                 session.minLatencyMs,
                                 session.messagesPerSecond,
                                 session.dictionaryHitRate,
                                 session.trieHitRate,
                                 session.codet5SuccessRate);
            });
        
        System.out.println("=".repeat(130));
        
        // Performance improvement analysis
        generatePerformanceAnalysis(consolidatedSessions);
        
        // Save tables if requested (pass config for access to save options)
        if (config != null && config.saveDir != null) {
            saveComparisonTables(consolidatedSessions, strategy, config);
        }
    }
    
    /**
     * Generate performance improvement analysis
     */
    private static void generatePerformanceAnalysis(List<SessionData> sessions) {
        // Find baseline (ALL_OFF)
        Optional<SessionData> baselineOpt = sessions.stream()
            .filter(s -> "ALL_OFF".equals(s.configurationType))
            .findFirst();
        
        if (!baselineOpt.isPresent()) {
            System.out.println("\nNo baseline (ALL_OFF) configuration found for improvement analysis.");
            return;
        }
        
        SessionData baseline = baselineOpt.get();
        
        System.out.println("\nPERFORMANCE IMPROVEMENT vs BASELINE (ALL_OFF)");
        System.out.println("-".repeat(70));
        System.out.printf("%-15s | %-12s | %-15s | %-15s%n",
                         "Configuration", "Avg Latency", "Improvement", "Speedup Factor");
        System.out.println("-".repeat(15) + "|" + "-".repeat(14) + "|" + "-".repeat(17) + "|" + "-".repeat(16));
        
        for (SessionData session : sessions) {
            if (!"ALL_OFF".equals(session.configurationType)) {
                double improvement = ((baseline.avgLatencyMs - session.avgLatencyMs) / baseline.avgLatencyMs) * 100;
                double speedupFactor = baseline.avgLatencyMs / session.avgLatencyMs;
                
                System.out.printf("%-15s | %11.2fms | %14.1f%% | %14.2fx%n",
                                 session.configurationType,
                                 session.avgLatencyMs,
                                 improvement,
                                 speedupFactor);
            }
        }
        
        System.out.printf("%-15s | %11.2fms | %14s | %14s%n",
                         "ALL_OFF (base)", baseline.avgLatencyMs, "0.0%", "1.00x");
        System.out.println("-".repeat(70));
    }
    
    /**
     * Save comparison tables in multiple formats with scientific naming
     */
    private static void saveComparisonTables(List<SessionData> consolidatedSessions, ConsolidationStrategy strategy, Config config) {
        // Generate scientific timestamp: YYYY-MM-DD_HH-MM-SS
        String timestamp = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String baseFilename = "latency_comparison_" + strategy.name().toLowerCase() + "_" + timestamp;
        
        try {
            java.nio.file.Path saveDir = java.nio.file.Paths.get(config.saveDir);
            if (!java.nio.file.Files.exists(saveDir)) {
                java.nio.file.Files.createDirectories(saveDir);
            }
            
            for (String format : config.saveFormats) {
                String filename = baseFilename + "." + format;
                java.nio.file.Path filePath = saveDir.resolve(filename);
                
                switch (format) {
                    case "csv":
                        saveAsCsv(consolidatedSessions, filePath, strategy);
                        break;
                    case "json":
                        saveAsJson(consolidatedSessions, filePath, strategy);
                        break;
                    case "md":
                        saveAsMarkdown(consolidatedSessions, filePath, strategy);
                        break;
                    case "html":
                        saveAsHtml(consolidatedSessions, filePath, strategy);
                        break;
                    default:
                        System.err.println("Unknown format: " + format);
                        continue;
                }
                
                if (config.verbose) {
                    System.out.printf("💾 Saved %s table: %s%n", format.toUpperCase(), filePath.getFileName());
                }
            }
            
            System.out.printf("\n📁 Tables saved to: %s%n", saveDir.toAbsolutePath());
            System.out.printf("📅 Timestamp format: %s%n", timestamp);
            
        } catch (Exception e) {
            System.err.printf("Error saving tables: %s%n", e.getMessage());
            if (config.verbose) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Save as CSV format (Excel-friendly)
     */
    private static void saveAsCsv(List<SessionData> sessions, java.nio.file.Path filePath, ConsolidationStrategy strategy) throws java.io.IOException {
        StringBuilder csv = new StringBuilder();
        
        // Header with metadata
        csv.append("# PonySDK WebSocket Optimization Comparison\n");
        csv.append("# Generated: ").append(java.time.LocalDateTime.now()).append("\n");
        csv.append("# Strategy: ").append(strategy.name()).append("\n");
        csv.append("# Sessions: ").append(sessions.size()).append("\n\n");
        
        // CSV Header
        csv.append("Configuration,Avg_Latency_ms,P95_Latency_ms,Min_Latency_ms,Max_Latency_ms,Messages_per_sec,Dict_Hit_Rate_%,Trie_Hit_Rate_%,CodeT5_Success_Rate_%,Total_Messages,Total_Bytes,Avg_Message_Size_bytes\n");
        
        // Data rows
        sessions.stream()
            .sorted(java.util.Comparator.comparingDouble(s -> s.avgLatencyMs))
            .forEach(session -> {
                csv.append(String.format("%s,%.3f,%.3f,%.3f,%.3f,%.1f,%.1f,%.1f,%.1f,%d,%d,%.1f\n",
                    session.configurationType,
                    session.avgLatencyMs,
                    session.p95LatencyMs,
                    session.minLatencyMs,
                    session.maxLatencyMs,
                    session.messagesPerSecond,
                    session.dictionaryHitRate,
                    session.trieHitRate,
                    session.codet5SuccessRate,
                    session.totalTransmissions,
                    session.totalBytes,
                    session.avgMessageSizeBytes
                ));
            });
            
        java.nio.file.Files.write(filePath, csv.toString().getBytes("UTF-8"));
    }
    
    /**
     * Save as JSON format (programmatic use)
     */
    private static void saveAsJson(List<SessionData> sessions, java.nio.file.Path filePath, ConsolidationStrategy strategy) throws java.io.IOException {
        StringBuilder json = new StringBuilder();
        
        json.append("{\n");
        json.append("  \"metadata\": {\n");
        json.append("    \"generated_at\": \"").append(java.time.Instant.now()).append("\",\n");
        json.append("    \"consolidation_strategy\": \"").append(strategy.name()).append("\",\n");
        json.append("    \"session_count\": ").append(sessions.size()).append(",\n");
        json.append("    \"tool_version\": \"LatencyTableGenerator_v1.0\"\n");
        json.append("  },\n");
        json.append("  \"configurations\": [\n");
        
        boolean first = true;
        for (SessionData session : sessions.stream()
                .sorted(java.util.Comparator.comparingDouble(s -> s.avgLatencyMs))
                .collect(java.util.stream.Collectors.toList())) {
            if (!first) json.append(",\n");
            first = false;
            
            json.append("    {\n");
            json.append("      \"configuration\": \"").append(session.configurationType).append("\",\n");
            json.append("      \"latency\": {\n");
            json.append("        \"avg_ms\": ").append(session.avgLatencyMs).append(",\n");
            json.append("        \"p95_ms\": ").append(session.p95LatencyMs).append(",\n");
            json.append("        \"min_ms\": ").append(session.minLatencyMs).append(",\n");
            json.append("        \"max_ms\": ").append(session.maxLatencyMs).append("\n");
            json.append("      },\n");
            json.append("      \"performance\": {\n");
            json.append("        \"messages_per_second\": ").append(session.messagesPerSecond).append(",\n");
            json.append("        \"dictionary_hit_rate_percent\": ").append(session.dictionaryHitRate).append(",\n");
            json.append("        \"trie_hit_rate_percent\": ").append(session.trieHitRate).append(",\n");
            json.append("        \"codet5_success_rate_percent\": ").append(session.codet5SuccessRate).append("\n");
            json.append("      },\n");
            json.append("      \"volume\": {\n");
            json.append("        \"total_messages\": ").append(session.totalTransmissions).append(",\n");
            json.append("        \"total_bytes\": ").append(session.totalBytes).append(",\n");
            json.append("        \"avg_message_size_bytes\": ").append(session.avgMessageSizeBytes).append("\n");
            json.append("      }\n");
            json.append("    }");
        }
        
        json.append("\n  ]\n");
        json.append("}\n");
        
        java.nio.file.Files.write(filePath, json.toString().getBytes("UTF-8"));
    }
    
    /**
     * Save as Markdown format (documentation)
     */
    private static void saveAsMarkdown(List<SessionData> sessions, java.nio.file.Path filePath, ConsolidationStrategy strategy) throws java.io.IOException {
        StringBuilder md = new StringBuilder();
        
        md.append("# PonySDK WebSocket Optimization Comparison\n\n");
        md.append("**Generated:** ").append(java.time.LocalDateTime.now()).append("\n");
        md.append("**Strategy:** ").append(strategy.name()).append("\n");
        md.append("**Configurations:** ").append(sessions.size()).append("\n\n");
        
        md.append("## Comparison Table\n\n");
        md.append("| Configuration | Avg Latency (ms) | P95 Latency (ms) | Messages/sec | Dict Hit% | Trie Hit% | CodeT5 Success% |\n");
        md.append("|---------------|------------------|------------------|--------------|-----------|-----------|-----------------|\n");
        
        sessions.stream()
            .sorted(java.util.Comparator.comparingDouble(s -> s.avgLatencyMs))
            .forEach(session -> {
                md.append(String.format("| %s | %.2f | %.2f | %.1f | %.1f%% | %.1f%% | %.1f%% |\n",
                    session.configurationType,
                    session.avgLatencyMs,
                    session.p95LatencyMs,
                    session.messagesPerSecond,
                    session.dictionaryHitRate,
                    session.trieHitRate,
                    session.codet5SuccessRate
                ));
            });
            
        md.append("\n## Performance Analysis\n\n");
        
        // Find best performer for each metric
        SessionData fastestAvg = sessions.stream().min(java.util.Comparator.comparingDouble(s -> s.avgLatencyMs)).orElse(null);
        SessionData bestThroughput = sessions.stream().max(java.util.Comparator.comparingDouble(s -> s.messagesPerSecond)).orElse(null);
        
        if (fastestAvg != null) {
            md.append("- **Fastest Average Latency:** ").append(fastestAvg.configurationType)
              .append(" (").append(String.format("%.2f", fastestAvg.avgLatencyMs)).append(" ms)\n");
        }
        if (bestThroughput != null) {
            md.append("- **Best Throughput:** ").append(bestThroughput.configurationType)
              .append(" (").append(String.format("%.1f", bestThroughput.messagesPerSecond)).append(" msg/sec)\n");
        }
        
        md.append("\n---\n");
        md.append("*Generated by LatencyTableGenerator*\n");
        
        java.nio.file.Files.write(filePath, md.toString().getBytes("UTF-8"));
    }
    
    /**
     * Parse consolidation strategy from string (case-insensitive)
     * Time complexity: O(1), Space complexity: O(1)
     */
    private static ConsolidationStrategy parseConsolidationStrategy(String strategyStr) {
        if (strategyStr == null) return ConsolidationStrategy.AVERAGE;
        
        switch (strategyStr.toLowerCase().trim()) {
            case "latest": return ConsolidationStrategy.LATEST;
            case "best": return ConsolidationStrategy.BEST;
            case "average": 
            default: return ConsolidationStrategy.AVERAGE;
        }
    }
    
    /**
     * Configuration class for command line arguments (competitive programming style)
     */
    static class Config {
        String inputPath;
        int maxFilesPerConfig = Integer.MAX_VALUE; // --limit N
        int latestFiles = -1; // --latest N (overrides limit)
        String configFilter = null; // --config dictionary_only
        boolean verbose = false; // --verbose
        String saveDir = null; // --save ./output/
        String[] saveFormats = {"csv", "json", "md"}; // --save-formats csv,json,md
        
        static Config parseArgs(String[] args) {
            Config config = new Config();
            
            if (args.length == 0) {
                printUsage();
                return null;
            }
            
            config.inputPath = args[0];
            
            // Parse optional flags
            for (int i = 1; i < args.length; i++) {
                switch (args[i]) {
                    case "--limit":
                        if (i + 1 < args.length) {
                            config.maxFilesPerConfig = Integer.parseInt(args[++i]);
                        }
                        break;
                    case "--latest":
                        if (i + 1 < args.length) {
                            config.latestFiles = Integer.parseInt(args[++i]);
                        }
                        break;
                    case "--config":
                        if (i + 1 < args.length) {
                            config.configFilter = args[++i].toUpperCase();
                        }
                        break;
                    case "--verbose":
                        config.verbose = true;
                        break;
                    case "--save":
                        if (i + 1 < args.length) {
                            config.saveDir = args[++i];
                        } else {
                            config.saveDir = "./";
                        }
                        break;
                    case "--save-formats":
                        if (i + 1 < args.length) {
                            config.saveFormats = args[++i].toLowerCase().split(",");
                        }
                        break;
                }
            }
            
            return config;
        }
        
        static void printUsage() {
            System.out.println("Usage: java LatencyTableGenerator <json-file-or-directory> [options]");
            System.out.println("\nOptions:");
            System.out.println("  --limit N         Limit to N files per configuration type (default: all)");
            System.out.println("  --latest N        Use only the N most recent files per config (overrides --limit)");
            System.out.println("  --config TYPE     Filter to specific configuration (e.g., DICTIONARY_ONLY)");
            System.out.println("  --verbose         Show detailed processing information");
            System.out.println("  --save DIR        Save tables to directory (default: current dir)");
            System.out.println("  --save-formats    Comma-separated formats: csv,json,md,html (default: csv,json,md)");
            System.out.println("\nExamples:");
            System.out.println("  java LatencyTableGenerator results-dir/");
            System.out.println("  java LatencyTableGenerator results-dir/ --limit 5");
            System.out.println("  java LatencyTableGenerator results-dir/ --latest 3 --config DICTIONARY_ONLY");
            System.out.println("  java LatencyTableGenerator results-dir/ --latest 10 --verbose");
            System.out.println("  java LatencyTableGenerator results-dir/ --latest 5 --save ./output/");
            System.out.println("  java LatencyTableGenerator results-dir/ --save-formats csv,json");
            System.out.println("\nSystem Properties:");
            System.out.println("  -Dponysdk.table.consolidation=average|latest|best");
        }
    }
    
    /**
     * Smart file loading with filtering and limiting (optimized for large datasets)
     * Time complexity: O(n log n) where n = filtered files
     */
    public static List<SessionData> loadSessionsWithLimits(Path inputPath, Config config) throws IOException {
        List<SessionData> sessions = new ArrayList<>();
        
        if (Files.isRegularFile(inputPath)) {
            // Single file mode
            if (config.verbose) System.out.println("Processing single file: " + inputPath);
            SessionData session = loadSessionData(inputPath.toString());
            sessions.add(session);
            return sessions;
        }
        
        if (config.verbose) System.out.println("Scanning directory: " + inputPath);
        
        // Collect and filter files first (avoid loading unnecessary files)
        List<Path> candidateFiles = Files.list(inputPath)
            .filter(path -> path.toString().endsWith(".json"))
            .filter(path -> !path.getFileName().toString().startsWith("manifest_"))
            .collect(Collectors.toList());
            
        if (config.verbose) {
            System.out.printf("Found %d candidate JSON files%n", candidateFiles.size());
        }
        
        // Group files by configuration type (based on filename pattern)
        Map<String, List<Path>> filesByConfig = new HashMap<>();
        
        for (Path file : candidateFiles) {
            String filename = file.getFileName().toString();
            String configType = extractConfigTypeFromFilename(filename);
            
            // Apply config filter if specified
            if (config.configFilter != null && !configType.equals(config.configFilter)) {
                continue;
            }
            
            filesByConfig.computeIfAbsent(configType, k -> new ArrayList<>()).add(file);
        }
        
        if (config.verbose) {
            System.out.println("\nFiles by configuration type:");
            filesByConfig.forEach((configType, files) -> 
                System.out.printf("  %s: %d files%n", configType, files.size()));
        }
        
        // Apply limits per configuration type
        for (Map.Entry<String, List<Path>> entry : filesByConfig.entrySet()) {
            String configType = entry.getKey();
            List<Path> configFiles = entry.getValue();
            
            // Sort by modification time (newest first) for --latest option
            if (config.latestFiles > 0) {
                configFiles.sort((a, b) -> {
                    try {
                        return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
                    } catch (IOException e) {
                        return 0;
                    }
                });
                
                // Take only the latest N files
                configFiles = configFiles.stream()
                    .limit(config.latestFiles)
                    .collect(Collectors.toList());
                    
                if (config.verbose) {
                    System.out.printf("Taking %d latest files for %s%n", 
                                     Math.min(config.latestFiles, entry.getValue().size()), configType);
                }
            } else if (config.maxFilesPerConfig < Integer.MAX_VALUE) {
                // Apply general limit
                configFiles = configFiles.stream()
                    .limit(config.maxFilesPerConfig)
                    .collect(Collectors.toList());
                    
                if (config.verbose) {
                    System.out.printf("Limiting to %d files for %s%n", 
                                     Math.min(config.maxFilesPerConfig, entry.getValue().size()), configType);
                }
            }
            
            // Load the selected files
            for (Path file : configFiles) {
                try {
                    SessionData session = loadSessionData(file.toString());
                    sessions.add(session);
                    
                    if (config.verbose) {
                        System.out.printf("  Loaded: %s (%s, %d messages)%n", 
                                         file.getFileName(), session.configurationType, session.totalTransmissions);
                    }
                } catch (IOException e) {
                    System.err.printf("Error loading %s: %s%n", file, e.getMessage());
                }
            }
        }
        
        return sessions;
    }
    
    /**
     * Extract configuration type from filename pattern
     * Handles patterns like: run_default_dictionary_only_export_123.json -> DICTIONARY_ONLY
     */
    private static String extractConfigTypeFromFilename(String filename) {
        // Common patterns in the existing files
        if (filename.contains("dictionary_only")) return "DICTIONARY_ONLY";
        if (filename.contains("all_on")) return "ALL_ON";
        if (filename.contains("custom")) return "CUSTOM";
        if (filename.contains("trie_only")) return "TRIE_ONLY";
        if (filename.contains("codet5_only")) return "CODET5_ONLY";
        if (filename.contains("all_off")) return "ALL_OFF";
        
        // Fallback: try to extract from JSON content later
        return "UNKNOWN";
    }
    
    /**
     * Main method with improved argument parsing
     */
    public static void main(String[] args) {
        Config config = Config.parseArgs(args);
        if (config == null) {
            return;
        }
        
        Path inputPath = Paths.get(config.inputPath);
        List<SessionData> sessions;
        
        try {
            // Use improved loading with limits and filtering
            sessions = loadSessionsWithLimits(inputPath, config);
            
            if (sessions.isEmpty()) {
                System.out.println("No valid MetricsExporter JSON files found.");
                if (config.configFilter != null) {
                    System.out.printf("Note: Filtered for configuration type '%s'%n", config.configFilter);
                }
                return;
            }
            
            System.out.printf("\n📊 Loaded %d sessions total%n", sessions.size());
            
            // Generate individual tables for each session
            for (SessionData session : sessions) {
                generateSessionTable(session);
            }
            
            // Generate comparison table if multiple sessions
            if (sessions.size() > 1) {
                // Parse consolidation strategy from system property (competitive programming style)
                ConsolidationStrategy strategy = parseConsolidationStrategy(
                    System.getProperty("ponysdk.table.consolidation", "average")
                );
                generateComparisonTableWithStrategy(sessions, strategy, config);
            }
            
        } catch (IOException e) {
            System.err.printf("Error: %s%n", e.getMessage());
            if (config.verbose) {
                e.printStackTrace();
            }
        } catch (NumberFormatException e) {
            System.err.println("Error: Invalid number in arguments. " + e.getMessage());
            Config.printUsage();
        }
    }
}