package com.github.brannow.phpstormmcp

import com.intellij.util.messages.Topic

/**
 * MessageBus topic for MCP server state changes.
 * Decouples state (McpServerState, McpActivityLog) from UI (tool window).
 */
interface McpServerStateListener {
    companion object {
        @JvmField
        val TOPIC: Topic<McpServerStateListener> = Topic.create(
            "MCP Server State",
            McpServerStateListener::class.java
        )
    }

    /** Server started/stopped or client count changed. */
    fun stateChanged()

    /** New activity log entry or log cleared. */
    fun logUpdated()
}
