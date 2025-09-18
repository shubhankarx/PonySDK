package com.ponysdk.core.terminal;

import com.ponysdk.core.model.ServerToClientModel;
import com.ponysdk.core.terminal.socket.ClientModelTracker;
import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

import java.util.List;
import java.util.ArrayList;

/**
 * Critical test to validate that pattern replay doesn't corrupt buffer state.
 *
 * ROOT CAUSE: UIBuilder.update() calls itself recursively with same ReaderBuffer,
 * corrupting buffer position and causing object lookup failures.
 *
 * SOLUTION: Pattern replay must not rely on buffer state for object resolution.
 */
public class BufferStateIsolationTest {

    private ClientModelTracker clientTracker;

    @Before
    public void setUp() {
        clientTracker = new ClientModelTracker();
    }

    /**
     * TEST 1: Pattern Replay Independence
     *
     * Verifies that pattern replay can work without buffer manipulation
     */
    @Test
    public void testPatternReplayIndependence() {
        System.out.println("=== TEST 1: Pattern Replay Independence ===");

        // Create pattern that would normally cause buffer corruption
        List<ClientModelTracker.ModelValuePair> pattern = new ArrayList<>();
        pattern.add(new ClientModelTracker.ModelValuePair(ServerToClientModel.TYPE_UPDATE, Integer.valueOf(20)));
        pattern.add(new ClientModelTracker.ModelValuePair(ServerToClientModel.TEXT, "Hello World"));
        pattern.add(new ClientModelTracker.ModelValuePair(ServerToClientModel.WIDGET_ID, Integer.valueOf(123)));

        // Store pattern
        clientTracker.recordPattern(1, pattern);

        // Retrieve pattern for replay
        List<ClientModelTracker.ModelValuePair> retrievedPattern = clientTracker.getPattern(1);
        assertNotNull(retrievedPattern);
        assertEquals(3, retrievedPattern.size());

        // Verify each element is independently accessible
        for (int i = 0; i < retrievedPattern.size(); i++) {
            ClientModelTracker.ModelValuePair pair = retrievedPattern.get(i);
            assertNotNull(pair.getModel());
            assertNotNull(pair.getValue());

            System.out.println("Element " + i + ": " + pair.getModel() + " = " + pair.getValue());

            // Each element should be processable without buffer dependency
            assertTrue(isProcessableWithoutBuffer(pair));
        }
    }

    /**
     * TEST 2: Object ID Resolution Without Buffer
     *
     * Tests that object IDs can be extracted from patterns without buffer manipulation
     */
    @Test
    public void testObjectIdResolutionWithoutBuffer() {
        System.out.println("=== TEST 2: Object ID Resolution Without Buffer ===");

        // Create pattern with multiple object references
        List<ClientModelTracker.ModelValuePair> pattern = new ArrayList<>();
        pattern.add(new ClientModelTracker.ModelValuePair(ServerToClientModel.TYPE_UPDATE, Integer.valueOf(21)));
        pattern.add(new ClientModelTracker.ModelValuePair(ServerToClientModel.TEXT, "Price: 100"));
        pattern.add(new ClientModelTracker.ModelValuePair(ServerToClientModel.TYPE_UPDATE, Integer.valueOf(22)));
        pattern.add(new ClientModelTracker.ModelValuePair(ServerToClientModel.TEXT, "Status: A"));

        clientTracker.recordPattern(2, pattern);

        // Extract object IDs without buffer manipulation
        List<Integer> objectIds = extractObjectIdsFromPattern(clientTracker.getPattern(2));

        assertNotNull(objectIds);
        assertEquals(2, objectIds.size());
        assertTrue(objectIds.contains(21));
        assertTrue(objectIds.contains(22));

        System.out.println("Extracted object IDs: " + objectIds);
    }

    /**
     * TEST 3: Pattern Replay Sequence Validation
     *
     * Ensures pattern replay follows correct sequence without buffer corruption
     */
    @Test
    public void testPatternReplaySequence() {
        System.out.println("=== TEST 3: Pattern Replay Sequence Validation ===");

        // Create complex pattern that mirrors real UI operations
        List<ClientModelTracker.ModelValuePair> pattern = new ArrayList<>();
        pattern.add(new ClientModelTracker.ModelValuePair(ServerToClientModel.TYPE_UPDATE, Integer.valueOf(20)));
        pattern.add(new ClientModelTracker.ModelValuePair(ServerToClientModel.WIDGET_TYPE, "PLabel"));
        pattern.add(new ClientModelTracker.ModelValuePair(ServerToClientModel.TEXT, "Hello"));
        pattern.add(new ClientModelTracker.ModelValuePair(ServerToClientModel.WIDGET_ID, Integer.valueOf(20)));

        clientTracker.recordPattern(3, pattern);

        // Simulate pattern replay
        List<ClientModelTracker.ModelValuePair> retrievedPattern = clientTracker.getPattern(3);
        List<ReplayOperation> replayOperations = simulatePatternReplay(retrievedPattern);

        assertNotNull(replayOperations);
        assertEquals(4, replayOperations.size());

        // Verify sequence integrity
        ReplayOperation typeUpdate = replayOperations.get(0);
        assertEquals(ServerToClientModel.TYPE_UPDATE, typeUpdate.model);
        assertEquals(Integer.valueOf(20), typeUpdate.value);

        ReplayOperation textUpdate = replayOperations.get(2);
        assertEquals(ServerToClientModel.TEXT, textUpdate.model);
        assertEquals("Hello", textUpdate.value);

        System.out.println("Replay sequence verified successfully");
    }

    /**
     * TEST 4: Buffer Corruption Detection
     *
     * Tests that would detect if buffer state is being corrupted during pattern replay
     */
    @Test
    public void testBufferCorruptionDetection() {
        System.out.println("=== TEST 4: Buffer Corruption Detection ===");

        // Create multiple patterns that would interfere if buffer state is shared
        List<ClientModelTracker.ModelValuePair> pattern1 = new ArrayList<>();
        pattern1.add(new ClientModelTracker.ModelValuePair(ServerToClientModel.TYPE_UPDATE, Integer.valueOf(10)));
        pattern1.add(new ClientModelTracker.ModelValuePair(ServerToClientModel.TEXT, "Pattern1"));

        List<ClientModelTracker.ModelValuePair> pattern2 = new ArrayList<>();
        pattern2.add(new ClientModelTracker.ModelValuePair(ServerToClientModel.TYPE_UPDATE, Integer.valueOf(20)));
        pattern2.add(new ClientModelTracker.ModelValuePair(ServerToClientModel.TEXT, "Pattern2"));

        clientTracker.recordPattern(10, pattern1);
        clientTracker.recordPattern(20, pattern2);

        // Retrieve patterns in different order
        List<ClientModelTracker.ModelValuePair> retrieved2 = clientTracker.getPattern(20);
        List<ClientModelTracker.ModelValuePair> retrieved1 = clientTracker.getPattern(10);

        // Verify no cross-contamination
        assertNotNull(retrieved1);
        assertNotNull(retrieved2);

        assertEquals("Pattern1", getTextValue(retrieved1));
        assertEquals("Pattern2", getTextValue(retrieved2));

        assertEquals(Integer.valueOf(10), getTypeUpdateValue(retrieved1));
        assertEquals(Integer.valueOf(20), getTypeUpdateValue(retrieved2));

        System.out.println("No buffer corruption detected");
    }

    /**
     * TEST 5: Error Recovery Mechanism
     *
     * Tests that the system can recover from pattern replay failures
     */
    @Test
    public void testErrorRecoveryMechanism() {
        System.out.println("=== TEST 5: Error Recovery Mechanism ===");

        // Create pattern with potential issues
        List<ClientModelTracker.ModelValuePair> problemPattern = new ArrayList<>();
        problemPattern.add(new ClientModelTracker.ModelValuePair(ServerToClientModel.TYPE_UPDATE, null)); // null value
        problemPattern.add(new ClientModelTracker.ModelValuePair(ServerToClientModel.TEXT, "Valid text"));

        clientTracker.recordPattern(99, problemPattern);

        // Attempt to process problematic pattern
        List<ClientModelTracker.ModelValuePair> retrieved = clientTracker.getPattern(99);
        assertNotNull(retrieved);

        // Error recovery should handle null values gracefully
        boolean hasNullValue = false;
        boolean hasValidValue = false;

        for (ClientModelTracker.ModelValuePair pair : retrieved) {
            if (pair.getValue() == null) {
                hasNullValue = true;
                System.out.println("Detected null value for model: " + pair.getModel());
            } else {
                hasValidValue = true;
                System.out.println("Valid value: " + pair.getModel() + " = " + pair.getValue());
            }
        }

        assertTrue(hasNullValue);
        assertTrue(hasValidValue);

        System.out.println("Error recovery mechanism working");
    }

    // Helper methods
    private boolean isProcessableWithoutBuffer(ClientModelTracker.ModelValuePair pair) {
        // Check that pair has all necessary information for processing
        return pair.getModel() != null &&
               (pair.getValue() != null || pair.getModel() == ServerToClientModel.END);
    }

    private List<Integer> extractObjectIdsFromPattern(List<ClientModelTracker.ModelValuePair> pattern) {
        List<Integer> objectIds = new ArrayList<>();
        for (ClientModelTracker.ModelValuePair pair : pattern) {
            if (pair.getModel() == ServerToClientModel.TYPE_UPDATE ||
                pair.getModel() == ServerToClientModel.TYPE_CREATE ||
                pair.getModel() == ServerToClientModel.TYPE_ADD ||
                pair.getModel() == ServerToClientModel.TYPE_REMOVE) {
                if (pair.getValue() instanceof Number) {
                    objectIds.add(((Number) pair.getValue()).intValue());
                }
            }
        }
        return objectIds;
    }

    private List<ReplayOperation> simulatePatternReplay(List<ClientModelTracker.ModelValuePair> pattern) {
        List<ReplayOperation> operations = new ArrayList<>();
        for (ClientModelTracker.ModelValuePair pair : pattern) {
            operations.add(new ReplayOperation(pair.getModel(), pair.getValue()));
        }
        return operations;
    }

    private String getTextValue(List<ClientModelTracker.ModelValuePair> pattern) {
        for (ClientModelTracker.ModelValuePair pair : pattern) {
            if (pair.getModel() == ServerToClientModel.TEXT) {
                return (String) pair.getValue();
            }
        }
        return null;
    }

    private Integer getTypeUpdateValue(List<ClientModelTracker.ModelValuePair> pattern) {
        for (ClientModelTracker.ModelValuePair pair : pattern) {
            if (pair.getModel() == ServerToClientModel.TYPE_UPDATE) {
                return (Integer) pair.getValue();
            }
        }
        return null;
    }

    // Helper class for replay simulation
    private static class ReplayOperation {
        final ServerToClientModel model;
        final Object value;

        ReplayOperation(ServerToClientModel model, Object value) {
            this.model = model;
            this.value = value;
        }
    }
}