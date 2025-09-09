# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

PonySDK is a Java-based web application framework that uses GWT on the frontend and encapsulates a Jetty web server on the backend. It enables writing standard Java code for creating web applications with WebSocket communication between server and client.

## Build Commands

### Building the Project
```bash
# Clean and build the entire project
./gradlew clean build

# Build only the ponysdk module
./gradlew :ponysdk:build

# Build only the sample module
./gradlew :sample:build
```

### Running Tests
```bash
# Run all tests
./gradlew test

# Run tests for a specific module
./gradlew :ponysdk:test

# Run a specific test class
./gradlew :ponysdk:test --tests "*WebSocketTest"

# Run tests with JaCoCo coverage report
./gradlew :ponysdk:test jacocoTestReport
```

### Running the Sample Application
```bash
# Run the Spring-based sample (recommended)
./gradlew runSampleSpring

# Run the Java-based trading sample
./gradlew runSampleTrading

# Run the GWT Code Server for development
./gradlew runCodeServer
```

The sample application will be available at:
- HTTP: http://localhost:8081/sample/
- HTTPS: https://localhost:8082/sample/ (SSL enabled by default)

## Architecture Overview

### Core Components

1. **Server-Side Architecture**
   - `UIContext`: Core session management class that maintains state for each user session
   - `WebSocket`: Handles WebSocket communication with dictionary compression and pattern prediction
   - `ApplicationManager`: Manages application lifecycle and UI contexts
   - `TxnContext`: Transaction management for thread-safe operations
   - `ModelWriter`: Encodes server-to-client messages

2. **Client-Side Architecture (GWT/Terminal)**
   - `UIBuilder`: Processes server instructions and builds UI components
   - `WebSocketClient`: Handles client-side WebSocket communication
   - `PTObject` classes: Client-side representations of UI components

3. **WebSocket Dictionary Optimization**
   - `ModelValueDictionary`: Tracks and manages repeated message patterns
   - `ModelValuePair`: Represents model-value pairs for pattern detection
   - Pattern compression reduces WebSocket traffic by replacing repetitive sequences with references

4. **Communication Protocol**
   - `ServerToClientModel`: Enum defining server-to-client message types
   - `ClientToServerModel`: Enum defining client-to-server message types
   - Binary protocol with JSON fallback for complex data

### Project Structure

- `ponysdk/`: Core framework code
  - `src/main/java/com/ponysdk/core/`
    - `server/`: Server-side components (UIContext, WebSocket, Application management)
    - `terminal/`: GWT client-side code
    - `ui/`: Shared UI component definitions
    - `model/`: Communication protocol models
- `sample/`: Example applications demonstrating framework usage
- `build/`: Build outputs and GWT compilation results

## Development Guidelines

### Working with WebSocket Dictionary Feature

The codebase includes an advanced WebSocket dictionary compression system. Key files:
- `WebSocket.java`: Main implementation with pattern detection
- `ModelValueDictionary.java`: Pattern storage and lookup
- `WebSocketTest.java`: Comprehensive tests including performance benchmarks

When modifying WebSocket communication:
1. Ensure proper UIContext acquisition/release
2. Test with dictionary enabled and disabled
3. Use the performance test framework to measure impact

### Testing WebSocket Features

```bash
# Run WebSocket-specific tests
./gradlew :ponysdk:test --tests "*WebSocketTest"

# Run dictionary extractor tests
./gradlew :ponysdk:test --tests "*DictionaryExtractorTest"

# Run performance tests
./gradlew :ponysdk:test --tests "*WebSocketPerformanceTest"
```

### GWT Compilation

The project uses GWT 2.9.0 for client-side compilation:
```bash
# Compile GWT module (required before packaging)
./gradlew gwtc

# Development mode with detailed output
./gradlew gwtc -Pstyle=DETAILED -Poptimize=0
```

## Current Active Development

Based on the existing cursor.md file, the project has recently implemented:
- WebSocket dictionary compression for reducing network traffic
- Pattern detection and optimization for repetitive UI updates
- Performance testing framework for measuring optimization impact
- Semantic pattern prediction system (experimental)

### Recent Fixes (December 2024)

**Fixed Dictionary Compression Latency Tracking (0ms issue)**
- **Problem**: LatencyTracker showed 0ms for dictionary-compressed messages because certain code paths didn't properly close messages with END/flush
- **Root Cause**: `onFrameWriteSuccess()` callback never fired without proper message termination
- **Fixed Paths**:
  1. Single TYPE command reference (line ~1305): Added END + flush0()
  2. New pattern definition (line ~1679): Added END + flush0() + beginObject() for separate messages  
  3. Batch pattern reference (line ~1351): Added END + flush0()
- **Impact**: All dictionary transmissions now properly record latency metrics instead of showing 0ms

## Key Dependencies

- GWT 2.9.0
- Jetty 9.4.43
- Spring 5.1.3
- Selenium 3.14.0 (for PonyDriver)
- JUnit 4.12 / JUnit 5.8.1
- Java 9+ required

## Debugging

- WebSocket logging: Set log level for `WebSocket-IN` and `WebSocket-OUT` loggers
- Prediction logging: Enable `PredictionLogger` for pattern matching debug info
- Remote debugging: Sample apps configured with port 8888 for debugger attachment