package com.ponysdk.core.server.websocket;

import com.ponysdk.core.server.application.UIContext;
import com.ponysdk.core.server.stm.TxnContext;
import org.eclipse.jetty.websocket.api.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Performance tests for WebSocket with dictionary compression.
 * Uses the TestRunner and NetworkMemoryLatencyMonitor to measure performance.
 */
public class WebSocketPerformanceTest {
    
    private WebSocket webSocket;
    private Session mockSession;
    private UIContext mockUIContext;
    private TestRunner testRunner;
    private NetworkMemoryLatencyMonitor monitor;
    
    @BeforeEach
    public void setUp() {
        // Create mocks for WebSocket dependencies
        mockSession = Mockito.mock(Session.class);
        Mockito.when(mockSession.isOpen()).thenReturn(true);
        
        mockUIContext = Mockito.mock(UIContext.class);
        Mockito.when(mockUIContext.getID()).thenReturn(1);
        
        // Create WebSocket instance
        webSocket = new WebSocket();
        webSocket.setContext(Mockito.mock(TxnContext.class));
        
        // Initialize WebSocket
        webSocket.onWebSocketConnect(mockSession);
        
        // Set UIContext field via reflection (since it's private)
        try {
            java.lang.reflect.Field uiContextField = WebSocket.class.getDeclaredField("uiContext");
            uiContextField.setAccessible(true);
            uiContextField.set(webSocket, mockUIContext);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set UIContext field", e);
        }
        
        // Create monitor and test runner
        monitor = new NetworkMemoryLatencyMonitor();
        webSocket.setListener(monitor);
        testRunner = new TestRunner(webSocket);
    }
    
    @AfterEach
    public void tearDown() {
        if (testRunner != null) {
            testRunner.shutdown();
        }
    }
    
    @Test
    public void testTextToggleWithDictionaryEnabled() throws InterruptedException {
        // Configure test with dictionary enabled
        testRunner.configure(true, false)
                .setCycles(5)
                .setInterval(100); // Use short interval for unit tests
        
        // Add text toggle task
        testRunner.addTask(new TextToggleTask(webSocket, "text1", "text2", 100));
        
        // Start monitoring
        monitor.start();
        
        // Run test
        TestRunner.TestResults results = testRunner.run();
        
        // Stop monitoring
        monitor.stop();
        
        // Print report
        System.out.println("=== Dictionary ENABLED Results ===");
        System.out.println(results.generateReport());
    }
    
    @Test
    public void testTextToggleWithDictionaryDisabled() throws InterruptedException {
        // Configure test with dictionary disabled
        testRunner.configure(false, false)
                .setCycles(5)
                .setInterval(100); // Use short interval for unit tests
        
        // Add text toggle task
        testRunner.addTask(new TextToggleTask(webSocket, "text1", "text2", 100));
        
        // Start monitoring
        monitor.start();
        
        // Run test
        TestRunner.TestResults results = testRunner.run();
        
        // Stop monitoring
        monitor.stop();
        
        // Print report
        System.out.println("=== Dictionary DISABLED Results ===");
        System.out.println(results.generateReport());
    }
    
    @Test
    public void testComparisonDictionaryOnVsOff() throws InterruptedException {
        // Run with dictionary enabled
        testRunner.configure(true, false)
                .setCycles(10)
                .setInterval(50);
        testRunner.addTask(new TextToggleTask(webSocket, "test_text_1", "test_text_2", 200));
        
        monitor.start();
        TestRunner.TestResults resultsOn = testRunner.run();
        monitor.stop();
        
        long bytesWithDictionary = monitor.getTotalBytesSent();
        int framesWithDictionary = monitor.getTotalWSFrames();
        double latencyWithDictionary = monitor.getTotalLatencyMillis();
        
        // Reset WebSocket state
        setUp();
        
        // Run with dictionary disabled
        testRunner.configure(false, false)
                .setCycles(10)
                .setInterval(50);
        testRunner.addTask(new TextToggleTask(webSocket, "test_text_1", "test_text_2", 200));
        
        monitor.start();
        TestRunner.TestResults resultsOff = testRunner.run();
        monitor.stop();
        
        long bytesWithoutDictionary = monitor.getTotalBytesSent();
        int framesWithoutDictionary = monitor.getTotalWSFrames();
        double latencyWithoutDictionary = monitor.getTotalLatencyMillis();
        
        // Compare results
        System.out.println("=== COMPARISON RESULTS ===");
        System.out.println(String.format("Dictionary ON:  %d bytes, %d frames, %.2fms latency", 
                bytesWithDictionary, framesWithDictionary, latencyWithDictionary));
        System.out.println(String.format("Dictionary OFF: %d bytes, %d frames, %.2fms latency", 
                bytesWithoutDictionary, framesWithoutDictionary, latencyWithoutDictionary));
        
        if (bytesWithDictionary < bytesWithoutDictionary) {
            double savingsPercent = ((double)(bytesWithoutDictionary - bytesWithDictionary) 
                    / bytesWithoutDictionary) * 100;
            System.out.println(String.format("Dictionary saved %.1f%% bytes", savingsPercent));
        }
    }
} 