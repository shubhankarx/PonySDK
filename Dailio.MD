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


