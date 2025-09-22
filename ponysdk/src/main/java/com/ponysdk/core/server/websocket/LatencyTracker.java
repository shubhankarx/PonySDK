package com.ponysdk.core.server.websocket;

import com.ponysdk.core.model.ServerToClientModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-frequency trading latency tracker for WebSocket message pipeline.
 * Measures 5 stages: intercept → dictionary → hash → encode → transmit
 */
public final class LatencyTracker implements WebSocket.Listener {
    
    private static final Logger log = LoggerFactory.getLogger(LatencyTracker.class);
    
    // Pipeline stage counters
    private final AtomicLong messageInterceptCount = new AtomicLong(0);
    private final AtomicLong messageEncodeCount = new AtomicLong(0);
    private final AtomicLong dictionaryHitCount = new AtomicLong(0);
    private final AtomicLong dictionaryMissCount = new AtomicLong(0);
    private final AtomicLong hashOperationCount = new AtomicLong(0);
    
    // Dictionary performance tracking
    private final AtomicLong messagesWithDictionary = new AtomicLong(0);
    private final AtomicLong messagesWithoutDictionary = new AtomicLong(0);
    private final AtomicLong totalDictionaryLatencyNanos = new AtomicLong(0);
    private final AtomicLong totalNoDictionaryLatencyNanos = new AtomicLong(0);
    
    // Trie prediction tracking (thread-safe for cross-thread access)
    private final AtomicLong trieQueryCount = new AtomicLong(0);
    private final AtomicLong trieHitCount = new AtomicLong(0);
    private final AtomicLong trieMissCount = new AtomicLong(0);
    private final AtomicLong totalTrieLatencyNanos = new AtomicLong(0);
    
    // CodeT5/FastAPI tracking (thread-safe for async HTTP calls)
    private final AtomicLong codeT5QueryCount = new AtomicLong(0);
    private final AtomicLong codeT5SuccessCount = new AtomicLong(0);
    private final AtomicLong codeT5ErrorCount = new AtomicLong(0);
    private final AtomicLong totalCodeT5LatencyNanos = new AtomicLong(0);
    private final AtomicLong minCodeT5LatencyNanos = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxCodeT5LatencyNanos = new AtomicLong(Long.MIN_VALUE);
    
    // Widget prediction tracking (single-threaded WebSocket context)
    private final AtomicLong widgetPredictionCount = new AtomicLong(0);
    private final AtomicLong widgetHitCount = new AtomicLong(0);
    private final AtomicLong widgetMissCount = new AtomicLong(0);
    
    // Network transmission timing
    private final AtomicLong transmissionStartNanos = new AtomicLong(0);
    private final AtomicLong currentTransmissionBytes = new AtomicLong(0);
    
    // Ring buffer for percentile calculations (power of 2 for fast modulo)
    private static final int RING_BUFFER_SIZE = 1024;
    private final long[] latencyRingBuffer = new long[RING_BUFFER_SIZE];
    private final boolean[] dictionaryUsedBuffer = new boolean[RING_BUFFER_SIZE]; // Track dictionary usage per transmission
    private final AtomicLong ringBufferWriteIndex = new AtomicLong(0);
    
    // Track current transmission's dictionary usage
    private volatile boolean currentTransmissionUsedDictionary = false;
    
    // Aggregate metrics
    private final AtomicLong totalTransmissions = new AtomicLong(0);
    private final AtomicLong totalTransmittedBytes = new AtomicLong(0);
    private final AtomicLong minLatencyNanos = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxLatencyNanos = new AtomicLong(Long.MIN_VALUE);
    
    // Frame type distribution tracking
    private final ConcurrentHashMap<ServerToClientModel, AtomicLong> frameTypeDistribution = new ConcurrentHashMap<>();

    // TRUE END-TO-END LATENCY TRACKING (Server → Client DOM Ready)
    private final AtomicLong endToEndCount = new AtomicLong(0);
    private final AtomicLong totalEndToEndLatencyMillis = new AtomicLong(0);
    private final AtomicLong minEndToEndLatencyMillis = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxEndToEndLatencyMillis = new AtomicLong(Long.MIN_VALUE);

    // Server-side only latency (current measurement - for comparison)
    private final AtomicLong serverOnlyCount = new AtomicLong(0);
    private final AtomicLong totalServerOnlyLatencyNanos = new AtomicLong(0);

    // CLIENT-SIDE LATENCY TRACKING (Message Receipt → DOM Update)
    private final AtomicLong clientMessageStartTime = new AtomicLong(0);
    private final AtomicLong clientLatencyCount = new AtomicLong(0);
    private final AtomicLong totalClientLatencyMillis = new AtomicLong(0);
    private final AtomicLong minClientLatencyMillis = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxClientLatencyMillis = new AtomicLong(Long.MIN_VALUE);

    // Periodic reporting control
    private volatile long lastReportTimeMillis = System.currentTimeMillis();
    private static final long REPORT_INTERVAL_MILLIS = 30000; // 30 seconds
    
    /**
     * Stage 1: Message interception at encode() entry point
     * Called from WebSocket.java:1175
     */
    public void onInterceptMessage(String modelType, Object value) {
        messageInterceptCount.incrementAndGet();
        triggerPeriodicReportIfNeeded();
    }

    /**
     * Handle true end-to-end latency measurement from client roundtrip response
     * Called when client reports TERMINAL_LATENCY back to server
     */
    public void onClientRoundtripLatency(long clientLatencyMillis) {
        endToEndCount.incrementAndGet();
        totalEndToEndLatencyMillis.addAndGet(clientLatencyMillis);

        // Update min/max bounds
        long currentMin = minEndToEndLatencyMillis.get();
        while (clientLatencyMillis < currentMin &&
               !minEndToEndLatencyMillis.compareAndSet(currentMin, clientLatencyMillis)) {
            currentMin = minEndToEndLatencyMillis.get();
        }

        long currentMax = maxEndToEndLatencyMillis.get();
        while (clientLatencyMillis > currentMax &&
               !maxEndToEndLatencyMillis.compareAndSet(currentMax, clientLatencyMillis)) {
            currentMax = maxEndToEndLatencyMillis.get();
        }
    }
    
    /**
     * Stage 2: Dictionary pattern lookup result
     * Called from WebSocket.java:1269
     */
    public void onDictionaryLookup(String patternKey, boolean cacheHit) {
        if (cacheHit) {
            dictionaryHitCount.incrementAndGet();
            currentTransmissionUsedDictionary = true; // Mark current transmission as using dictionary
            log.debug("Dictionary cache HIT: {}", patternKey);
        } else {
            dictionaryMissCount.incrementAndGet();
            // Note: miss doesn't mean no dictionary, just no pattern found
        }
    }
    
    /**
     * Stage 3: Hash computation for dictionary key generation
     * Called from WebSocket.java:1278
     */
    public void onHashCompute(String hashKey, byte[] payload) {
        hashOperationCount.incrementAndGet();
    }
    
    /**
     * Stage 4: Message encoding before transmission
     * Called from WebSocket.java:1204,1234
     */
    public void onEncode(ServerToClientModel frameType, Object frameValue) {
        messageEncodeCount.incrementAndGet();
    }
    
    /**
     * Trie prediction query measurement
     * Called from WebSocket.java trie operations (isPrefixOfKnownTriplet, isKnownTriplet)
     */
    public void onTrieQuery(final String operation, final boolean hit, final long latencyNanos) {
        trieQueryCount.incrementAndGet();
        if (hit) {
            trieHitCount.incrementAndGet();
            log.debug("Trie {} HIT: {:.2f}ms", operation, latencyNanos / 1_000_000.0);
        } else {
            trieMissCount.incrementAndGet();
        }
        totalTrieLatencyNanos.addAndGet(latencyNanos);
    }
    
    /**
     * CodeT5/FastAPI HTTP request measurement  
     * Called from WebSocket.java sendJsonPostRequestUsingHttpURLConnection
     */
    public void onCodeT5Query(final boolean success, final long latencyNanos, final String endpoint) {
        codeT5QueryCount.incrementAndGet();
        if (success) {
            codeT5SuccessCount.incrementAndGet();
            log.debug("CodeT5 SUCCESS: {:.2f}ms to {}", latencyNanos / 1_000_000.0, endpoint);
        } else {
            codeT5ErrorCount.incrementAndGet();
            log.warn("CodeT5 ERROR: {:.2f}ms to {}", latencyNanos / 1_000_000.0, endpoint);
        }
        totalCodeT5LatencyNanos.addAndGet(latencyNanos);
        
        // Update min/max using CAS pattern (thread-safe for async HTTP calls)
        updateMinCodeT5Latency(latencyNanos);
        updateMaxCodeT5Latency(latencyNanos);
    }
    
    /**
     * Widget sequence prediction measurement
     * Called from WebSocket.java tryPredictNextWidget and validation methods
     */
    public void onWidgetPrediction(final String widgetType, final boolean hit) {
        widgetPredictionCount.incrementAndGet();
        if (hit) {
            widgetHitCount.incrementAndGet();
            log.debug("Widget prediction HIT: {}", widgetType);
        } else {
            widgetMissCount.incrementAndGet();
        }
    }
    
    /**
     * Stage 5A: Frame ready for transmission (WebSocket.Listener)
     */
    @Override
    public void onOutgoingPonyFrame(ServerToClientModel frameType, Object frameValue) {
        // Mark transmission start time on first frame of batch
        transmissionStartNanos.compareAndSet(0, System.nanoTime());
        
        // Track frame type distribution
        frameTypeDistribution.computeIfAbsent(frameType, k -> new AtomicLong()).incrementAndGet();
    }
    
    /**
     * Stage 5B: Bytes queued for transmission (WebSocket.Listener)
     */
    @Override
    public void onOutgoingPonyFramesBytes(int byteCount) {
        currentTransmissionBytes.addAndGet(byteCount);
        totalTransmittedBytes.addAndGet(byteCount);
    }
    
    /**
     * Stage 5C: Transmission complete with network ACK (WebSocket.Listener)
     */
    @Override
    public void onFrameWriteSuccess() {
        long startNanos = transmissionStartNanos.getAndSet(0);
        if (startNanos == 0) return; // No active transmission

        // Calculate SERVER-SIDE-ONLY latency (NOT true end-to-end)
        long serverLatencyNanos = System.nanoTime() - startNanos;

        // Track server-side latency separately for comparison
        serverOnlyCount.incrementAndGet();
        totalServerOnlyLatencyNanos.addAndGet(serverLatencyNanos);
        
        // Store in ring buffer for percentile calculation (server-side only)
        int bufferIndex = (int)(ringBufferWriteIndex.getAndIncrement() & (RING_BUFFER_SIZE - 1));
        latencyRingBuffer[bufferIndex] = serverLatencyNanos;
        dictionaryUsedBuffer[bufferIndex] = currentTransmissionUsedDictionary; // Store dictionary usage
        
        // Track dictionary-specific counters
        if (currentTransmissionUsedDictionary) {
            messagesWithDictionary.incrementAndGet();
            totalDictionaryLatencyNanos.addAndGet(serverLatencyNanos);
        } else {
            messagesWithoutDictionary.incrementAndGet();
            totalNoDictionaryLatencyNanos.addAndGet(serverLatencyNanos);
        }

        // Update min/max with CAS loop
        updateMinLatency(serverLatencyNanos);
        updateMaxLatency(serverLatencyNanos);

        totalTransmissions.incrementAndGet();
        currentTransmissionBytes.set(0);

        // Reset dictionary flag for next transmission
        currentTransmissionUsedDictionary = false;

        // Alert on high latency (server-side only)
        if (serverLatencyNanos > 50_000_000L) { // > 50ms
            log.warn("HIGH SERVER LATENCY: {:.2f}ms", serverLatencyNanos / 1_000_000.0);
        }
    }
    
    /**
     * Thread-safe min update using CAS
     */
    private void updateMinLatency(long newValue) {
        long currentMin;
        while ((currentMin = minLatencyNanos.get()) > newValue) {
            if (minLatencyNanos.compareAndSet(currentMin, newValue)) break;
        }
    }
    
    /**
     * Thread-safe max update using CAS
     */
    private void updateMaxLatency(long newValue) {
        long currentMax;
        while ((currentMax = maxLatencyNanos.get()) < newValue) {
            if (maxLatencyNanos.compareAndSet(currentMax, newValue)) break;
        }
    }
    
    /**
     * Thread-safe min CodeT5 latency update using CAS
     */
    private void updateMinCodeT5Latency(final long newValue) {
        long currentMin;
        while ((currentMin = minCodeT5LatencyNanos.get()) > newValue) {
            if (minCodeT5LatencyNanos.compareAndSet(currentMin, newValue)) break;
        }
    }
    
    /**
     * Thread-safe max CodeT5 latency update using CAS  
     */
    private void updateMaxCodeT5Latency(final long newValue) {
        long currentMax;
        while ((currentMax = maxCodeT5LatencyNanos.get()) < newValue) {
            if (maxCodeT5LatencyNanos.compareAndSet(currentMax, newValue)) break;
        }
    }
    
    /**
     * Trigger periodic metrics report every 30 seconds
     */
    private void triggerPeriodicReportIfNeeded() {
        long nowMillis = System.currentTimeMillis();
        if (nowMillis - lastReportTimeMillis > REPORT_INTERVAL_MILLIS) {
            lastReportTimeMillis = nowMillis;
            logMetricsReport();
        }
    }
    
    /**
     * Log comprehensive metrics report
     */
    private void logMetricsReport() {
        long transmissions = totalTransmissions.get();
        if (transmissions == 0) return;
        
        // Calculate cache efficiency
        long cacheHits = dictionaryHitCount.get();
        long cacheMisses = dictionaryMissCount.get();
        long cacheTotal = cacheHits + cacheMisses;
        double cacheHitRate = cacheTotal > 0 ? (cacheHits * 100.0 / cacheTotal) : 0;
        
        log.info("=== LATENCY METRICS REPORT ===");
        log.info("Pipeline: intercepts={}, encodes={}, transmissions={}", 
            messageInterceptCount.get(), messageEncodeCount.get(), transmissions);
        log.info("Dictionary: hits={}, misses={}, hitRate={}%", 
            cacheHits, cacheMisses, String.format("%.1f", cacheHitRate));
        
        // Trie prediction metrics
        long trieQueries = trieQueryCount.get();
        long trieHits = trieHitCount.get();
        long trieMisses = trieMissCount.get();
        double trieHitRate = trieQueries > 0 ? (trieHits * 100.0 / trieQueries) : 0;
        double avgTrieLatency = trieQueries > 0 ? (totalTrieLatencyNanos.get() / 1_000_000.0 / trieQueries) : 0;
        log.info("Trie: queries={}, hits={}, hitRate={}%, avgLatency={}ms", 
            trieQueries, trieHits, String.format("%.1f", trieHitRate), String.format("%.2f", avgTrieLatency));
            
        // CodeT5/FastAPI metrics
        long codeT5Queries = codeT5QueryCount.get();
        long codeT5Success = codeT5SuccessCount.get();
        long codeT5Errors = codeT5ErrorCount.get();
        double codeT5SuccessRate = codeT5Queries > 0 ? (codeT5Success * 100.0 / codeT5Queries) : 0;
        double avgCodeT5Latency = codeT5Queries > 0 ? (totalCodeT5LatencyNanos.get() / 1_000_000.0 / codeT5Queries) : 0;
        double minCodeT5Latency = minCodeT5LatencyNanos.get() == Long.MAX_VALUE ? 0 : minCodeT5LatencyNanos.get() / 1_000_000.0;
        double maxCodeT5Latency = maxCodeT5LatencyNanos.get() == Long.MIN_VALUE ? 0 : maxCodeT5LatencyNanos.get() / 1_000_000.0;
        log.info("CodeT5: queries={}, success={}, errors={}, successRate={}%", 
            codeT5Queries, codeT5Success, codeT5Errors, String.format("%.1f", codeT5SuccessRate));
        log.info("CodeT5 Latency: avg={}ms, min={}ms, max={}ms", 
            String.format("%.2f", avgCodeT5Latency), String.format("%.2f", minCodeT5Latency), String.format("%.2f", maxCodeT5Latency));
            
        // Widget prediction metrics
        long widgetQueries = widgetPredictionCount.get();
        long widgetHits = widgetHitCount.get();
        long widgetMisses = widgetMissCount.get();
        double widgetHitRate = widgetQueries > 0 ? (widgetHits * 100.0 / widgetQueries) : 0;
        log.info("Widget: predictions={}, hits={}, hitRate={}%", 
            widgetQueries, widgetHits, String.format("%.1f", widgetHitRate));
        log.info("Network: {}KB transmitted, {} hash ops", 
            totalTransmittedBytes.get() / 1024, hashOperationCount.get());
        log.info("Latency: p50={}ms, p95={}ms, p99={}ms", 
            String.format("%.2f", calculatePercentile(50)), String.format("%.2f", calculatePercentile(95)), String.format("%.2f", calculatePercentile(99)));
            
        // Dictionary performance comparison
        long dictMsgs = messagesWithDictionary.get();
        long noDictMsgs = messagesWithoutDictionary.get();
        
        if (dictMsgs > 0 && noDictMsgs > 0) {
            double avgDictLatency = totalDictionaryLatencyNanos.get() / 1_000_000.0 / dictMsgs;
            double avgNoDictLatency = totalNoDictionaryLatencyNanos.get() / 1_000_000.0 / noDictMsgs;
            double improvement = ((avgNoDictLatency - avgDictLatency) / avgNoDictLatency) * 100;
            
            log.info("=== LATENCY MEASUREMENT COMPARISON ===");

            // Server-side only measurements (current system)
            double avgServerOnlyMs = serverOnlyCount.get() > 0 ?
                totalServerOnlyLatencyNanos.get() / 1_000_000.0 / serverOnlyCount.get() : 0;
            log.info("SERVER-SIDE ONLY: {}ms avg ({} measurements) [Socket Buffer Write Only]",
                String.format("%.2f", avgServerOnlyMs), serverOnlyCount.get());

            // True end-to-end measurements
            double avgEndToEndMs = endToEndCount.get() > 0 ?
                totalEndToEndLatencyMillis.get() / (double) endToEndCount.get() : 0;
            log.info("TRUE END-TO-END: {}ms avg ({} measurements) [Network + Client DOM Ready]",
                String.format("%.2f", avgEndToEndMs), endToEndCount.get());

            if (endToEndCount.get() > 0) {
                log.info("End-to-End Range: min={}ms, max={}ms",
                    minEndToEndLatencyMillis.get(), maxEndToEndLatencyMillis.get());
            }

            log.info("=== DICTIONARY PERFORMANCE COMPARISON ===");
            log.info("WITH Dictionary: {}ms avg ({} transmissions)",
                String.format("%.2f", avgDictLatency), dictMsgs);
            log.info("WITHOUT Dictionary: {}ms avg ({} transmissions)",
                String.format("%.2f", avgNoDictLatency), noDictMsgs);
            log.info("Performance Impact: {}% {}",
                String.format("%.1f", Math.abs(improvement)),
                improvement > 0 ? "IMPROVEMENT" : "OVERHEAD");
            
            // Calculate percentiles for each category
            log.info("Dictionary percentiles: p50={}ms, p95={}ms, p99={}ms",
                String.format("%.2f", calculatePercentileForCategory(50, true)),
                String.format("%.2f", calculatePercentileForCategory(95, true)),
                String.format("%.2f", calculatePercentileForCategory(99, true)));
            log.info("No-Dictionary percentiles: p50={}ms, p95={}ms, p99={}ms",
                String.format("%.2f", calculatePercentileForCategory(50, false)),
                String.format("%.2f", calculatePercentileForCategory(95, false)),
                String.format("%.2f", calculatePercentileForCategory(99, false)));
        } else if (dictMsgs > 0) {
            double avgDictLatency = totalDictionaryLatencyNanos.get() / 1_000_000.0 / dictMsgs;
            log.info("Dictionary ONLY: {}ms avg ({} transmissions)", String.format("%.2f", avgDictLatency), dictMsgs);
        } else if (noDictMsgs > 0) {
            double avgNoDictLatency = totalNoDictionaryLatencyNanos.get() / 1_000_000.0 / noDictMsgs;
            log.info("No-Dictionary ONLY: {}ms avg ({} transmissions)", String.format("%.2f", avgNoDictLatency), noDictMsgs);
        }
        
        // Log top 3 frame types
        frameTypeDistribution.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
            .limit(3)
            .forEach(entry -> log.info("  Frame type: {} = {} occurrences", 
                entry.getKey(), entry.getValue().get()));
    }
    
    /**
     * Calculate latency percentile using quickselect on ring buffer
     * @param percentile 0-100
     * @return latency in milliseconds
     */
    public double calculatePercentile(double percentile) {
        int sampleCount = Math.min((int)totalTransmissions.get(), RING_BUFFER_SIZE);
        if (sampleCount == 0) return 0;
        
        // Copy ring buffer for sorting
        long[] latencySamples = new long[sampleCount];
        int startIndex = totalTransmissions.get() > RING_BUFFER_SIZE ? 
            (int)((ringBufferWriteIndex.get() - RING_BUFFER_SIZE) & (RING_BUFFER_SIZE - 1)) : 0;
        
        for (int i = 0; i < sampleCount; i++) {
            latencySamples[i] = latencyRingBuffer[(startIndex + i) & (RING_BUFFER_SIZE - 1)];
        }
        
        java.util.Arrays.sort(latencySamples);
        int percentileIndex = (int)(percentile * (sampleCount - 1) / 100.0);
        return latencySamples[percentileIndex] / 1_000_000.0; // Convert to ms
    }
    
    /**
     * Calculate latency percentile for specific category (dictionary vs no-dictionary)
     * @param percentile 0-100
     * @param withDictionary true for dictionary transmissions, false for non-dictionary
     * @return latency in milliseconds
     */
    public double calculatePercentileForCategory(double percentile, boolean withDictionary) {
        int sampleCount = Math.min((int)totalTransmissions.get(), RING_BUFFER_SIZE);
        if (sampleCount == 0) return 0;
        
        // Collect samples for the specific category
        java.util.List<Long> categorySamples = new java.util.ArrayList<>();
        int startIndex = totalTransmissions.get() > RING_BUFFER_SIZE ? 
            (int)((ringBufferWriteIndex.get() - RING_BUFFER_SIZE) & (RING_BUFFER_SIZE - 1)) : 0;
            
        for (int i = 0; i < sampleCount; i++) {
            int bufferIndex = (startIndex + i) & (RING_BUFFER_SIZE - 1);
            if (dictionaryUsedBuffer[bufferIndex] == withDictionary) {
                categorySamples.add(latencyRingBuffer[bufferIndex]);
            }
        }
        
        if (categorySamples.isEmpty()) return 0;
        
        // Sort and calculate percentile
        categorySamples.sort(Long::compareTo);
        int percentileIndex = (int)(percentile * (categorySamples.size() - 1) / 100.0);
        return categorySamples.get(percentileIndex) / 1_000_000.0; // Convert to ms
    }
    
    /**
     * Get current latency statistics snapshot
     */
    public LatencyStats getStats() {
        return new LatencyStats(
            totalTransmissions.get(),
            totalTransmittedBytes.get(),
            minLatencyNanos.get() == Long.MAX_VALUE ? 0 : minLatencyNanos.get() / 1_000_000.0,
            maxLatencyNanos.get() == Long.MIN_VALUE ? 0 : maxLatencyNanos.get() / 1_000_000.0,
            calculatePercentile(50),
            calculatePercentile(90),
            calculatePercentile(95),
            calculatePercentile(99)
        );
    }
    
    /**
     * Alias for getStats() to match WebSocket.java:1476 call
     */
    public LatencyStats getStageMetrics() {
        return getStats();
    }
    
    /**
     * Immutable latency statistics snapshot
     */
    public static class LatencyStats {
        public final long totalWrites;
        public final long totalBytes;
        public final double minMs;
        public final double maxMs;
        public final double p50Ms;
        public final double p90Ms;
        public final double p95Ms;
        public final double p99Ms;
        
        LatencyStats(long writes, long bytes, double min, double max, 
                    double p50, double p90, double p95, double p99) {
            this.totalWrites = writes;
            this.totalBytes = bytes;
            this.minMs = min;
            this.maxMs = max;
            this.p50Ms = p50;
            this.p90Ms = p90;
            this.p95Ms = p95;
            this.p99Ms = p99;
        }
        
        @Override
        public String toString() {
            return String.format("Writes=%d, Bytes=%d, Latency[min=%.2f, p50=%.2f, p90=%.2f, p95=%.2f, p99=%.2f, max=%.2f]ms",
                totalWrites, totalBytes, minMs, p50Ms, p90Ms, p95Ms, p99Ms, maxMs);
        }
    }

    // ========== CLIENT-SIDE LATENCY TRACKING METHODS ==========

    /**
     * Start timing when message is received from server (called from WebSocketClient)
     */
    public void startClientTiming() {
        clientMessageStartTime.set(System.currentTimeMillis());
    }

    /**
     * End timing when UI building completes (called from UIBuilder)
     */
    public void endClientTiming() {
        long startTime = clientMessageStartTime.getAndSet(0);
        if (startTime == 0) return; // No active timing

        long latencyMs = System.currentTimeMillis() - startTime;

        // Update statistics
        clientLatencyCount.incrementAndGet();
        totalClientLatencyMillis.addAndGet(latencyMs);

        // Update min/max bounds
        long currentMin = minClientLatencyMillis.get();
        while (latencyMs < currentMin &&
               !minClientLatencyMillis.compareAndSet(currentMin, latencyMs)) {
            currentMin = minClientLatencyMillis.get();
        }

        long currentMax = maxClientLatencyMillis.get();
        while (latencyMs > currentMax &&
               !maxClientLatencyMillis.compareAndSet(currentMax, latencyMs)) {
            currentMax = maxClientLatencyMillis.get();
        }

        // Log every 100 messages or significant latency (>50ms)
        long count = clientLatencyCount.get();
        if (count % 100 == 0 || latencyMs > 50) {
            double avgLatency = (double) totalClientLatencyMillis.get() / count;
            long minLatency = minClientLatencyMillis.get();
            long maxLatency = maxClientLatencyMillis.get();

            log.info("CLIENT LATENCY: {}ms (avg: {:.1f}ms, min: {}ms, max: {}ms, count: {})",
                    latencyMs, avgLatency,
                    minLatency == Long.MAX_VALUE ? 0 : minLatency,
                    maxLatency == Long.MIN_VALUE ? 0 : maxLatency,
                    count);
        }
    }

    // ========== Getter Methods for MetricsExporter Integration ==========

    public long getMinLatencyNanos() { return minLatencyNanos.get(); }
    public long getMaxLatencyNanos() { return maxLatencyNanos.get(); }
    public long getDictionaryHitCount() { return dictionaryHitCount.get(); }
    public long getDictionaryMissCount() { return dictionaryMissCount.get(); }
    public long getTrieQueryCount() { return trieQueryCount.get(); }
    public long getTrieHitCount() { return trieHitCount.get(); }
    public long getTrieMissCount() { return trieMissCount.get(); }
    public long getTotalTrieLatencyNanos() { return totalTrieLatencyNanos.get(); }
    public long getCodeT5QueryCount() { return codeT5QueryCount.get(); }
    public long getCodeT5SuccessCount() { return codeT5SuccessCount.get(); }
    public long getCodeT5ErrorCount() { return codeT5ErrorCount.get(); }
    public long getTotalCodeT5LatencyNanos() { return totalCodeT5LatencyNanos.get(); }
    public long getMinCodeT5LatencyNanos() { return minCodeT5LatencyNanos.get(); }
    public long getMaxCodeT5LatencyNanos() { return maxCodeT5LatencyNanos.get(); }
    public long getTotalTransmissions() { return totalTransmissions.get(); }
    public long getTotalTransmittedBytes() { return totalTransmittedBytes.get(); }

    // Client-side end-to-end latency getters
    public long getEndToEndCount() { return endToEndCount.get(); }
    public long getTotalEndToEndLatencyMillis() { return totalEndToEndLatencyMillis.get(); }
    public long getMinEndToEndLatencyMillis() { return minEndToEndLatencyMillis.get(); }
    public long getMaxEndToEndLatencyMillis() { return maxEndToEndLatencyMillis.get(); }
    public double getAvgEndToEndLatencyMillis() {
        long count = endToEndCount.get();
        return count > 0 ? (double) totalEndToEndLatencyMillis.get() / count : 0.0;
    }

    // WebSocket.Listener unused callbacks
    @Override public void onIncomingText(String text) {}
    @Override public void onOutgoingWebSocketFrame(int headerLength, int payloadLength) {}
    @Override public void onIncomingWebSocketFrame(int headerLength, int payloadLength) {}
    @Override public void onFrameWriteFailure(Throwable cause) {
        log.error("Frame transmission failed", cause);
    }
}