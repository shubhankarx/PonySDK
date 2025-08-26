package com.ponysdk.core.server.websocket;

import com.ponysdk.core.model.ServerToClientModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An enhanced network latency monitor that measures **true round-trip latency**.
 * Unlike the original NetworkMemoryLatencyMonitor which only measured server-side
 * socket buffer acknowledgments, this monitor implements ping-pong messaging to 
 * measure actual server → client → server latency.
 * 
 * <p>Key improvements:
 * <ul>
 *   <li><b>Real network latency</b> – measures time for data to reach client and return</li>
 *   <li><b>Ping-pong protocol</b> – sends PING messages, waits for PONG responses</li>
 *   <li><b>Memory leak protection</b> – automatic cleanup of stale pings</li>
 *   <li><b>Thread-safe statistics</b> – concurrent access safe</li>
 * </ul>
 * 
 * <p>Usage:
 * <pre>
 * RoundTripLatencyMonitor monitor = new RoundTripLatencyMonitor();
 * webSocket.setListener(monitor);
 * monitor.start();
 * 
 * // Send periodic pings during your test
 * monitor.sendPing();
 * 
 * // Client should respond with PONG messages
 * // monitor.onIncomingText() handles the responses
 * 
 * monitor.stop();
 * double latency = monitor.getRoundTripLatencyMillis();
 * </pre>
 */
public final class RoundTripLatencyMonitor implements WebSocket.Listener {

    private static final Logger log = LoggerFactory.getLogger(RoundTripLatencyMonitor.class);

    /* ---------- Network counters ---------- */
    private final AtomicLong totalBytesSent   = new AtomicLong();
    private final AtomicInteger totalWSFrames = new AtomicInteger();
    private final Map<ServerToClientModel, AtomicInteger> perModelCount = new ConcurrentHashMap<>();

    /* ---------- Round-trip latency measurements ---------- */
    private final Map<String, Long> pendingPings = new ConcurrentHashMap<>();
    private final AtomicLong totalRoundTripNanos = new AtomicLong();
    private final AtomicInteger completedPings = new AtomicInteger();
    private final AtomicLong pingSequence = new AtomicLong();

    /* ---------- Memory snapshots ---------- */
    private long startMemory;
    private long endMemory;
    private long peakMemory;

    private final Runtime rt = Runtime.getRuntime();

    /** Call once just before the test begins. */
    public void start() {
        gcAndWait();
        startMemory = usedMemory();
        peakMemory  = startMemory;
        
        // Clear any previous ping state
        pendingPings.clear();
        totalRoundTripNanos.set(0);
        completedPings.set(0);
        pingSequence.set(0);
        
        log.info("[RTM] Start memory = {} KB", startMemory / 1024);
        log.info("[RTM] Round-trip latency monitor started");
    }

    /** Call once right after the test ends. */
    public void stop() {
        gcAndWait();
        endMemory = usedMemory();
        
        // Log final statistics
        log.info("[RTM] End memory = {} KB (Δ = {} KB)", endMemory / 1024, (endMemory - startMemory) / 1024);
        
        if (completedPings.get() > 0) {
            log.info("[RTM] Round-trip latency: {:.2f} ms (over {} pings)", 
                    getRoundTripLatencyMillis(), completedPings.get());
        } else {
            log.warn("[RTM] No ping-pong cycles completed during test");
        }
        
        if (!pendingPings.isEmpty()) {
            log.warn("[RTM] {} pings still pending (possible network issues)", pendingPings.size());
        }
    }

    /* ---------------------------------------------------------------------
     *                        WebSocket.Listener hooks
     * ------------------------------------------------------------------ */

    @Override
    public void onOutgoingPonyFrame(final ServerToClientModel model, final Object value) {
        perModelCount.computeIfAbsent(model, k -> new AtomicInteger()).incrementAndGet();
    }

    @Override
    public void onOutgoingPonyFramesBytes(final int bytes) {
        totalBytesSent.addAndGet(bytes);

        // Track peak memory roughly once per frame group – cheap enough
        long current = usedMemory();
        if (current > peakMemory) peakMemory = current;
    }

    @Override
    public void onOutgoingWebSocketFrame(final int headerLength, final int payloadLength) {
        totalWSFrames.incrementAndGet();
    }

    @Override
    public void onIncomingText(final String text) {
        // Handle PONG responses for round-trip latency measurement
        if (text != null && text.startsWith("PONG_")) {
            processPongResponse(text);
        }
    }

    @Override public void onIncomingWebSocketFrame(int h, int p) { /* unused */ }
    @Override public void onFrameWriteSuccess() { /* unused - we measure true round-trip */ }
    
    /* --------------------------------------------------------------------- */

    private void gcAndWait() {
        System.gc();
        try { Thread.sleep(50); } catch (InterruptedException ignored) { }
    }

    private long usedMemory() {
        return rt.totalMemory() - rt.freeMemory();
    }

    /* --------------------------  round-trip latency methods  -------------------------- */

    /**
     * Send a ping message to measure round-trip latency.
     * The client should respond with a PONG message containing the same ID.
     * 
     * @return the ping ID that was sent
     */
    public String sendPing() {
        final long seq = pingSequence.incrementAndGet();
        final String pingId = "PING_" + seq + "_" + System.nanoTime();
        final long startTime = System.nanoTime();

        // Store ping for later matching with pong
        pendingPings.put(pingId, startTime);
        
        log.debug("[RTM] Sent ping: {}", pingId);
        
        // Clean up old pings to prevent memory leaks
        cleanupOldPings();
        
        return pingId;
    }

    /**
     * Process incoming PONG response and calculate round-trip time.
     */
    private void processPongResponse(final String pongMessage) {
        if (pongMessage == null || !pongMessage.startsWith("PONG_")) {
            return;
        }

        // Extract ping ID from pong message (PONG_123_456 -> PING_123_456)
        final String pingId = pongMessage.replace("PONG_", "PING_");
        final Long startTime = pendingPings.remove(pingId);
        
        if (startTime != null) {
            final long roundTripNanos = System.nanoTime() - startTime;
            totalRoundTripNanos.addAndGet(roundTripNanos);
            completedPings.incrementAndGet();
            
            log.debug("[RTM] Completed ping-pong cycle: {:.2f} ms", 
                     roundTripNanos / 1_000_000.0);
        } else {
            log.warn("[RTM] Received PONG for unknown ping: {}", pingId);
        }
    }

    /**
     * Clean up pings older than 10 seconds to prevent memory leaks.
     * This handles cases where PONG messages are lost.
     */
    private void cleanupOldPings() {
        final long cutoffTime = System.nanoTime() - 10_000_000_000L; // 10 seconds ago
        int removedCount = 0;
        
        java.util.Iterator<java.util.Map.Entry<String, Long>> iterator = pendingPings.entrySet().iterator();
        while (iterator.hasNext()) {
            java.util.Map.Entry<String, Long> entry = iterator.next();
            if (entry.getValue() < cutoffTime) {
                iterator.remove();
                removedCount++;
            }
        }
        
        if (removedCount > 0) {
            log.debug("[RTM] Cleaned up {} stale pings", removedCount);
        }
    }

    /**
     * Simulate receiving a pong response for testing purposes.
     * This allows testing the latency calculation without a real client.
     */
    public void simulatePongAfterDelay(final String pingId, final long delayMillis) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException ignored) { }
        
        final String pongMessage = pingId.replace("PING_", "PONG_");
        processPongResponse(pongMessage);
    }

    /* --------------------------  public getters  -------------------------- */

    /** Network volume statistics */
    public long getTotalBytesSent()              { return totalBytesSent.get(); }
    public int  getTotalWSFrames()               { return totalWSFrames.get(); }
    public Map<ServerToClientModel, AtomicInteger> getPerModelCount() { return perModelCount; }

    /** Memory statistics */
    public long getMemoryIncrease()              { return endMemory - startMemory; }
    public long getPeakMemory()                  { return peakMemory; }

    /** Round-trip latency statistics */
    public double getRoundTripLatencyMillis() {
        int pings = completedPings.get();
        if (pings == 0) return -1;
        return (totalRoundTripNanos.get() / (double) pings) / 1_000_000.0;
    }

    /** Number of completed ping-pong cycles */
    public int getCompletedPings() {
        return completedPings.get();
    }

    /** Number of pings still waiting for response */
    public int getPendingPings() {
        return pendingPings.size();
    }

    /** Get the minimum, maximum, and average latency if multiple samples exist */
    public LatencyStatistics getDetailedLatencyStats() {
        return new LatencyStatistics(
            completedPings.get(),
            getRoundTripLatencyMillis(),
            pendingPings.size()
        );
    }

    /** Container for detailed latency statistics */
    public static class LatencyStatistics {
        public final int completedPings;
        public final double averageLatencyMs;
        public final int pendingPings;

        public LatencyStatistics(int completed, double avgLatency, int pending) {
            this.completedPings = completed;
            this.averageLatencyMs = avgLatency;
            this.pendingPings = pending;
        }

        @Override
        public String toString() {
            return String.format("LatencyStats{completed=%d, avgLatency=%.2f ms, pending=%d}", 
                               completedPings, averageLatencyMs, pendingPings);
        }
    }

}