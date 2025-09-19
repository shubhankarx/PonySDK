# PonySDK WebSocket Protocol Stack Diagram

## Network Protocol Stack Architecture

This diagram shows the complete PonySDK WebSocket system as a proper **Protocol Stack** with each component placed at its correct networking layer, ensuring proper separation of concerns and clear understanding of where latency measurements occur.

```mermaid
graph TB
    subgraph "Layer 7: Application Layer"
        direction TB
        UIComponents["🖥️ UI Components<br/>PTButton, PTLabel, PTPanel<br/>ponysdk/src/main/java/com/ponysdk/core/ui/"]
        BusinessLogic["🏢 Business Logic<br/>Application Entry Points<br/>sample/src/main/java/com/ponysdk/sample/client/"]
        UIContext["🎯 UIContext<br/>Session Management<br/>ponysdk/src/main/java/com/ponysdk/core/server/application/UIContext.java"]
    end

    subgraph "Layer 6: Optimization & Intelligence Layer"
        direction TB

        subgraph "Dictionary Compression"
            DictManager["🗂️ ModelValueDictionary<br/>Pattern Storage & Lookup<br/>ponysdk/src/main/java/com/ponysdk/core/server/websocket/ModelValueDictionary.java"]
            DictClient["📋 ClientModelTracker<br/>Client-side Pattern Storage<br/>ponysdk/src/main/java/com/ponysdk/core/terminal/socket/ClientModelTracker.java"]
        end

        subgraph "Trie Prediction"
            TrieEngine["🌳 TrieNode & WidgetTrieNode<br/>Pattern Prediction Engine<br/>WebSocket.java:182-198, 130-148"]
            TrieOps["🔍 Trie Operations<br/>isPrefixOfKnownTriplet()<br/>isKnownTriplet()<br/>WebSocket.java:495-746"]
        end

        subgraph "CodeT5 AI Analysis"
            CodeT5Proc["🤖 Semantic Processing<br/>processInstructionForPrediction()<br/>WebSocket.java:2364-2433"]
            FastAPIComm["🌐 HTTP Communication<br/>sendJsonPostRequestUsingHttpURLConnection()<br/>WebSocket.java:2170-2313"]
        end
    end

    subgraph "Layer 5: Message Protocol Layer"
        direction TB

        ProtocolModels["📋 Protocol Models<br/>ServerToClientModel<br/>ClientToServerModel<br/>ponysdk/src/main/java/com/ponysdk/core/model/"]
        BinaryEncoding["📦 Binary Encoding<br/>BinaryModel, ModelValuePair<br/>ponysdk/src/main/java/com/ponysdk/core/terminal/model/"]
        MessageBoundary["🔚 Message Boundaries<br/>beginObject(), endObject()<br/>END markers"]
    end

    subgraph "Layer 4: WebSocket Transport Layer"
        direction TB

        subgraph "Server Transport"
            WebSocketServer["🔌 WebSocket Server<br/>WebSocket.java:encode()<br/>Line 1224"]
            WebSocketPusher["📤 WebSocketPusher<br/>Frame transmission<br/>ponysdk/src/main/java/com/ponysdk/core/server/websocket/WebSocketPusher.java"]
            LatencyTracker["📊 LatencyTracker<br/>5-Stage Pipeline Measurement<br/>ponysdk/src/main/java/com/ponysdk/core/server/websocket/LatencyTracker.java"]
        end

        subgraph "Client Transport"
            UIBuilder["🔄 UIBuilder<br/>Message Processing<br/>ponysdk/src/main/java/com/ponysdk/core/terminal/UIBuilder.java"]
            ReaderBuffer["📖 ReaderBuffer<br/>Binary Message Parsing<br/>ponysdk/src/main/java/com/ponysdk/core/terminal/model/ReaderBuffer.java"]
        end
    end

    subgraph "Layer 3: HTTP/TCP Transport Layer"
        direction TB
        JettyWebSocket["⚡ Jetty WebSocket<br/>org.eclipse.jetty.websocket"]
        HTTPUpgrade["🔄 HTTP Upgrade<br/>WebSocket handshake"]
        TCPConnection["🔗 TCP Connection<br/>Reliable byte stream"]
    end

    subgraph "Layer 2: Network Layer"
        direction TB
        IPRouting["🌐 IP Routing<br/>127.0.0.1:8081/8082"]
        PacketForwarding["📦 Packet Forwarding"]
    end

    subgraph "Layer 1: Physical Layer"
        direction TB
        NetworkInterface["🔌 Network Interface<br/>Ethernet/WiFi"]
        PhysicalMedium["📡 Physical Medium<br/>Cables/Radio waves"]
    end

    %% External Services
    subgraph "External AI Services"
        FastAPIService["🤖 FastAPI Service<br/>http://127.0.0.1:8000/generate<br/>CodeT5 ML Model"]
    end

    %% Latency Measurement Points across layers
    subgraph "Cross-Layer Latency Measurement"
        direction TB
        Stage1["📊 Stage 1: Message Intercept<br/>onInterceptMessage()<br/>Layer 4 → Layer 6"]
        Stage2["📊 Stage 2: Optimization Processing<br/>onDictionaryLookup(), onTrieQuery(), onCodeT5Query()<br/>Layer 6 processing"]
        Stage3["📊 Stage 3: Protocol Encoding<br/>onHashCompute(), onEncode()<br/>Layer 5 → Layer 4"]
        Stage4["📊 Stage 4: Transport Queuing<br/>onOutgoingPonyFrame(), onOutgoingPonyFramesBytes()<br/>Layer 4"]
        Stage5["📊 Stage 5: Network Transmission<br/>onFrameWriteSuccess(), onFrameWriteFailure()<br/>Layer 4 → Layer 3"]
    end

    %% Flow connections between layers
    UIComponents --> UIContext
    BusinessLogic --> UIContext
    UIContext --> DictManager
    UIContext --> TrieEngine
    UIContext --> CodeT5Proc

    %% Optimization layer processing
    DictManager --> ProtocolModels
    TrieOps --> ProtocolModels
    FastAPIComm --> FastAPIService
    FastAPIService --> CodeT5Proc

    %% Protocol layer
    ProtocolModels --> BinaryEncoding
    BinaryEncoding --> MessageBoundary
    MessageBoundary --> WebSocketServer

    %% Transport layer
    WebSocketServer --> WebSocketPusher
    WebSocketPusher --> LatencyTracker
    LatencyTracker --> JettyWebSocket

    %% Network transmission
    JettyWebSocket --> HTTPUpgrade
    HTTPUpgrade --> TCPConnection
    TCPConnection --> IPRouting
    IPRouting --> NetworkInterface
    NetworkInterface --> PhysicalMedium

    %% Client-side reception (reverse flow)
    PhysicalMedium --> NetworkInterface
    NetworkInterface --> IPRouting
    IPRouting --> TCPConnection
    TCPConnection --> JettyWebSocket
    JettyWebSocket --> UIBuilder
    UIBuilder --> ReaderBuffer
    ReaderBuffer --> DictClient

    %% Latency measurement flow
    WebSocketServer --> Stage1
    Stage1 --> Stage2
    Stage2 --> Stage3
    Stage3 --> Stage4
    Stage4 --> Stage5

    %% Color coding by layer
    classDef layer7 fill:#ffebee,stroke:#c62828,stroke-width:2px
    classDef layer6 fill:#e8f5e8,stroke:#2e7d32,stroke-width:2px
    classDef layer5 fill:#e3f2fd,stroke:#1565c0,stroke-width:2px
    classDef layer4 fill:#fff3e0,stroke:#ef6c00,stroke-width:2px
    classDef layer3 fill:#f3e5f5,stroke:#7b1fa2,stroke-width:2px
    classDef layer2 fill:#e0f2f1,stroke:#00695c,stroke-width:2px
    classDef layer1 fill:#fafafa,stroke:#424242,stroke-width:2px
    classDef measurement fill:#fff9c4,stroke:#f57f17,stroke-width:3px
    classDef external fill:#ffccbc,stroke:#d84315,stroke-width:2px

    class UIComponents,BusinessLogic,UIContext layer7
    class DictManager,DictClient,TrieEngine,TrieOps,CodeT5Proc,FastAPIComm layer6
    class ProtocolModels,BinaryEncoding,MessageBoundary layer5
    class WebSocketServer,WebSocketPusher,LatencyTracker,UIBuilder,ReaderBuffer layer4
    class JettyWebSocket,HTTPUpgrade,TCPConnection layer3
    class IPRouting,PacketForwarding layer2
    class NetworkInterface,PhysicalMedium layer1
    class Stage1,Stage2,Stage3,Stage4,Stage5 measurement
    class FastAPIService external
```

## Layer-by-Layer Component Mapping

### Layer 7: Application Layer
- **Purpose**: Business logic and UI component management
- **Components**: UIContext, PTObject classes, Application entry points
- **Latency Impact**: Minimal (~0.1-0.5ms) - mostly object creation and method calls
- **Key Files**: `UIContext.java`, `sample/client/` classes

### Layer 6: Optimization & Intelligence Layer
- **Purpose**: Pattern recognition, compression, and AI-based prediction
- **Components**: Dictionary, Trie, CodeT5 systems
- **Latency Impact**: Variable (1-500ms depending on AI calls)
- **Key Files**: `ModelValueDictionary.java`, `ClientModelTracker.java`, WebSocket trie methods

### Layer 5: Message Protocol Layer
- **Purpose**: Message structure, encoding, and protocol compliance
- **Components**: ServerToClientModel, BinaryModel, message boundaries
- **Latency Impact**: Low (~0.5-2ms) - binary encoding overhead
- **Key Files**: `ServerToClientModel.java`, `BinaryModel.java`

### Layer 4: WebSocket Transport Layer
- **Purpose**: WebSocket frame management and latency measurement
- **Components**: WebSocket.java, WebSocketPusher, LatencyTracker, UIBuilder
- **Latency Impact**: Critical measurement layer (tracks all 5 stages)
- **Key Files**: `WebSocket.java`, `LatencyTracker.java`, `UIBuilder.java`

### Layer 3: HTTP/TCP Transport Layer
- **Purpose**: Reliable transport and connection management
- **Components**: Jetty WebSocket implementation, TCP streams
- **Latency Impact**: Network dependent (~1-50ms typical)
- **Key Files**: Jetty libraries, system TCP stack

### Layer 2: Network Layer
- **Purpose**: IP routing and packet forwarding
- **Components**: IP stack, routing tables
- **Latency Impact**: Network topology dependent
- **Key Files**: Operating system network stack

### Layer 1: Physical Layer
- **Purpose**: Physical transmission medium
- **Components**: Network interfaces, cables, radio waves
- **Latency Impact**: Speed of light + hardware processing
- **Key Files**: Hardware drivers

## Latency Measurement Cross-Layer Integration

The **LatencyTracker** operates primarily at **Layer 4** but measures performance across multiple layers:

| Stage | Measurement Point | Layers Involved | Typical Latency |
|-------|------------------|-----------------|-----------------|
| **Stage 1** | Message Intercept | 7 → 6 → 4 | ~0.1ms |
| **Stage 2** | Optimization Processing | 6 (Dictionary/Trie/CodeT5) | 1-500ms |
| **Stage 3** | Protocol Encoding | 6 → 5 → 4 | ~0.5-2ms |
| **Stage 4** | Transport Queuing | 4 | ~0.1-1ms |
| **Stage 5** | Network Transmission | 4 → 3 → 2 → 1 | ~1-50ms |

## Protocol Stack Benefits

This layered approach provides:

1. **Clear Separation of Concerns**: Each layer has specific responsibilities
2. **Proper Latency Attribution**: Can identify which layer contributes to delays
3. **Network Engineering Best Practices**: Follows standard protocol stack design
4. **Debugging Efficiency**: Issues can be isolated to specific layers
5. **Performance Optimization**: Can optimize each layer independently

This **Protocol Stack Diagram** is the proper networking terminology and approach for your WebSocket system architecture!