package com.ponysdk.core.server.websocket;

/**
 * A *deterministic* unit-test operation.  Instead of reacting to unpredictable
 * UI callbacks we drive the system under test with a fixed sequence of
 * {@link TestTask tasks}.  This dramatically simplifies reproducibility and
 * makes it trivial to measure network, memory and latency for each step.
 */
public interface TestTask {

    /**
     * Execute one logical step (e.g. change label text, add a widget, …).
     * *Implementations must be side-effect free apart from the WebSocket
     * traffic they deliberately generate.*
     */
    void run();

    /**
     * @return a short stable identifier (no spaces) – used in logs.
     */
    String id();

    /**
     * @return human readable description for debugging.
     */
    String description();
} 