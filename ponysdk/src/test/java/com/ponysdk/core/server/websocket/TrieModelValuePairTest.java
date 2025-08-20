package com.ponysdk.core.server.websocket;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import com.ponysdk.core.model.ServerToClientModel;

/**
 * Test that verifies the trie can work with ModelValuePair objects
 * using a different storage mechanism than equals/hashCode.
 */
public class TrieModelValuePairTest {

    @Test
    public void testTrieWithModelValuePairs() {
        // Create a pattern using ModelValuePair objects
        List<ModelValuePair> pattern = Arrays.asList(
            new ModelValuePair(ServerToClientModel.WIDGET_TYPE, "PButton"),
            new ModelValuePair(ServerToClientModel.WIDGET_ID, "123"),
            new ModelValuePair(ServerToClientModel.WIDGET_TYPE, "PLabel")
        );

        // Build trie from ModelValuePair pattern
        WebSocket.buildSemanticPatternTrieFromPairs(Collections.singletonList(pattern));

        WebSocket ws = new WebSocket();

        // Test prefix recognition with ModelValuePair objects
        assertTrue(ws.isPrefixOfKnownTripletFromPairs(Collections.emptyList()));
        assertTrue(ws.isPrefixOfKnownTripletFromPairs(pattern.subList(0, 1)));
        assertTrue(ws.isPrefixOfKnownTripletFromPairs(pattern.subList(0, 2)));

        // Test complete triplet recognition
        assertTrue(ws.isKnownTripletFromPairs(pattern));

        // Test with different pattern (should not match)
        List<ModelValuePair> differentPattern = Arrays.asList(
            new ModelValuePair(ServerToClientModel.WIDGET_TYPE, "PTextBox"),
            new ModelValuePair(ServerToClientModel.WIDGET_ID, "456"),
            new ModelValuePair(ServerToClientModel.WIDGET_TYPE, "PCheckBox")
        );

        assertFalse(ws.isPrefixOfKnownTripletFromPairs(differentPattern.subList(0, 1)));
        assertFalse(ws.isKnownTripletFromPairs(differentPattern));
    }

    @Test
    public void testTrieWithMixedPatterns() {
        // Test that both string and ModelValuePair patterns can coexist
        
        // Add string pattern
        List<String> stringPattern = Arrays.asList("PButton", "PLabel", "PCheckBox");
        WebSocket.buildSemanticPatternTrieFromStrings(Collections.singletonList(stringPattern));

        // Add ModelValuePair pattern
        List<ModelValuePair> pairPattern = Arrays.asList(
            new ModelValuePair(ServerToClientModel.TYPE_CREATE, 1),
            new ModelValuePair(ServerToClientModel.WIDGET_TYPE, "PButton"),
            new ModelValuePair(ServerToClientModel.WIDGET_ID, 123)
        );
        WebSocket.buildSemanticPatternTrieFromPairs(Collections.singletonList(pairPattern));

        WebSocket ws = new WebSocket();

        // Both patterns should be recognized
        assertTrue(ws.isKnownTriplet(stringPattern));
        assertTrue(ws.isKnownTripletFromPairs(pairPattern));

        // Cross-type checks should not interfere
        assertFalse(ws.isKnownTriplet(Arrays.asList("TYPE_CREATE:1", "WIDGET_TYPE:PButton", "WIDGET_ID:123")));
    }

    @Test
    public void testTrieKeyGeneration() {
        // Test that different ModelValuePair objects with same content generate same keys
        ModelValuePair pair1 = new ModelValuePair(ServerToClientModel.WIDGET_TYPE, "PButton");
        ModelValuePair pair2 = new ModelValuePair(ServerToClientModel.WIDGET_TYPE, "PButton");
        
        // Even though these are different objects (different hashCodes), 
        // they should work the same way in the trie due to key generation
        List<ModelValuePair> pattern1 = Collections.singletonList(pair1);
        List<ModelValuePair> pattern2 = Collections.singletonList(pair2);
        
        WebSocket.buildSemanticPatternTrieFromPairs(Collections.singletonList(Arrays.asList(pair1, pair1, pair1)));
        
        WebSocket ws = new WebSocket();
        
        // Both should be recognized as prefixes since they generate the same key
        assertTrue(ws.isPrefixOfKnownTripletFromPairs(pattern1));
        assertTrue(ws.isPrefixOfKnownTripletFromPairs(pattern2));
    }
}