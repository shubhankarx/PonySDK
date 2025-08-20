package com.ponysdk.core.server.websocket;

import com.ponysdk.core.model.ServerToClientModel;

/**
 * A test task that toggles text between two values.
 * This simulates a common UI update scenario for testing dictionary compression.
 */
public class TextToggleTask implements TestTask {
    
    private final WebSocket webSocket;
    private final String text1;
    private final String text2;
    private final int widgetId;
    private final String taskId;
    private boolean useText1 = true;
    
    /**
     * Create a new text toggle task
     * 
     * @param webSocket The WebSocket to send updates through
     * @param text1 First text value
     * @param text2 Second text value
     * @param widgetId Widget ID to update
     */
    public TextToggleTask(WebSocket webSocket, String text1, String text2, int widgetId) {
        this.webSocket = webSocket;
        this.text1 = text1;
        this.text2 = text2;
        this.widgetId = widgetId;
        this.taskId = "text_toggle_" + widgetId;
    }
    
    @Override
    public void run() {
        // Start a new message
        webSocket.beginObject();
        
        // Send TYPE_UPDATE to indicate we're updating an existing widget
        webSocket.encode(ServerToClientModel.TYPE_UPDATE, widgetId);
        
        // Send the new text value
        String currentText = useText1 ? text1 : text2;
        webSocket.encode(ServerToClientModel.TEXT, currentText);
        
        // End the message
        webSocket.endObject(); // This calls encode(END, null)
        
        // Toggle for next execution
        useText1 = !useText1;
    }
    
    @Override
    public String id() {
        return taskId;
    }
    
    @Override
    public String description() {
        return String.format("Toggle text between '%s' and '%s' for widget %d", text1, text2, widgetId);
    }
    
    /**
     * Get the current text value that would be sent
     * 
     * @return Current text value
     */
    public String getCurrentText() {
        return useText1 ? text1 : text2;
    }
} 