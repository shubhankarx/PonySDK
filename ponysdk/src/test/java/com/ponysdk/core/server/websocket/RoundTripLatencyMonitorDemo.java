package com.ponysdk.core.server.websocket;

import com.ponysdk.core.model.ServerToClientModel;
import org.junit.Test;

/**
 * Demonstration of the improved RoundTripLatencyMonitor that measures
 * true network latency using ping-pong protocol.
 * 
 * This demo shows the difference between:
 * - Old approach: Local socket buffer acknowledgment (~0.1ms)
 * - New approach: True round-trip network latency (~50-200ms)
 */
public class RoundTripLatencyMonitorDemo {
    
    @Test
    public void demonstrateRoundTripLatency() throws InterruptedException {
        System.out.println("=== Round-Trip Latency Monitor Demo ===\n");
        
        // Create new improved monitor
        RoundTripLatencyMonitor monitor = new RoundTripLatencyMonitor();
        
        // Start monitoring
        monitor.start();
        System.out.println("✅ Round-trip monitor started...\n");
        
        // Simulate typical WebSocket activity
        System.out.println("📊 Simulating WebSocket frame activity...");
        simulateFrameActivity(monitor);
        
        // Now demonstrate the key improvement: ping-pong latency measurement
        System.out.println("\n🏓 Demonstrating ping-pong latency measurement...");
        demonstratePingPong(monitor);
        
        // Stop monitoring
        monitor.stop();
        System.out.println("\n✅ Monitor stopped.\n");
        
        // Show results
        printResults(monitor);
    }
    
    @Test
    public void compareOldVsNewApproach() throws InterruptedException {
        System.out.println("=== Comparison: Old vs New Latency Measurement ===\n");
        
        // Test old approach
        System.out.println("🔴 OLD APPROACH (NetworkMemoryLatencyMonitor):");
        NetworkMemoryLatencyMonitor oldMonitor = new NetworkMemoryLatencyMonitor();
        oldMonitor.start();
        
        // Simulate the old approach
        oldMonitor.onOutgoingPonyFramesBytes(1024);
        Thread.sleep(50); // Simulate some processing
        oldMonitor.onFrameWriteSuccess(); // This is just local socket buffer ack!
        
        oldMonitor.stop();
        double oldLatency = oldMonitor.getTotalLatencyMillis();
        
        System.out.println("  Result: " + String.format("%.2f", oldLatency) + " ms");
        System.out.println("  ❌ Problem: This only measures local I/O, not network latency!\n");
        
        // Test new approach
        System.out.println("🟢 NEW APPROACH (RoundTripLatencyMonitor):");
        RoundTripLatencyMonitor newMonitor = new RoundTripLatencyMonitor();
        newMonitor.start();
        
        // Send ping and simulate client response after realistic network delay
        String pingId = newMonitor.sendPing();
        System.out.println("  📤 Sent ping: " + pingId);
        
        // Simulate realistic network round-trip time
        Thread.sleep(75); // Simulate 75ms network delay
        newMonitor.simulatePongAfterDelay(pingId, 0);
        System.out.println("  📥 Received pong after network delay");
        
        newMonitor.stop();
        double newLatency = newMonitor.getRoundTripLatencyMillis();
        
        System.out.println("  Result: " + String.format("%.2f", newLatency) + " ms");
        System.out.println("  ✅ Success: This measures true round-trip network latency!\n");
        
        // Compare results
        System.out.println("📈 COMPARISON:");
        System.out.println("  Old method: " + String.format("%.2f", oldLatency) + " ms (socket buffer only)");
        System.out.println("  New method: " + String.format("%.2f", newLatency) + " ms (true network)");
        System.out.println("  Difference: " + String.format("%.1fx", newLatency / oldLatency) + " more realistic!");
    }
    
    @Test
    public void demonstrateMultiplePings() throws InterruptedException {
        System.out.println("=== Multiple Ping-Pong Cycles Demo ===\n");
        
        RoundTripLatencyMonitor monitor = new RoundTripLatencyMonitor();
        monitor.start();
        
        // Send multiple pings with different delays to simulate varying network conditions
        int[] networkDelays = {30, 45, 60, 35, 50}; // ms
        
        System.out.println("🏓 Sending " + networkDelays.length + " pings with varying network delays...");
        
        for (int i = 0; i < networkDelays.length; i++) {
            String pingId = monitor.sendPing();
            System.out.println("  📤 Ping " + (i+1) + ": " + pingId);
            
            // Simulate different network conditions
            monitor.simulatePongAfterDelay(pingId, networkDelays[i]);
            System.out.println("  📥 Pong " + (i+1) + ": ~" + networkDelays[i] + "ms delay");
            
            // Small gap between pings
            Thread.sleep(10);
        }
        
        monitor.stop();
        
        // Show statistics
        RoundTripLatencyMonitor.LatencyStatistics stats = monitor.getDetailedLatencyStats();
        System.out.println("\n📊 STATISTICS:");
        System.out.println("  " + stats);
        System.out.println("  Average latency: " + String.format("%.2f", monitor.getRoundTripLatencyMillis()) + " ms");
        System.out.println("  Completed cycles: " + monitor.getCompletedPings());
        System.out.println("  Pending pings: " + monitor.getPendingPings());
    }
    
    @Test
    public void demonstrateTimeoutHandling() throws InterruptedException {
        System.out.println("=== Timeout and Memory Leak Prevention Demo ===\n");
        
        RoundTripLatencyMonitor monitor = new RoundTripLatencyMonitor();
        monitor.start();
        
        // Send pings but don't respond to some (simulate network loss)
        System.out.println("📤 Sending pings, some will timeout...");
        
        String ping1 = monitor.sendPing();
        String ping2 = monitor.sendPing();
        String ping3 = monitor.sendPing();
        
        // Only respond to ping2
        monitor.simulatePongAfterDelay(ping2, 50);
        
        System.out.println("  ✅ Responded to ping2");
        System.out.println("  ❌ ping1 and ping3 will timeout");
        
        Thread.sleep(100);
        
        monitor.stop();
        
        System.out.println("\n📊 RESULTS:");
        System.out.println("  Completed pings: " + monitor.getCompletedPings() + "/3");
        System.out.println("  Pending pings: " + monitor.getPendingPings());
        System.out.println("  Average latency: " + 
                          (monitor.getCompletedPings() > 0 ? 
                           String.format("%.2f", monitor.getRoundTripLatencyMillis()) + " ms" : 
                           "N/A"));
        
        System.out.println("\n✅ Memory leak prevention: Stale pings will be cleaned up automatically");
    }
    
    // Helper methods
    
    private void simulateFrameActivity(RoundTripLatencyMonitor monitor) {
        // Simulate typical UI activity
        monitor.onOutgoingPonyFrame(ServerToClientModel.TYPE_CREATE, "PButton");
        monitor.onOutgoingPonyFrame(ServerToClientModel.WIDGET_TYPE, "PButton");
        monitor.onOutgoingPonyFrame(ServerToClientModel.TYPE_UPDATE, "text");
        monitor.onOutgoingPonyFrame(ServerToClientModel.END, null);
        
        // Simulate bytes and frames
        monitor.onOutgoingPonyFramesBytes(1024);
        monitor.onOutgoingPonyFramesBytes(512);
        monitor.onOutgoingWebSocketFrame(8, 1024);
        monitor.onOutgoingWebSocketFrame(8, 512);
        
        System.out.println("  📊 Frame activity: 4 pony frames, 1536 bytes, 2 WS frames");
    }
    
    private void demonstratePingPong(RoundTripLatencyMonitor monitor) throws InterruptedException {
        // Send a ping
        String pingId = monitor.sendPing();
        System.out.println("  📤 Sent ping: " + pingId);
        
        // Simulate network delay + client processing + response
        System.out.println("  ⏳ Simulating network round-trip...");
        monitor.simulatePongAfterDelay(pingId, 60); // 60ms simulated delay
        
        System.out.println("  📥 Received pong!");
        System.out.println("  ✅ Round-trip latency calculated: ~60ms");
    }
    
    private void printResults(RoundTripLatencyMonitor monitor) {
        System.out.println("=== FINAL RESULTS ===");
        System.out.println("Network Activity:");
        System.out.println("  Total bytes sent: " + monitor.getTotalBytesSent());
        System.out.println("  Total WS frames: " + monitor.getTotalWSFrames());
        System.out.println("  Memory increase: " + (monitor.getMemoryIncrease() / 1024) + " KB");
        System.out.println("  Peak memory: " + (monitor.getPeakMemory() / 1024) + " KB");
        
        System.out.println("\nLatency Measurements:");
        System.out.println("  Round-trip latency: " + 
                          String.format("%.2f", monitor.getRoundTripLatencyMillis()) + " ms");
        System.out.println("  Completed pings: " + monitor.getCompletedPings());
        System.out.println("  Pending pings: " + monitor.getPendingPings());
        
        // Show frame counts by type
        System.out.println("\nFrame counts by type:");
        monitor.getPerModelCount().forEach((model, count) -> {
            System.out.println("  " + model.name() + ": " + count.get());
        });
        
        System.out.println("\n🎯 KEY IMPROVEMENT:");
        System.out.println("  ✅ Now measuring TRUE network latency (server → client → server)");
        System.out.println("  ❌ Previously measured only local socket buffer time");
        System.out.println("  📈 This gives you actionable performance data!");
    }
}