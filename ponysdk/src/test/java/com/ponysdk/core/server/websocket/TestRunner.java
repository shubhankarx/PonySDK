package com.ponysdk.core.server.websocket;

import com.ponysdk.core.model.ServerToClientModel;
import com.ponysdk.core.server.application.UIContext;
import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test runner that executes tasks at fixed intervals and collects metrics.
 * Uses NetworkMemoryLatencyMonitor for measuring performance.
 */
public class TestRunner {
    
    private static final Logger log = LoggerFactory.getLogger(TestRunner.class);
    
    // Test configuration
    private final List<TestTask> tasks = new ArrayList<>();
    private final NetworkMemoryLatencyMonitor monitor = new NetworkMemoryLatencyMonitor();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicInteger cycleCounter = new AtomicInteger(0);
    
    // WebSocket under test
    private WebSocket webSocket;
    
    // Test settings
    private boolean dictionaryEnabled = true;
    private boolean predictionEnabled = true;
    private int totalCycles = 10;
    private long intervalMillis = 5000; // Default 5 seconds
    
    /**
     * Create a test runner with a WebSocket instance
     * 
     * @param webSocket The WebSocket instance to test
     */
    public TestRunner(WebSocket webSocket) {
        this.webSocket = webSocket;
        
        // Attach monitor to WebSocket
        webSocket.setListener(monitor);
    }
    
    /**
     * Add a task to be executed in the test cycle
     * 
     * @param task The task to add
     * @return this TestRunner for chaining
     */
    public TestRunner addTask(TestTask task) {
        tasks.add(task);
        log.info("Added task: {} - {}", task.id(), task.description());
        return this;
    }
    
    /**
     * Configure test settings
     * 
     * @param dictionaryEnabled Whether to enable dictionary compression
     * @param predictionEnabled Whether to enable prediction
     * @return this TestRunner for chaining
     */
    public TestRunner configure(boolean dictionaryEnabled, boolean predictionEnabled) {
        this.dictionaryEnabled = dictionaryEnabled;
        this.predictionEnabled = predictionEnabled;
        
        // Apply settings to WebSocket
        webSocket.setDictionaryEnabled(dictionaryEnabled);
        // Note: prediction enabling would need to be implemented in WebSocket
        
        log.info("Test configured: dictionary={}, prediction={}", 
                dictionaryEnabled ? "ON" : "OFF", 
                predictionEnabled ? "ON" : "OFF");
        return this;
    }
    
    /**
     * Set the number of test cycles to run
     * 
     * @param cycles Number of cycles
     * @return this TestRunner for chaining
     */
    public TestRunner setCycles(int cycles) {
        this.totalCycles = cycles;
        return this;
    }
    
    /**
     * Set the interval between task executions
     * 
     * @param intervalMillis Interval in milliseconds
     * @return this TestRunner for chaining
     */
    public TestRunner setInterval(long intervalMillis) {
        this.intervalMillis = intervalMillis;
        return this;
    }
    
    /**
     * Run the test cycle
     * 
     * @return TestResults containing metrics from the test run
     * @throws InterruptedException if interrupted while waiting for completion
     */
    public TestResults run() throws InterruptedException {
        if (tasks.isEmpty()) {
            throw new IllegalStateException("No tasks defined for test cycle");
        }
        
        log.info("Starting test cycle: {} cycles, {}ms interval", totalCycles, intervalMillis);
        
        // Reset counters and start monitoring
        cycleCounter.set(0);
        monitor.start();
        
        // Use CountDownLatch to wait for all cycles to complete
        CountDownLatch completionLatch = new CountDownLatch(totalCycles);
        
        // Schedule task execution at fixed intervals
        scheduler.scheduleAtFixedRate(() -> {
            try {
                int currentCycle = cycleCounter.incrementAndGet();
                
                if (currentCycle <= totalCycles) {
                    log.info("Executing cycle {} of {}", currentCycle, totalCycles);
                    executeTaskCycle(currentCycle);
                    completionLatch.countDown();
                    
                    if (currentCycle == totalCycles) {
                        log.info("Test cycle completed");
                    }
                }
            } catch (Exception e) {
                log.error("Error in test cycle", e);
                completionLatch.countDown();
            }
        }, 0, intervalMillis, TimeUnit.MILLISECONDS);
        
        // Wait for all cycles to complete
        boolean completed = completionLatch.await(totalCycles * intervalMillis + 5000, TimeUnit.MILLISECONDS);
        if (!completed) {
            log.warn("Test cycle did not complete within expected time");
        }
        
        // Stop monitoring and collect results
        monitor.stop();
        
        // Shutdown scheduler
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Return test results
        return new TestResults(
                monitor.getTotalBytesSent(),
                monitor.getTotalWSFrames(),
                monitor.getPerModelCount(),
                monitor.getMemoryIncrease(),
                monitor.getPeakMemory(),
                monitor.getTotalLatencyMillis(),
                dictionaryEnabled,
                predictionEnabled,
                totalCycles
        );
    }
    
    /**
     * Execute one cycle of all tasks
     * 
     * @param cycleNumber Current cycle number
     */
    private void executeTaskCycle(int cycleNumber) {
        for (TestTask task : tasks) {
            try {
                log.debug("Executing task {} in cycle {}", task.id(), cycleNumber);
                task.run();
            } catch (Exception e) {
                log.error("Error executing task {} in cycle {}", task.id(), cycleNumber, e);
            }
        }
    }
    
    /**
     * Shutdown the scheduler
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Results from a test run
     */
    public static class TestResults {
        private final long totalBytesSent;
        private final int totalWSFrames;
        private final Map<ServerToClientModel, AtomicInteger> perModelCount;
        private final long memoryIncrease;
        private final long peakMemory;
        private final double totalLatencyMillis;
        private final boolean dictionaryEnabled;
        private final boolean predictionEnabled;
        private final int totalCycles;
        
        public TestResults(
                long totalBytesSent,
                int totalWSFrames,
                Map<ServerToClientModel, AtomicInteger> perModelCount,
                long memoryIncrease,
                long peakMemory,
                double totalLatencyMillis,
                boolean dictionaryEnabled,
                boolean predictionEnabled,
                int totalCycles) {
            this.totalBytesSent = totalBytesSent;
            this.totalWSFrames = totalWSFrames;
            this.perModelCount = perModelCount;
            this.memoryIncrease = memoryIncrease;
            this.peakMemory = peakMemory;
            this.totalLatencyMillis = totalLatencyMillis;
            this.dictionaryEnabled = dictionaryEnabled;
            this.predictionEnabled = predictionEnabled;
            this.totalCycles = totalCycles;
        }
        
        public long getTotalBytesSent() { return totalBytesSent; }
        public int getTotalWSFrames() { return totalWSFrames; }
        public Map<ServerToClientModel, AtomicInteger> getPerModelCount() { return perModelCount; }
        public long getMemoryIncrease() { return memoryIncrease; }
        public long getPeakMemory() { return peakMemory; }
        public double getTotalLatencyMillis() { return totalLatencyMillis; }
        public boolean isDictionaryEnabled() { return dictionaryEnabled; }
        public boolean isPredictionEnabled() { return predictionEnabled; }
        public int getTotalCycles() { return totalCycles; }
        
        /**
         * Generate a summary report of test results
         * 
         * @return Formatted report string
         */
        public String generateReport() {
            StringBuilder report = new StringBuilder();
            report.append("=== Test Results ===\n");
            report.append(String.format("Configuration: Dictionary=%s, Prediction=%s, Cycles=%d\n",
                    dictionaryEnabled ? "ON" : "OFF",
                    predictionEnabled ? "ON" : "OFF",
                    totalCycles));
            report.append(String.format("Network: %d bytes, %d frames\n", totalBytesSent, totalWSFrames));
            report.append(String.format("Memory: Increase=%d KB, Peak=%d KB\n", 
                    memoryIncrease / 1024, peakMemory / 1024));
            report.append(String.format("Latency: %.2f ms total\n", totalLatencyMillis));
            
            if (!perModelCount.isEmpty()) {
                report.append("Frame Types:\n");
                perModelCount.entrySet().stream()
                        .sorted((e1, e2) -> Integer.compare(e2.getValue().get(), e1.getValue().get()))
                        .forEach(entry -> report.append(String.format("  %s: %d\n", 
                                entry.getKey(), entry.getValue().get())));
            }
            
            return report.toString();
        }
    }
} 