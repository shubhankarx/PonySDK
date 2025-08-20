package com.ponysdk.core.server.websocket;

import static org.junit.Assert.*;

import com.ponysdk.core.model.ServerToClientModel;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Comprehensive test suite for ModelValueDictionary to verify dictionary compression
 * functionality, pattern recognition, and the HashMap key normalization fix.
 * 
 * This test addresses the critical issue where repeated button clicks weren't being stored
 * due to HashMap key type mismatches between storage and lookup operations.
 */
public class ModelValueDictionaryTest {

    private ModelValueDictionary dictionary;
    private List<ModelValuePair> buttonClickPattern;
    private List<ModelValuePair> labelUpdatePattern;
    private List<ModelValuePair> complexPattern;

    @Before
    public void setUp() {
        // Create dictionary with threshold 2 to match WebSocket.java fix
        dictionary = new ModelValueDictionary(2);
        
        // Create test patterns that mirror real WebSocket traffic
        buttonClickPattern = Arrays.asList(
            new ModelValuePair(ServerToClientModel.TYPE_UPDATE, 11),
            new ModelValuePair(ServerToClientModel.END_OF_PROCESSING, null)
        );
        
        labelUpdatePattern = Arrays.asList(
            new ModelValuePair(ServerToClientModel.TYPE_UPDATE, 10),
            new ModelValuePair(ServerToClientModel.TEXT, "Same Text Every Time")
        );
        
        complexPattern = Arrays.asList(
            new ModelValuePair(ServerToClientModel.TYPE_CREATE, 123),
            new ModelValuePair(ServerToClientModel.WIDGET_TYPE, "PButton"),
            new ModelValuePair(ServerToClientModel.TEXT, "Click Me")
        );
    }

    @Test
    public void testBasicPatternRecording() {
        // First attempt should return null (threshold not met)
        Integer id1 = dictionary.recordPattern(buttonClickPattern);
        assertNull("First recording should return null (threshold not met)", id1);
        
        // Second attempt should return pattern ID (threshold met)
        Integer id2 = dictionary.recordPattern(buttonClickPattern);
        assertNotNull("Second recording should return pattern ID", id2);
        assertTrue("Pattern ID should be positive", id2 > 0);
    }

    @Test
    public void testHashMapKeyNormalizationFix() {
        // This test verifies the critical fix for repeated button clicks not being stored
        
        // Record pattern twice to meet threshold
        dictionary.recordPattern(buttonClickPattern);
        Integer patternId = dictionary.recordPattern(buttonClickPattern);
        assertNotNull("Pattern should be recorded", patternId);
        
        // Test lookup with original pattern (Arrays.asList)
        Integer lookupId1 = dictionary.getPatternId(buttonClickPattern);
        assertEquals("Lookup with original pattern should work", patternId, lookupId1);
        
        // Test lookup with ArrayList (different List implementation)
        List<ModelValuePair> arrayListPattern = new ArrayList<>(buttonClickPattern);
        Integer lookupId2 = dictionary.getPatternId(arrayListPattern);
        assertEquals("Lookup with ArrayList should work (normalization fix)", patternId, lookupId2);
        
        // Test lookup with LinkedList (another List implementation)
        List<ModelValuePair> linkedListPattern = new LinkedList<>(buttonClickPattern);
        Integer lookupId3 = dictionary.getPatternId(linkedListPattern);
        assertEquals("Lookup with LinkedList should work (normalization fix)", patternId, lookupId3);
        
        // Test lookup with immutable list
        List<ModelValuePair> immutablePattern = Collections.unmodifiableList(new ArrayList<>(buttonClickPattern));
        Integer lookupId4 = dictionary.getPatternId(immutablePattern);
        assertEquals("Lookup with immutable list should work (normalization fix)", patternId, lookupId4);
    }

    @Test
    public void testWebSocketBatchScenario() {
        // Simulating exact WebSocket.flushCurrentBatch() behavior
        
        // Simulate WebSocket.encode() creating a currentBatch
        List<ModelValuePair> currentBatch = new ArrayList<>();
        currentBatch.add(new ModelValuePair(ServerToClientModel.TYPE_UPDATE, 11));
        currentBatch.add(new ModelValuePair(ServerToClientModel.END_OF_PROCESSING, null));
        
        // Simulate WebSocket.flushCurrentBatch() creating a snapshot
        List<ModelValuePair> snapshot = new ArrayList<>(currentBatch);
        
        assertTrue("Current batch and snapshot should be equal", currentBatch.equals(snapshot));
        assertEquals("Current batch and snapshot should have same hashCode", 
                    currentBatch.hashCode(), snapshot.hashCode());
        
        // Record pattern using snapshot (as done in flushCurrentBatch)
        dictionary.recordPattern(snapshot);
        Integer recordedId = dictionary.recordPattern(snapshot);
        
        // Try to look up using currentBatch (as done in encode method)
        Integer lookupId = dictionary.getPatternId(currentBatch);
        
        assertEquals("Lookup should work across different ArrayList instances", recordedId, lookupId);
    }

    @Test
    public void testMultiplePatternStorage() {
        // Record first pattern
        dictionary.recordPattern(buttonClickPattern);
        Integer buttonId = dictionary.recordPattern(buttonClickPattern);
        
        // Record second pattern
        dictionary.recordPattern(labelUpdatePattern);
        Integer labelId = dictionary.recordPattern(labelUpdatePattern);
        
        // Record third pattern
        dictionary.recordPattern(complexPattern);
        Integer complexId = dictionary.recordPattern(complexPattern);
        
        assertNotNull("Button pattern should be recorded", buttonId);
        assertNotNull("Label pattern should be recorded", labelId);
        assertNotNull("Complex pattern should be recorded", complexId);
        
        // All IDs should be different
        assertNotEquals("Pattern IDs should be unique", buttonId, labelId);
        assertNotEquals("Pattern IDs should be unique", buttonId, complexId);
        assertNotEquals("Pattern IDs should be unique", labelId, complexId);
        
        // Verify all patterns can be retrieved
        List<ModelValuePair> retrievedButton = dictionary.getPattern(buttonId);
        List<ModelValuePair> retrievedLabel = dictionary.getPattern(labelId);
        List<ModelValuePair> retrievedComplex = dictionary.getPattern(complexId);
        
        assertEquals("Button pattern should be retrievable", buttonClickPattern, retrievedButton);
        assertEquals("Label pattern should be retrievable", labelUpdatePattern, retrievedLabel);
        assertEquals("Complex pattern should be retrievable", complexPattern, retrievedComplex);
        
        // Check total count
        Set<Integer> patternIds = dictionary.getPatternIds();
        assertEquals("Dictionary should contain 3 patterns", 3, patternIds.size());
    }

    @Test
    public void testPatternValidation() {
        // Test null pattern
        Integer nullResult = dictionary.recordPattern(null);
        assertNull("Null pattern should return null", nullResult);
        
        // Test empty pattern
        Integer emptyResult = dictionary.recordPattern(new ArrayList<>());
        assertNull("Empty pattern should return null", emptyResult);
        
        // Test pattern without TYPE_* command (should be rejected)
        List<ModelValuePair> invalidPattern = Arrays.asList(
            new ModelValuePair(ServerToClientModel.TEXT, "Hello"),
            new ModelValuePair(ServerToClientModel.END_OF_PROCESSING, null)
        );
        
        Integer invalidResult1 = dictionary.recordPattern(invalidPattern);
        Integer invalidResult2 = dictionary.recordPattern(invalidPattern);
        assertNull("Pattern without TYPE_* command should not be recorded", invalidResult1);
        assertNull("Pattern without TYPE_* command should not be recorded", invalidResult2);
    }

    @Test
    public void testThresholdBehavior() {
        // Create dictionary with threshold 3
        ModelValueDictionary highThresholdDict = new ModelValueDictionary(3);
        
        // Record pattern twice (below threshold)
        Integer id1 = highThresholdDict.recordPattern(buttonClickPattern);
        Integer id2 = highThresholdDict.recordPattern(buttonClickPattern);
        
        assertNull("Pattern should not be recorded below threshold", id1);
        assertNull("Pattern should not be recorded below threshold", id2);
        
        // Third time should meet threshold
        Integer id3 = highThresholdDict.recordPattern(buttonClickPattern);
        assertNotNull("Pattern should be recorded when threshold is met", id3);
        
        // Fourth time should return existing ID
        Integer id4 = highThresholdDict.recordPattern(buttonClickPattern);
        assertEquals("Subsequent recordings should return existing ID", id3, id4);
    }

    @Test
    public void testConcurrentAccess() {
        final int threadCount = 10;
        final int recordsPerThread = 5;
        final List<Integer> results = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(threadCount);
        final AtomicInteger errorCount = new AtomicInteger(0);
        
        // Create threads that record the same pattern concurrently
        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    for (int j = 0; j < recordsPerThread; j++) {
                        Integer id = dictionary.recordPattern(buttonClickPattern);
                        if (id != null) {
                            results.add(id);
                        }
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Release all threads at once
        startLatch.countDown();
        
        // Wait for all threads to complete
        try {
            endLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Thread interrupted during concurrent test");
        }
        
        // Verify results
        assertEquals("No errors should occur during concurrent access", 0, errorCount.get());
        assertFalse("Some threads should have recorded the pattern", results.isEmpty());
        
        // All recorded IDs should be the same (same pattern)
        if (results.size() > 1) {
            Integer firstId = results.get(0);
            for (Integer id : results) {
                assertEquals("All concurrent recordings should return same ID", firstId, id);
            }
        }
    }

    @Test
    public void testRealWorldButtonClickScenario() {
        // Simulating 'Dictionary Test - Set Same Text' button clicks from server logs
        
        // Simulate the exact scenario from WebSocket logs:
        // User clicks "Dictionary Test" button multiple times
        List<ModelValuePair> realButtonClick = Arrays.asList(
            new ModelValuePair(ServerToClientModel.TYPE_UPDATE, 11),
            new ModelValuePair(ServerToClientModel.END_OF_PROCESSING, null)
        );
        
        // First click - not recorded (threshold not met)
        Integer click1 = dictionary.recordPattern(realButtonClick);
        assertNull("First click should not be recorded", click1);
        
        // Second click - should be recorded and assigned ID
        Integer click2 = dictionary.recordPattern(realButtonClick);
        assertNotNull("Second click should be recorded", click2);
        
        // Third click - should return existing ID (pattern already exists)
        Integer click3 = dictionary.recordPattern(realButtonClick);
        assertEquals("Third click should return existing pattern ID", click2, click3);
        
        // Verify pattern can be looked up
        Integer lookupId = dictionary.getPatternId(realButtonClick);
        assertEquals("Pattern lookup should work", click2, lookupId);
        
        // Verify pattern can be retrieved
        List<ModelValuePair> retrieved = dictionary.getPattern(click2);
        assertEquals("Retrieved pattern should match original", realButtonClick, retrieved);
    }

    @Test
    public void testPatternEquality() {
        // Create identical patterns with different list implementations
        List<ModelValuePair> pattern1 = Arrays.asList(
            new ModelValuePair(ServerToClientModel.TYPE_UPDATE, 11),
            new ModelValuePair(ServerToClientModel.TEXT, "Hello")
        );
        
        List<ModelValuePair> pattern2 = new ArrayList<>();
        pattern2.add(new ModelValuePair(ServerToClientModel.TYPE_UPDATE, 11));
        pattern2.add(new ModelValuePair(ServerToClientModel.TEXT, "Hello"));
        
        List<ModelValuePair> pattern3 = new LinkedList<>();
        pattern3.add(new ModelValuePair(ServerToClientModel.TYPE_UPDATE, 11));
        pattern3.add(new ModelValuePair(ServerToClientModel.TEXT, "Hello"));
        
        // Record first pattern
        dictionary.recordPattern(pattern1);
        Integer id1 = dictionary.recordPattern(pattern1);
        
        // All variations should return the same ID
        Integer id2 = dictionary.getPatternId(pattern2);
        Integer id3 = dictionary.getPatternId(pattern3);
        
        assertEquals("ArrayList pattern should match", id1, id2);
        assertEquals("LinkedList pattern should match", id1, id3);
    }

    @Test
    public void testDictionaryClear() {
        // Record some patterns
        dictionary.recordPattern(buttonClickPattern);
        Integer id1 = dictionary.recordPattern(buttonClickPattern);
        
        dictionary.recordPattern(labelUpdatePattern);
        Integer id2 = dictionary.recordPattern(labelUpdatePattern);
        
        assertNotNull("Patterns should be recorded before clear", id1);
        assertNotNull("Patterns should be recorded before clear", id2);
        assertEquals("Dictionary should contain 2 patterns", 2, dictionary.getPatternIds().size());
        
        // Clear dictionary
        dictionary.clear();
        
        assertEquals("Dictionary should be empty after clear", 0, dictionary.getPatternIds().size());
        
        // Verify lookups return null
        assertNull("Lookup should return null after clear", dictionary.getPatternId(buttonClickPattern));
        assertNull("Pattern retrieval should return null after clear", dictionary.getPattern(id1));
        
        // Verify we can record new patterns after clear
        Integer newId = dictionary.recordPattern(buttonClickPattern);
        assertNull("First recording after clear should return null", newId);
        
        Integer newId2 = dictionary.recordPattern(buttonClickPattern);
        assertNotNull("Second recording after clear should work", newId2);
    }

    @Test 
    public void testWebSocketIntegrationScenario() {
        // Test the exact flow that happens in WebSocket.encode() and flushCurrentBatch()
        
        // Simulate WebSocket currentBatch creation
        List<ModelValuePair> currentBatch = new ArrayList<>();
        
        // Add TYPE_UPDATE (this starts a batch in WebSocket.encode)
        ModelValuePair typeUpdate = new ModelValuePair(ServerToClientModel.TYPE_UPDATE, 11);
        currentBatch.add(typeUpdate);
        
        // Add END_OF_PROCESSING (this gets added to batch)
        ModelValuePair endProcessing = new ModelValuePair(ServerToClientModel.END_OF_PROCESSING, null);
        currentBatch.add(endProcessing);
        
        // Size should be 2, which meets our BATCH_THRESHOLD = 2
        assertEquals("Batch should have 2 elements", 2, currentBatch.size());
        
        // Simulate flushCurrentBatch() creating snapshot
        List<ModelValuePair> snapshot = new ArrayList<>(currentBatch);
        
        // Record pattern using snapshot (first time - below dictionary threshold)
        Integer firstRecord = dictionary.recordPattern(snapshot);
        assertNull("First record should return null (dictionary threshold not met)", firstRecord);
        
        // Record pattern using snapshot (second time - should meet dictionary threshold)  
        Integer secondRecord = dictionary.recordPattern(snapshot);
        assertNotNull("Second record should return pattern ID", secondRecord);
        
        // Test lookup from WebSocket.encode() using currentBatch
        Integer lookupFromBatch = dictionary.getPatternId(currentBatch);
        assertEquals("Lookup from currentBatch should find the pattern", secondRecord, lookupFromBatch);
        
        // Test lookup from different ArrayList instance (simulating different batch)
        List<ModelValuePair> differentBatch = new ArrayList<>();
        differentBatch.add(new ModelValuePair(ServerToClientModel.TYPE_UPDATE, 11));
        differentBatch.add(new ModelValuePair(ServerToClientModel.END_OF_PROCESSING, null));
        
        Integer lookupFromDifferentBatch = dictionary.getPatternId(differentBatch);
        assertEquals("Lookup from different ArrayList should find the pattern", secondRecord, lookupFromDifferentBatch);
        
        // Verify the pattern can be retrieved
        List<ModelValuePair> retrieved = dictionary.getPattern(secondRecord);
        assertEquals("Retrieved pattern should match", snapshot, retrieved);
    }
}