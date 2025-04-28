Day 1 1/4/25
Started with registeration, logging on smart trade network and learned about the internal tools. 

Day 2 2/4/25
Started with Understanding smart trade local tools like Websocket. Tried to read reserach papers and video for better understanding.

Day 3 3/4/25
Tried to understand the big picture architecture and the functionality of code, without internet.


Day 4 4/4/25
Added some functionality in the code, wasn't able to test.

Day 5 7/4/25

This implementation adds a dictionary-based compression system to PonySDK's WebSocket communication layer. The optimization identifies repeated patterns of model-value pairs sent from server to client and replaces them with short references, reducing the amount of data transferred.

How It Works?
Pattern Detection: The server tracks sequences of model-value pairs sent to the client.
Dictionary Storage: When a pattern repeats, it's stored in a dictionary with a unique ID.
Reference Transmission: Instead of sending the entire pattern again, the server sends just the reference ID.
Client-Side Resolution: The client maintains its own dictionary to resolve references back to the original pattern.
Request Mechanism: If the client receives an unknown reference, it requests the pattern definition from the server.


Files Modified:
1 ServerToClientModel.java: Added enum values for dictionary operations

i DICTIONARY_REFERENCE: Reference to a pattern
ii DICTIONARY_PATTERN_START: Start of a pattern definition
iii DICTIONARY_PATTERN_END: End of a pattern definition

2 ClientToServerModel.java: Added enum value

i DICTIONARY_REQUEST: Request for an unknown pattern

3 WebSocket.java: Added server-side pattern detection and dictionary handling

i Pattern collection and batching
ii Dictionary lookup and reference sending
iii Pattern definition handling

4WebSocketClient.java: Added client-side dictionary resolution

i Reference handling
ii Pattern storage
iii Pattern request mechanism

5 UIContext.java: Added handling for dictionary requests

i Processing pattern requests from client

Additional Classes
i ModelValuePair.java: Stores server-to-client model-value pairs
ii ModelValueDictionary.java: Maintains the dictionary of patterns

How to Use
The dictionary optimization is automatically enabled by default. To control it:

// To disable
webSocket.setDictionaryEnabled(false);

// To enable
webSocket.setDictionaryEnabled(true);

Current status: some bugs I have to fix, some errors

Day 6 8/4/25

I started with fixing errors and debugging the code. There were some issues in files:
WebSocketClient.java
UIContext.java
WebsocketClient.java
among others.

Further, here's the function calls:
• WebSocketClient (com.ponysdk.core.terminal.socket.WebSocketClient):
– Opens the client WebSocket connection and registers onmessage.
– When a message containing ArrayBuffer arrives, it calls processArrayBuffer(...).
– That in turn calls uiBuilder.updateMainTerminal(...) to update the UI.

• WebsocketClient (com.ponysdk.driver.WebsocketClient):
– Also manages a WebSocket session on the driver side (similar flow).
– processArrayBuffer(...) checks if it’s receiving a dictionary pattern or referencing one and uses requestDictionaryPattern(...) as needed.

• WebSocket (com.ponysdk.core.server.websocket.WebSocket):
– Server-side listener implementation.
– encode(...) batches and possibly compresses data via processBatch() (dictionary compression).
– handleDictionaryRequest(...) sends missing dictionary patterns back to the client.

• ModelValuePair (com.ponysdk.core.server.websocket.ModelValuePair):
– Represents a (model, value) to track repeated data patterns.

• UIContext (com.ponysdk.core.server.application.UIContext):
– Maintains server-side context, acquiring/releasing the UI context when encode(...) or other actions are performed.

In effect, the client’s onmessage handlers call processArrayBuffer(...), which may request unknown dictionary data. On the server side, encode(...) in WebSocket can batch and compress data, then
handleDictionaryRequest(...) if the client needs full pattern definitions. The UIContext provides the server context for these operations.

Day 7 10/4/25

I believe the errors have been restricgted to one file. I decided to read more files: UIBuilder, UIFactory, etc.
I get a better understanding by going through function calls.

Day 8 14/4/25

With Mathieu, we start discussing today. We can discuss code, and practice more ideas.
We decided to take the step back and complete the hashing system.
So rather than having the complicated system now, we change it.
Trainings with Mathieu: Trading overview, training presentation and foundation and code review.

Day 9 15/4/25
We had training on learning about Smart Trade systems, especially for Trainees. Like Front end training with Nathan.

Day 10 16/4/25

I worked on implementing and adding the fresh version of dictionary, 

Training tools and managed services and scenario runner.

Day 11 17/4/25

I commented out everything, from the file UI Entry point.
Which was basically everything in the Start function


We discuss during meeting to make the presentation.

Some notes from meeting:
Not going to byte level bit
Not encode somehting not to be sent
Predict next instruction to send
send some instruction already known
encode bytes we dont need to sent
just before encoding to move on at the byte level

10-20 slides expected 
15-20 without speak alot
Let people in R&D Know , who we are
Know Red Line Porject
Subject + State of the art
Need Pictures

LLM + Optimization
Oracle AI
Q&A
 
Day 12 18/4/25

“Should the client mirror the server’s tracking logic and build its own frequency dictionary from incoming frames, or should the server compute the dictionary once and send that mapping to the client?”

“Alternatively, could we let both sides maintain their own dictionaries independently and occasionally synchronize them to handle drift?”


Day 28/4/25

Added changes
# Complete Dictionary Fix - Step by Step

Based on my review of the codebase, here's a step-by-step fix that minimizes changes while getting your dictionary feature working:

## Step 1: Add missing model values to ServerToClientModel

```java


// Add after TYPE_GC but before END
TYPE_GC(ValueTypeModel.UINT31),

// Dictionary-related models
DICT_UPDATE(ValueTypeModel.UINT31),        // Signals a dictionary entry update
DICT_VALUE_INDEX(ValueTypeModel.UINT31),   // Indicates value is sent by dictionary index

END(ValueTypeModel.NULL),
```

## Step 2: Add missing methods to ClientModelTracker

```java


// Add this field after other fields
private final Map<Integer, String> indexToKeyMap = new ConcurrentHashMap<>();
// Note: valueMap already exists

/**
 * Handles a dictionary update from the server
 */
public void handleDictUpdate(int index, String key, Object value) {
    if (value == null) {
        log.warning("Received null value for dictionary entry: " + index);
        return;
    }
    
    valueMap.put(key, value);
    indexToKeyMap.put(index, key);
    log.fine("Dictionary updated: index=" + index + ", key=" + key + ", value=" + value);
}

/**
 * Get a value by its dictionary index
 */
public Object getValueByIndex(int index) {
    String key = indexToKeyMap.get(index);
    if (key == null) {
        log.warning("No dictionary key found for index: " + index);
        return null;
    }
    return valueMap.get(key);
}
```

## Step 3: Fix processUpdateWithDictionary in UIBuilder

```java


// Replace entire method with this implementation
private void processUpdateWithDictionary(BinaryModel binaryModel, ReaderBuffer buffer) {
    final ServerToClientModel model = binaryModel.getModel();
    
    // Handle dictionary update from server
    if (ServerToClientModel.DICT_UPDATE == model) {
        // Format: DICT_UPDATE, INDEX, KEY, VALUE, END
        int index = buffer.readBinaryModel().getIntValue();
        String key = buffer.readBinaryModel().getStringValue();
        BinaryModel valueModel = buffer.readBinaryModel();
        Object value = extractValue(valueModel);
        
        modelTracker.handleDictUpdate(index, key, value);
        buffer.readBinaryModel(); // Read END
        return;
    }
    
    // Handle value-by-index reference
    if (ServerToClientModel.DICT_VALUE_INDEX == model) {
        int index = binaryModel.getIntValue();
        Object value = modelTracker.getValueByIndex(index);
        if (value == null) {
            log.warning("Dictionary lookup failed for index: " + index);
            buffer.shiftNextBlock(false);
            return;
        }
        
        // Read the model type (what kind of instruction this is)
        binaryModel = buffer.readBinaryModel();
        dispatchOperation(binaryModel.getModel(), value, buffer);
        return;
    }
    
    // Extract value based on model type - normal flow
    Object value = extractValue(binaryModel);
    dispatchOperation(model, value, buffer);
}

// New helper method to extract value from BinaryModel
private Object extractValue(BinaryModel binaryModel) {
    final ServerToClientModel model = binaryModel.getModel();
    switch (model.getTypeModel()) {
        case STRING:  return binaryModel.getStringValue();
        case INTEGER: 
        case UINT31:  return binaryModel.getIntValue();
        case BOOLEAN: return binaryModel.getBooleanValue();
        case DOUBLE:  return binaryModel.getDoubleValue();
        case FLOAT:   return binaryModel.getFloatValue();
        case LONG:    return binaryModel.getLongValue();
        case ARRAY:   return binaryModel.getArrayValue();
        default:
            log.warning("Unknown model type: " + model.getTypeModel());
            return null;
    }
}

// New helper method to dispatch operations
private void dispatchOperation(ServerToClientModel model, Object value, ReaderBuffer buffer) {
    try {
        if (ServerToClientModel.TYPE_CREATE == model) {
            processCreate(buffer, (Integer)value);
        } else if (ServerToClientModel.TYPE_UPDATE == model) {
            processUpdate(buffer, (Integer)value);
        } else if (ServerToClientModel.TYPE_ADD == model) {
            processAdd(buffer, (Integer)value);
        } else if (ServerToClientModel.TYPE_GC == model) {
            processGC(buffer, (Integer)value);
        } else if (ServerToClientModel.TYPE_REMOVE == model) {
            processRemove(buffer, (Integer)value);
        } else if (ServerToClientModel.TYPE_ADD_HANDLER == model) {
            processAddHandler(buffer, (Integer)value);
        } else if (ServerToClientModel.TYPE_REMOVE_HANDLER == model) {
            processRemoveHandler(buffer, (Integer)value);
        } else if (ServerToClientModel.TYPE_HISTORY == model) {
            processHistory(buffer, (String)value);
        } else {
            log.warning("Unknown instruction: " + model);
            if (ServerToClientModel.END != model) buffer.shiftNextBlock(false);
        }
    } catch (Exception e) {
        if (ServerToClientModel.END != model) buffer.shiftNextBlock(false);
        sendExceptionMessageToServer(e);
    }
}
```

## Step 4: Temporarily disable dictionary until testing is complete

```java


// Add after requestBuilder = requestBuilder in init() method
public void init(final RequestBuilder requestBuilder) {
    if (log.isLoggable(Level.INFO)) log.info("Init graphical system");
    
    this.requestBuilder = requestBuilder;

    // Temporarily disable dictionary
    final PTInstruction requestData = new PTInstruction();
    requestData.put(ClientToServerModel.DICTIONARY_ENABLED, false);
    requestBuilder.send(requestData);
    
    // Existing code...
```

These changes:
1. Add necessary dictionary model values
2. Add index-to-key lookups in ClientModelTracker
3. Properly handle dictionary updates in UIBuilder
4. Keep your UI rendering correctly while testing

Once you confirm everything works, you can remove Step 4 and allow the dictionary compression to work.