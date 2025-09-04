package com.ponysdk.core.server.websocket;

import com.ponysdk.core.model.ServerToClientModel;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * LatencyTrackerDemo - Shows real-world integration with PonySDK WebSocket.
 * 
 * This demo shows:
 * 1. How to attach LatencyTracker to WebSocket
 * 2. Simulate real UI updates  
 * 3. Get live latency measurements
 */
public class LatencyTrackerDemo {
    
    @Test
    public void demonstrateRealtimeLatencyTracking() throws InterruptedException {
        System.out.println("=== Real-time Latency Tracking Demo ===\n");
        
        // Create tracker
        LatencyTracker tracker = new LatencyTracker();
        
        // Simulate WebSocket activity with realistic UI patterns
        simulateUIUpdates(tracker, 100); // 100 UI updates
        
        // Get and display results
        LatencyTracker.LatencyStats stats = tracker.getStats();
        System.out.println("Results: " + stats);
        
        // Verify tracking is working
        assert stats.totalWrites > 0 : "No writes tracked";
        assert stats.p50Ms >= 0 : "Invalid median latency";
        
        System.out.println("\n✅ Latency tracking is working!");
    }
    
    @Test
    public void demonstrateLiveMonitoring() throws InterruptedException {
        System.out.println("=== Live Monitoring Demo ===\n");
        
        LatencyTracker tracker = new LatencyTracker();
        
        // Schedule live reporting
        ScheduledExecutorService reporter = Executors.newSingleThreadScheduledExecutor();
        reporter.scheduleAtFixedRate(() -> {
            LatencyTracker.LatencyStats stats = tracker.getStats();
            System.out.printf("[%d] %s%n", System.currentTimeMillis(), stats);
        }, 0, 500, TimeUnit.MILLISECONDS); // Report every 500ms
        
        // Simulate continuous activity for 3 seconds
        long endTime = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < endTime) {
            simulateUIUpdates(tracker, 10);
            Thread.sleep(200);
        }
        
        reporter.shutdown();
        System.out.println("\n✅ Live monitoring complete!");
    }
    
    @Test
    public void demonstrateWebSocketIntegration() {
        System.out.println("=== WebSocket Integration Example ===\n");
        
        // This is how you'd integrate in real PonySDK code:
        System.out.println("// In your WebSocket setup code:");
        System.out.println("WebSocket webSocket = new WebSocket();");
        System.out.println("LatencyTracker tracker = new LatencyTracker();");
        System.out.println("webSocket.setListener(tracker);");
        System.out.println("");
        System.out.println("// Get stats anytime:");
        System.out.println("LatencyTracker.LatencyStats stats = tracker.getStats();");
        System.out.println("logger.info(\"WebSocket latency: {}\", stats);");
        System.out.println("");
        System.out.println("✅ Integration is just 3 lines of code!");
    }
    
    /**
     * Simulate realistic UI update patterns.
     * Mimics button clicks, form updates, table refreshes, etc.
     */
    private void simulateUIUpdates(LatencyTracker tracker, int count) throws InterruptedException {
        for (int i = 0; i < count; i++) {
            // Simulate typical UI update sequence:
            
            // 1. Start with TYPE_UPDATE (widget modification)
            tracker.onOutgoingPonyFrame(ServerToClientModel.TYPE_UPDATE, i);
            
            // 2. Widget content (text, value, etc.)
            if (i % 3 == 0) {
                tracker.onOutgoingPonyFrame(ServerToClientModel.TEXT, "Button " + i);
            } else if (i % 3 == 1) {
                tracker.onOutgoingPonyFrame(ServerToClientModel.VALUE, "Value " + i);
            } else {
                tracker.onOutgoingPonyFrame(ServerToClientModel.WIDGET_ID, i);
            }
            
            // 3. End marker
            tracker.onOutgoingPonyFrame(ServerToClientModel.END, null);
            
            // 4. Simulate bytes being written (realistic sizes)
            int bytes = 50 + (i % 200); // 50-250 bytes per update
            tracker.onOutgoingPonyFramesBytes(bytes);
            
            // 5. Simulate processing delay (0-5ms)
            if (i % 10 == 0) {
                Thread.sleep(1 + (i % 5));
            }
            
            // 6. Network acknowledges write
            tracker.onFrameWriteSuccess();
            
            // Small delay between updates (realistic user interaction rate)
            if (count > 50) {
                Thread.sleep(1); // High frequency for stress test
            } else {
                Thread.sleep(10); // Normal user interaction rate
            }
        }
    }
}