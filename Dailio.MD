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
