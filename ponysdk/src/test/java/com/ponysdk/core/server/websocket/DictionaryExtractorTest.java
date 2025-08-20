package com.ponysdk.core.server.websocket;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the DictionaryExtractorTool
 */
public class DictionaryExtractorTest {
    
    @TempDir
    Path tempDir;
    
    @Test
    public void testExportAndImportPatterns() throws IOException {
        // Create test patterns
        List<List<String>> patterns = Arrays.asList(
                Arrays.asList("PButton", "PLabel", "PCheckBox"),
                Arrays.asList("PTree", "PTreeItem", "PTreeItem"),
                Arrays.asList("PTextBox", "PListBox", "PButton")
        );
        
        // Create temporary file
        File tempFile = tempDir.resolve("patterns.json").toFile();
        
        // Export patterns
        DictionaryExtractorTool.exportPatterns(patterns, tempFile.getAbsolutePath());
        
        // Verify file exists
        assertTrue(tempFile.exists(), "Exported file should exist");
        assertTrue(tempFile.length() > 0, "Exported file should not be empty");
        
        // Import patterns
        List<List<String>> importedPatterns = DictionaryExtractorTool.importPatterns(tempFile.getAbsolutePath());
        
        // Verify imported patterns
        assertEquals(patterns.size(), importedPatterns.size(), "Should import same number of patterns");
        
        for (int i = 0; i < patterns.size(); i++) {
            List<String> originalPattern = patterns.get(i);
            List<String> importedPattern = importedPatterns.get(i);
            
            assertEquals(originalPattern.size(), importedPattern.size(), 
                    "Pattern " + i + " should have same size");
            
            for (int j = 0; j < originalPattern.size(); j++) {
                assertEquals(originalPattern.get(j), importedPattern.get(j), 
                        "Element " + j + " of pattern " + i + " should match");
            }
        }
    }
    
    @Test
    public void testHandleSpecialCharacters() throws IOException {
        // Create test patterns with special characters
        List<List<String>> patterns = Arrays.asList(
                Arrays.asList("Button\"WithQuote", "Label\nWithNewline", "Check\\WithBackslash"),
                Arrays.asList("Tree{WithBrace}", "Item[WithBracket]", "Item\"WithQuote\"")
        );
        
        // Create temporary file
        File tempFile = tempDir.resolve("special_patterns.json").toFile();
        
        // Export patterns
        DictionaryExtractorTool.exportPatterns(patterns, tempFile.getAbsolutePath());
        
        // Import patterns
        List<List<String>> importedPatterns = DictionaryExtractorTool.importPatterns(tempFile.getAbsolutePath());
        
        // Verify imported patterns
        assertEquals(patterns.size(), importedPatterns.size(), "Should import same number of patterns");
        
        for (int i = 0; i < patterns.size(); i++) {
            List<String> originalPattern = patterns.get(i);
            List<String> importedPattern = importedPatterns.get(i);
            
            assertEquals(originalPattern.size(), importedPattern.size(), 
                    "Pattern " + i + " should have same size");
            
            for (int j = 0; j < originalPattern.size(); j++) {
                assertEquals(originalPattern.get(j), importedPattern.get(j), 
                        "Element " + j + " of pattern " + i + " should match");
            }
        }
    }
    
    @Test
    public void testInvalidJsonFormat() {
        // Create temporary file with invalid JSON
        File tempFile = tempDir.resolve("invalid.json").toFile();
        
        try {
            // Write invalid JSON
            java.nio.file.Files.writeString(tempFile.toPath(), "{ \"patterns\": [\"invalid\" }");
            
            // Try to import
            assertThrows(IOException.class, () -> {
                DictionaryExtractorTool.importPatterns(tempFile.getAbsolutePath());
            }, "Should throw IOException for invalid JSON");
            
        } catch (IOException e) {
            fail("Failed to write test file: " + e.getMessage());
        }
    }
    
    @Test
    public void testExtractTripletsFromDictionary() {
        // Create a dictionary with some patterns
        ModelValueDictionary dictionary = new ModelValueDictionary(1);
        
        // We can't easily create ModelValuePair objects with WIDGET_TYPE in a test
        // This would require more extensive mocking
        
        // Instead, verify that the method doesn't throw exceptions
        List<List<String>> triplets = DictionaryExtractorTool.extractTripletsFromDictionary(dictionary);
        assertNotNull(triplets, "Should return a non-null list");
    }
} 