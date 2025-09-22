/*
 * Copyright (c) 2017 PonySDK
 */

package com.ponysdk.core.server.websocket;

import com.ponysdk.core.model.ServerToClientModel;
import java.util.Set;

/**
 * Utility class for ServerToClientModel validation and categorization.
 * Uses Set-based O(1) lookups for efficient model type checking.
 */
public final class ModelValidationUtils {

    private ModelValidationUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * TYPE_* commands that create or modify widgets
     */
    private static final Set<ServerToClientModel> TYPE_COMMANDS = Set.of(
        ServerToClientModel.TYPE_CREATE, ServerToClientModel.TYPE_UPDATE,
        ServerToClientModel.TYPE_ADD, ServerToClientModel.TYPE_REMOVE,
        ServerToClientModel.TYPE_ADD_HANDLER, ServerToClientModel.TYPE_REMOVE_HANDLER,
        ServerToClientModel.TYPE_GC);

    /**
     * Critical protocol frames that should bypass dictionary compression
     */
    private static final Set<ServerToClientModel> CONTROL_FRAMES = Set.of(
        ServerToClientModel.CREATE_CONTEXT, ServerToClientModel.OPTION_FORMFIELD_TABULATION,
        ServerToClientModel.HEARTBEAT_PERIOD, ServerToClientModel.HEARTBEAT,
        ServerToClientModel.ROUNDTRIP_LATENCY, ServerToClientModel.TYPE_ADD_HANDLER,
        ServerToClientModel.TYPE_REMOVE_HANDLER, ServerToClientModel.HANDLER_TYPE,
        ServerToClientModel.WINDOW_ID, ServerToClientModel.FRAME_ID,
        ServerToClientModel.FUNCTION_ID, ServerToClientModel.DICTIONARY_PATTERN_START,
        ServerToClientModel.DICTIONARY_REFERENCE, ServerToClientModel.END);

    /**
     * Check if a model is a TYPE_* command (O(1) lookup)
     */
    public static boolean isTypeCommand(ServerToClientModel model) {
        return TYPE_COMMANDS.contains(model);
    }

    /**
     * Check if a model is a critical control frame that should bypass dictionary (O(1) lookup)
     */
    public static boolean isControlFrame(ServerToClientModel model) {
        return CONTROL_FRAMES.contains(model);
    }
}