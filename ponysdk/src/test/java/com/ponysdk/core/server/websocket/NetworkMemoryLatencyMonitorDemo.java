package com.ponysdk.core.server.websocket;

import com.ponysdk.core.model.ServerToClientModel;
import org.junit.Test;

/**
 * Simple demonstration of NetworkMemoryLatencyMonitor
 */
public class NetworkMemoryLatencyMonitorDemo {
    
    @Test
    public void demonstrateMonitor() throws InterruptedException {
        System.out.println("=== NetworkMemoryLatencyMonitor Demo ===\n");
        
        // Create monitor
        NetworkMemoryLatencyMonitor monitor = new NetworkMemoryLatencyMonitor();
        
        // Start monitoring
        monitor.start();
        System.out.println("Monitor started...");
        
        // Simulate some frame activity
        System.out.println("Simulating WebSocket activity...");
        
        // Simulate outgoing frames
        monitor.onOutgoingPonyFrame(ServerToClientModel.TYPE_CREATE, "PButton");
        monitor.onOutgoingPonyFrame(ServerToClientModel.WIDGET_TYPE, "PButton");
        monitor.onOutgoingPonyFrame(ServerToClientModel.TYPE_UPDATE, "text");
        monitor.onOutgoingPonyFrame(ServerToClientModel.END, null);
        
        // Simulate bytes being sent
        monitor.onOutgoingPonyFramesBytes(1024);
        monitor.onOutgoingPonyFramesBytes(512);
        
        // Simulate WebSocket frames
        monitor.onOutgoingWebSocketFrame(8, 1024);
        monitor.onOutgoingWebSocketFrame(8, 512);
        
        // Wait a bit to simulate processing time
        Thread.sleep(100);
        
        // Simulate frame acknowledgments (this is what we fixed!)
        monitor.onFrameWriteSuccess();
        monitor.onFrameWriteSuccess();
        
        // Wait a bit more
        Thread.sleep(50);
        
        // Stop monitoring
        monitor.stop();
        System.out.println("Monitor stopped.\n");
        
        // Print results
        System.out.println("=== RESULTS ===");
        System.out.println("Total bytes sent: " + monitor.getTotalBytesSent());
        System.out.println("Total WS frames: " + monitor.getTotalWSFrames());
        System.out.println("Memory increase: " + (monitor.getMemoryIncrease() / 1024) + " KB");
        System.out.println("Peak memory: " + (monitor.getPeakMemory() / 1024) + " KB");
        System.out.println("Total latency: " + String.format("%.2f", monitor.getTotalLatencyMillis()) + " ms");
        
        // Show frame counts by type
        System.out.println("\nFrame counts by type:");
        monitor.getPerModelCount().forEach((model, count) -> {
            System.out.println("  " + model.name() + ": " + count.get());
        });
        
        // Verify latency is now working (should not be -1)
        double latency = monitor.getTotalLatencyMillis();
        if (latency >= 0) {
            System.out.println("\n✅ SUCCESS: Latency measurement is working! (" + 
                             String.format("%.2f", latency) + " ms)");
        } else {
            System.out.println("\n❌ FAILED: Latency measurement still returning -1");
        }
    }
    
    @Test
    public void demonstrateLatencyMeasurement() throws InterruptedException {
        System.out.println("\n=== Latency Measurement Test ===");
        
        NetworkMemoryLatencyMonitor monitor = new NetworkMemoryLatencyMonitor();
        monitor.start();
        
        System.out.println("Sending frame...");
        monitor.onOutgoingPonyFramesBytes(100);
        
        // Simulate some processing delay
        Thread.sleep(50);
        
        System.out.println("Frame acknowledged...");
        monitor.onFrameWriteSuccess();
        
        monitor.stop();
        
        double latency = monitor.getTotalLatencyMillis();
        System.out.println("Measured latency: " + String.format("%.2f", latency) + " ms");
        System.out.println("Expected: approximately 50ms (due to Thread.sleep)");
        
        if (latency >= 40 && latency <= 100) {
            System.out.println("✅ Latency measurement is accurate!");
        } else if (latency == -1) {
            System.out.println("❌ Latency measurement not working (returning -1)");
        } else {
            System.out.println("⚠️  Latency measurement seems off, but at least not -1");
        }
    }
}

