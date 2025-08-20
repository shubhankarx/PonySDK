package com.ponysdk.core.server.websocket;

import com.ponysdk.core.model.ServerToClientModel;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Tool for extracting dictionary patterns to an external file and loading them back.
 * This addresses requirement #5: Extract dictionary construction to an external process.
 */
public class DictionaryExtractorTool {
    
    /**
     * Export dictionary patterns to a JSON file
     * 
     * @param patterns List of patterns to export
     * @param outputPath Path to write the JSON file
     * @throws IOException If an I/O error occurs
     */
    public static void exportPatterns(List<List<String>> patterns, String outputPath) throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"patterns\": [\n");
        
        for (int i = 0; i < patterns.size(); i++) {
            List<String> pattern = patterns.get(i);
            json.append("    [");
            
            for (int j = 0; j < pattern.size(); j++) {
                json.append("\"").append(pattern.get(j).replace("\"", "\\\"")).append("\"");
                if (j < pattern.size() - 1) {
                    json.append(", ");
                }
            }
            
            json.append("]");
            if (i < patterns.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        
        json.append("  ]\n");
        json.append("}\n");
        
        Files.writeString(Paths.get(outputPath), json.toString());
    }
    
    /**
     * Import dictionary patterns from a JSON file
     * 
     * @param inputPath Path to the JSON file
     * @return List of imported patterns
     * @throws IOException If an I/O error occurs
     */
    public static List<List<String>> importPatterns(String inputPath) throws IOException {
        String json = Files.readString(Paths.get(inputPath));
        
        // Very simple JSON parser for this specific format
        List<List<String>> patterns = new ArrayList<>();
        
        // Find the patterns array
        int patternsStart = json.indexOf("\"patterns\"");
        if (patternsStart == -1) {
            throw new IOException("Invalid JSON format: 'patterns' array not found");
        }
        
        // Find the start of the array
        int arrayStart = json.indexOf('[', patternsStart);
        if (arrayStart == -1) {
            throw new IOException("Invalid JSON format: array start not found");
        }
        
        // Find the end of the array
        int arrayEnd = findMatchingBracket(json, arrayStart);
        if (arrayEnd == -1) {
            throw new IOException("Invalid JSON format: array end not found");
        }
        
        // Extract the array content
        String arrayContent = json.substring(arrayStart + 1, arrayEnd).trim();
        
        // Parse each pattern
        int pos = 0;
        while (pos < arrayContent.length()) {
            // Find the start of a pattern
            int patternStart = arrayContent.indexOf('[', pos);
            if (patternStart == -1) {
                break;
            }
            
            // Find the end of the pattern
            int patternEnd = findMatchingBracket(arrayContent, patternStart);
            if (patternEnd == -1) {
                throw new IOException("Invalid JSON format: pattern end not found");
            }
            
            // Extract the pattern content
            String patternContent = arrayContent.substring(patternStart + 1, patternEnd).trim();
            
            // Parse the pattern elements
            List<String> pattern = parsePatternElements(patternContent);
            patterns.add(pattern);
            
            // Move to the next pattern
            pos = patternEnd + 1;
        }
        
        return patterns;
    }
    
    /**
     * Parse pattern elements from a JSON array string
     * 
     * @param patternContent JSON array content
     * @return List of pattern elements
     */
    private static List<String> parsePatternElements(String patternContent) {
        List<String> elements = new ArrayList<>();
        
        int pos = 0;
        while (pos < patternContent.length()) {
            // Find the start of a string
            int stringStart = patternContent.indexOf('"', pos);
            if (stringStart == -1) {
                break;
            }
            
            // Find the end of the string (accounting for escaped quotes)
            int stringEnd = stringStart + 1;
            while (stringEnd < patternContent.length()) {
                if (patternContent.charAt(stringEnd) == '"' && patternContent.charAt(stringEnd - 1) != '\\') {
                    break;
                }
                stringEnd++;
            }
            
            if (stringEnd >= patternContent.length()) {
                throw new IllegalArgumentException("Invalid JSON format: string end not found");
            }
            
            // Extract the string content
            String element = patternContent.substring(stringStart + 1, stringEnd)
                    .replace("\\\"", "\""); // Unescape quotes
            elements.add(element);
            
            // Move to the next element
            pos = stringEnd + 1;
        }
        
        return elements;
    }
    
    /**
     * Find the matching closing bracket for an opening bracket
     * 
     * @param text Text to search in
     * @param openPos Position of the opening bracket
     * @return Position of the matching closing bracket, or -1 if not found
     */
    private static int findMatchingBracket(String text, int openPos) {
        char open = text.charAt(openPos);
        char close = (open == '[') ? ']' : (open == '{') ? '}' : ')';
        
        int depth = 1;
        for (int i = openPos + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        
        return -1; // No matching bracket found
    }
    
    /**
     * Export dictionary patterns from ModelValueDictionary to a binary file
     * 
     * @param dictionary ModelValueDictionary to export
     * @param outputPath Path to write the binary file
     * @throws IOException If an I/O error occurs
     */
    public static void exportDictionary(ModelValueDictionary dictionary, String outputPath) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(outputPath))) {
            // Get all patterns from dictionary
            Set<Integer> patternIds = dictionary.getPatternIds();
            Map<Integer, List<ModelValuePair>> patterns = new HashMap<>();
            
            for (Integer id : patternIds) {
                List<ModelValuePair> pattern = dictionary.getPattern(id);
                if (pattern != null) {
                    patterns.put(id, pattern);
                }
            }
            
            // Write patterns to file
            oos.writeObject(patterns);
        }
    }
    
    /**
     * Import dictionary patterns from a binary file
     * 
     * @param inputPath Path to the binary file
     * @return ModelValueDictionary with imported patterns
     * @throws IOException If an I/O error occurs
     * @throws ClassNotFoundException If the class of a serialized object cannot be found
     */
    @SuppressWarnings("unchecked")
    public static ModelValueDictionary importDictionary(String inputPath) throws IOException, ClassNotFoundException {
        ModelValueDictionary dictionary = new ModelValueDictionary(1); // Use threshold 1 for immediate recording
        
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(inputPath))) {
            Map<Integer, List<ModelValuePair>> patterns = (Map<Integer, List<ModelValuePair>>) ois.readObject();
            
            // Add patterns to dictionary
            for (List<ModelValuePair> pattern : patterns.values()) {
                dictionary.recordPattern(pattern);
            }
        }
        
        return dictionary;
    }
    
    /**
     * Extract semantic patterns (triplets) from ModelValueDictionary
     * 
     * @param dictionary ModelValueDictionary to extract from
     * @return List of extracted triplets
     */
    public static List<List<String>> extractTripletsFromDictionary(ModelValueDictionary dictionary) {
        List<List<String>> triplets = new ArrayList<>();
        
        // Get all patterns from dictionary
        Set<Integer> patternIds = dictionary.getPatternIds();
        
        for (Integer id : patternIds) {
            List<ModelValuePair> pattern = dictionary.getPattern(id);
            if (pattern != null) {
                // Extract WIDGET_TYPE values
                List<String> triplet = pattern.stream()
                        .filter(p -> p.getModel() == ServerToClientModel.WIDGET_TYPE)
                        .limit(3)
                        .map(p -> (String) p.getValue())
                        .collect(Collectors.toList());
                
                // Only add if it's a valid triplet (3 elements)
                if (triplet.size() == 3) {
                    triplets.add(triplet);
                }
            }
        }
        
        return triplets;
    }
    
    /**
     * Command-line tool for dictionary extraction and loading
     * 
     * @param args Command-line arguments
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage:");
            System.out.println("  export <output_file> - Export built-in patterns to a file");
            System.out.println("  import <input_file> - Import patterns from a file");
            return;
        }
        
        String command = args[0];
        String filePath = args[1];
        
        try {
            if ("export".equals(command)) {
                // Create a WebSocket to access its built-in patterns
                WebSocket webSocket = new WebSocket();
                
                // Extract triplets from the built-in dictionary
                List<List<String>> triplets = new ArrayList<>();
                
                // Add hard-coded patterns (reflection to access private field)
                try {
                    java.lang.reflect.Field initialPatternsField = WebSocket.class.getDeclaredField("initialPatterns");
                    initialPatternsField.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    List<List<String>> initialPatterns = (List<List<String>>) initialPatternsField.get(null);
                    triplets.addAll(initialPatterns);
                } catch (Exception e) {
                    System.err.println("Failed to access initialPatterns field: " + e.getMessage());
                    // Use some default patterns as fallback
                    triplets.add(Arrays.asList("PButton", "PLabel", "PCheckBox"));
                    triplets.add(Arrays.asList("PTree", "PTreeItem", "PTreeItem"));
                }
                
                // Export patterns to file
                exportPatterns(triplets, filePath);
                System.out.println("Exported " + triplets.size() + " patterns to " + filePath);
                
            } else if ("import".equals(command)) {
                // Import patterns from file
                List<List<String>> patterns = importPatterns(filePath);
                System.out.println("Imported " + patterns.size() + " patterns from " + filePath);
                
                // Build trie from imported patterns
                WebSocket.buildSemanticPatternTrie(patterns);
                System.out.println("Built semantic pattern trie with imported patterns");
                
            } else {
                System.out.println("Unknown command: " + command);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 