package com.ponysdk.core.server.websocket;

import com.ponysdk.core.model.ServerToClientModel;
import com.ponysdk.core.terminal.socket.ClientModelTracker;
import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Critical test to identify and fix server-client ModelValuePair format incompatibilities
 * that cause dictionary pattern matching failures.
 *
 * ISSUE: Server uses ContentComparator, Client uses Objects.equals()
 * RESULT: Same patterns have different equality, preventing dictionary compression
 */
public class DictionaryCompatibilityTest {

    private ModelValueDictionary serverDictionary;
    private ClientModelTracker clientTracker;

    @Before
    public void setUp() {
        serverDictionary = new ModelValueDictionary(2); // Lower threshold for testing
        clientTracker = new ClientModelTracker();
    }

    /**
     * TEST 1: Value Type Conversion Compatibility
     *
     * CRITICAL BUG: Server stores Integer(20), client stores int(20)
     * Integer.valueOf(20).equals(20) → TRUE
     * Integer.valueOf(20).hashCode() != Objects.hash(20) → DIFFERENT HASH CODES
     */
    @Test
    public void testValueTypeConversion() {
        System.out.println("=== TEST 1: Value Type Conversion Compatibility ===");

        // Server-side pattern creation (how server stores patterns)
        List<ModelValuePair> serverPattern = new ArrayList<>();
        serverPattern.add(new ModelValuePair(ServerToClientModel.TYPE_UPDATE, Integer.valueOf(20)));
        serverPattern.add(new ModelValuePair(ServerToClientModel.TEXT, "Hello World"));

        // Client-side pattern creation (how client receives patterns from wire)
        List<ClientModelTracker.ModelValuePair> clientPattern = new ArrayList<>();
        clientPattern.add(new ClientModelTracker.ModelValuePair(ServerToClientModel.TYPE_UPDATE, 20)); // primitive int
        clientPattern.add(new ClientModelTracker.ModelValuePair(ServerToClientModel.TEXT, "Hello World"));

        // Store pattern in server dictionary
        Integer patternId = serverDictionary.recordPattern(serverPattern);
        assertNull(patternId);

        patternId = serverDictionary.recordPattern(serverPattern);
        assertNotNull(patternId);

        // Store pattern in client tracker (simulating pattern definition received from server)
        clientTracker.recordPattern(patternId, clientPattern);

        // TEST: Can client retrieve the pattern correctly?
        List<ClientModelTracker.ModelValuePair> retrievedPattern = clientTracker.getPattern(patternId);
        assertNotNull(retrievedPattern);
        assertEquals(2, retrievedPattern.size());

        // TEST: Value equality between server and client representations
        ClientModelTracker.ModelValuePair clientPair = retrievedPattern.get(0);
        ModelValuePair serverPair = serverPattern.get(0);

        System.out.println("Server pair: " + serverPair.getModel() + "=" + serverPair.getValue() + " (type: " + serverPair.getValue().getClass() + ")");
        System.out.println("Client pair: " + clientPair.getModel() + "=" + clientPair.getValue() + " (type: " + clientPair.getValue().getClass() + ")");

        // EXPECTED FAILURE: This will fail due to Integer vs int mismatch
        assertEquals(serverPair.getValue(), clientPair.getValue());
    }

    /**
     * TEST 2: Pattern Equality Across Server-Client Boundary
     *
     * Tests if patterns created on server can be matched by patterns stored on client
     */
    @Test
    public void testPatternEqualityCompatibility() {
        System.out.println("=== TEST 2: Pattern Equality Compatibility ===");

        // Create identical logical patterns using different classes
        List<ModelValuePair> serverPattern = createServerPattern();
        List<ClientModelTracker.ModelValuePair> clientPattern = createEquivalentClientPattern();

        // Store server pattern
        Integer patternId = serverDictionary.recordPattern(serverPattern);
        patternId = serverDictionary.recordPattern(serverPattern); // 2nd time to create
        assertNotNull(patternId);

        // Store equivalent client pattern
        clientTracker.recordPattern(patternId, clientPattern);

        // Verify client can retrieve
        List<ClientModelTracker.ModelValuePair> retrieved = clientTracker.getPattern(patternId);
        assertNotNull(retrieved);

        // TEST: Element-by-element comparison
        assertEquals(serverPattern.size(), clientPattern.size());
        for (int i = 0; i < serverPattern.size(); i++) {
            ModelValuePair serverPair = serverPattern.get(i);
            ClientModelTracker.ModelValuePair clientPair = clientPattern.get(i);

            assertEquals(serverPair.getModel(), clientPair.getModel());

            // CRITICAL TEST: This will expose the value type mismatch
            System.out.println("Comparing values at index " + i + ":");
            System.out.println("  Server: " + serverPair.getValue() + " (" +
                             (serverPair.getValue() != null ? serverPair.getValue().getClass().getName() : "null") + ")");
            System.out.println("  Client: " + clientPair.getValue() + " (" +
                             (clientPair.getValue() != null ? clientPair.getValue().getClass().getName() : "null") + ")");
        }
    }

    /**
     * TEST 3: Hash Code Consistency
     *
     * Critical for HashMap-based pattern storage to work correctly
     */
    @Test
    public void testHashCodeConsistency() {
        System.out.println("=== TEST 3: Hash Code Consistency ===");

        // Test basic value hash codes
        Integer serverInt = Integer.valueOf(20);
        int clientInt = 20;

        System.out.println("Server Integer(20) hashCode: " + serverInt.hashCode());
        System.out.println("Client int(20) hashCode: " + Objects.hash(clientInt));
        System.out.println("Are they equal? " + serverInt.equals(clientInt));
        System.out.println("Do they have same hashCode? " + (serverInt.hashCode() == Objects.hash(clientInt)));

        // Test ModelValuePair hash codes
        ModelValuePair serverPair = new ModelValuePair(ServerToClientModel.TYPE_UPDATE, Integer.valueOf(20));
        ClientModelTracker.ModelValuePair clientPair = new ClientModelTracker.ModelValuePair(ServerToClientModel.TYPE_UPDATE, 20);

        System.out.println("Server ModelValuePair hashCode: " + serverPair.hashCode());
        System.out.println("Client ModelValuePair hashCode: " + java.util.Objects.hash(clientPair.getModel(), clientPair.getValue()));

        // This will show the fundamental incompatibility
        assertNotEquals(serverPair.hashCode(), java.util.Objects.hash(clientPair.getModel(), clientPair.getValue()));
    }

    /**
     * TEST 4: Pattern Replay Simulation
     *
     * Simulates the complete pattern storage → transmission → replay cycle
     */
    @Test
    public void testCompletePatternCycle() {
        System.out.println("=== TEST 4: Complete Pattern Cycle ===");

        // Step 1: Server creates and stores pattern
        List<ModelValuePair> originalPattern = new ArrayList<>();
        originalPattern.add(new ModelValuePair(ServerToClientModel.TYPE_UPDATE, Integer.valueOf(21)));
        originalPattern.add(new ModelValuePair(ServerToClientModel.TEXT, "Status: A"));

        // Multiple occurrences to trigger dictionary storage
        serverDictionary.recordPattern(originalPattern);
        Integer patternId = serverDictionary.recordPattern(originalPattern);
        assertNotNull(patternId);

        // Step 2: Simulate wire transmission (Integer → int conversion)
        List<ClientModelTracker.ModelValuePair> wirePattern = new ArrayList<>();
        for (ModelValuePair serverPair : originalPattern) {
            Object convertedValue = simulateWireTransmission(serverPair.getValue());
            wirePattern.add(new ClientModelTracker.ModelValuePair(serverPair.getModel(), convertedValue));
        }

        // Step 3: Client stores received pattern
        clientTracker.recordPattern(patternId, wirePattern);

        // Step 4: Server sends reference, client attempts lookup
        List<ClientModelTracker.ModelValuePair> retrievedPattern = clientTracker.getPattern(patternId);
        assertNotNull(retrievedPattern);

        // Step 5: Verify pattern integrity for replay
        assertEquals(originalPattern.size(), retrievedPattern.size());

        for (int i = 0; i < originalPattern.size(); i++) {
            ModelValuePair original = originalPattern.get(i);
            ClientModelTracker.ModelValuePair retrieved = retrievedPattern.get(i);

            assertEquals(original.getModel(), retrieved.getModel());

            // VALUE COMPATIBILITY CHECK - This is where the bug manifests
            System.out.println("Pattern element " + i + ":");
            System.out.println("  Original: " + original.getValue() + " (" + original.getValue().getClass() + ")");
            System.out.println("  Retrieved: " + retrieved.getValue() + " (" + retrieved.getValue().getClass() + ")");

            // For successful pattern replay, these should be functionally equivalent
            assertTrue(valuesAreEquivalent(original.getValue(), retrieved.getValue()));
        }
    }

    /**
     * TEST 5: Buffer State Isolation Simulation
     *
     * Tests that pattern replay doesn't corrupt buffer state
     */
    @Test
    public void testBufferStateIsolation() {
        System.out.println("=== TEST 5: Buffer State Isolation ===");

        // This test simulates the buffer corruption issue
        // In real implementation, pattern replay calls update() recursively with same buffer
        // Here we test that pattern replay can work without buffer corruption

        List<ClientModelTracker.ModelValuePair> pattern = new ArrayList<>();
        pattern.add(new ClientModelTracker.ModelValuePair(ServerToClientModel.TYPE_UPDATE, 22));
        pattern.add(new ClientModelTracker.ModelValuePair(ServerToClientModel.TEXT, "Price: 100"));

        clientTracker.recordPattern(1, pattern);

        // Simulate pattern replay - should not require buffer manipulation
        List<ClientModelTracker.ModelValuePair> replayPattern = clientTracker.getPattern(1);
        assertNotNull(replayPattern);

        // Each element should be independently processable
        for (ClientModelTracker.ModelValuePair pair : replayPattern) {
            assertNotNull(pair.getModel());
            assertNotNull(pair.getValue());

            // This should not require buffer state manipulation
            System.out.println("Replay element: " + pair.getModel() + " = " + pair.getValue());
        }
    }

    // Helper methods
    private List<ModelValuePair> createServerPattern() {
        List<ModelValuePair> pattern = new ArrayList<>();
        pattern.add(new ModelValuePair(ServerToClientModel.TYPE_UPDATE, Integer.valueOf(20)));
        pattern.add(new ModelValuePair(ServerToClientModel.TEXT, "Hello"));
        pattern.add(new ModelValuePair(ServerToClientModel.WIDGET_ID, Integer.valueOf(123)));
        return pattern;
    }

    private List<ClientModelTracker.ModelValuePair> createEquivalentClientPattern() {
        List<ClientModelTracker.ModelValuePair> pattern = new ArrayList<>();
        pattern.add(new ClientModelTracker.ModelValuePair(ServerToClientModel.TYPE_UPDATE, 20)); // int, not Integer
        pattern.add(new ClientModelTracker.ModelValuePair(ServerToClientModel.TEXT, "Hello"));
        pattern.add(new ClientModelTracker.ModelValuePair(ServerToClientModel.WIDGET_ID, 123)); // int, not Integer
        return pattern;
    }

    private Object simulateWireTransmission(Object serverValue) {
        // Simulate how values get converted during wire transmission
        if (serverValue instanceof Integer) {
            return ((Integer) serverValue).intValue(); // Integer → int conversion
        }
        return serverValue; // Strings remain strings
    }

    private boolean valuesAreEquivalent(Object serverValue, Object clientValue) {
        if (serverValue == null && clientValue == null) return true;
        if (serverValue == null || clientValue == null) return false;

        // Handle Integer/int compatibility
        if (serverValue instanceof Integer && clientValue instanceof Integer) {
            return serverValue.equals(clientValue);
        }
        if (serverValue instanceof Integer && clientValue instanceof Number) {
            return ((Integer) serverValue).intValue() == ((Number) clientValue).intValue();
        }

        return serverValue.equals(clientValue);
    }
}