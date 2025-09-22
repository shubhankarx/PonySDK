/*
 * Copyright (c) 2017 PonySDK
 */

package com.ponysdk.core.server.websocket;

import com.ponysdk.core.model.ServerToClientModel;
import java.io.IOException;

/**
 * Utility class for WebSocket encoding operations.
 * Consolidates repetitive encoding patterns to reduce code duplication.
 */
public final class WebSocketEncodingUtils {

    private WebSocketEncodingUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Helper method to consolidate repetitive encoding pattern.
     * Encodes the model/value and notifies the listener if present.
     */
    public static void encodeAndNotify(WebSocketPusher pusher, WebSocket.Listener listener,
                                     ServerToClientModel model, Object value) throws IOException {
        pusher.encode(model, value);
        if (listener != null) {
            listener.onOutgoingPonyFrame(model, value);
        }
    }
}