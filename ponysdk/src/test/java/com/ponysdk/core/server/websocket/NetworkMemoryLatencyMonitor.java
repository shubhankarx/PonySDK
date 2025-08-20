package com.ponysdk.core.server.websocket;

import com.ponysdk.core.model.ServerToClientModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A minimal, **server-side only** instrumentation helper that can be plugged into any
 * {@link WebSocket} instance by calling {@link WebSocket#setListener(WebSocket.Listener)}.
 * It implements {@link WebSocket.Listener} so it will be notified for every model/value pair
 * as they are encoded as well as for the raw WebSocket frame sizes recorded by
 * {@link WebSocketPusher}.  
 * <ul>
 *   <li><b>Network volume</b> – bytes actually flushed on the {@code Session}.</li>
 *   <li><b>Frame distribution</b> – how many times each {@link ServerToClientModel} appears.</li>
 *   <li><b>Latency</b> – very coarse, server-side time elapsed between the first byte sent
 *       and Jetty’s confirmation callback.</li>
 *   <li><b>Memory usage</b> – heap deltas during the test run.</li>
 * </ul>
 *
 * 
 */
public final class NetworkMemoryLatencyMonitor implements WebSocket.Listener {

    private static final Logger log = LoggerFactory.getLogger(NetworkMemoryLatencyMonitor.class);

    /* ---------- Network counters ---------- */
    private final AtomicLong totalBytesSent   = new AtomicLong();
    private final AtomicInteger totalWSFrames = new AtomicInteger();
    private final Map<ServerToClientModel, AtomicInteger> perModelCount = new ConcurrentHashMap<>();

    /* ---------- Latency measurements ---------- */
    private final AtomicLong firstOutboundNano = new AtomicLong(-1);
    private final AtomicLong lastAckNano       = new AtomicLong(-1);

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
        log.info("[MON] Start memory = {} KB", startMemory / 1024);
    }

    /** Call once right after the test ends. */
    public void stop() {
        gcAndWait();
        endMemory = usedMemory();
        log.info("[MON] End memory = {} KB (Δ = {} KB)", endMemory / 1024, (endMemory - startMemory) / 1024);
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
        long now = System.nanoTime();
        if (firstOutboundNano.compareAndSet(-1, now)) {
            // first frame of the test – mark start time
        }
        totalBytesSent.addAndGet(bytes);

        // Track peak memory roughly once per frame group – cheap enough
        long current = usedMemory();
        if (current > peakMemory) peakMemory = current;
    }

    @Override
    public void onOutgoingWebSocketFrame(final int headerLength, final int payloadLength) {
        totalWSFrames.incrementAndGet();
    }

    @Override public void onIncomingText(final String text)                { /* unused in this simple harness */ }
    @Override public void onIncomingWebSocketFrame(int h, int p)           { /* ditto */ }

    @Override
    public void onFrameWriteSuccess() {
        // Update the last acknowledgment time when frames are successfully written
        lastAckNano.set(System.nanoTime());
    }
    n
    /* --------------------------------------------------------------------- */

    private void gcAndWait() {
        System.gc();
        try { Thread.sleep(50); } catch (InterruptedException ignored) { }
    }

    private long usedMemory() {
        return rt.totalMemory() - rt.freeMemory();
    }

    /* --------------------------  public getters  -------------------------- */
    public long getTotalBytesSent()              { return totalBytesSent.get(); }
    public int  getTotalWSFrames()               { return totalWSFrames.get(); }
    public Map<ServerToClientModel, AtomicInteger> getPerModelCount() { return perModelCount; }
    public long getMemoryIncrease()              { return endMemory - startMemory; }
    public long getPeakMemory()                  { return peakMemory; }

    /** Very coarse latency: time between first encode and Jetty ack of the very last WS frame. */
    public double getTotalLatencyMillis() {
        long start = firstOutboundNano.get();
        long end   = lastAckNano.get();
        if (start <= 0 || end <= 0) return -1;
        return (end - start) / 1_000_000.0;
    }

} 