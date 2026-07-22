package com.github.brannow.phpstormmcp.statusbar

import com.github.brannow.phpstormmcp.McpServerStateListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * Single source of truth for what the MCP server is doing.
 *
 * This used to hold only STOPPED/RUNNING, and the toolbar asked [McpServerService] instead
 * (`server != null`, set synchronously in start()). That split let the UI claim two different
 * things at once: the start button greyed out and the stop button armed, while the status line
 * still read "Stopped" and nothing was listening — the reported 0.7.0 symptom on PhpStorm 2025.3.
 * STARTING and ERROR exist so every stage of a start attempt has an honest representation, and
 * so nothing has to guess a state from the presence of an object.
 */
@Service(Service.Level.PROJECT)
class McpServerState(private val project: Project) {

    enum class Status {
        STOPPED,
        STARTING,
        RUNNING,
        ERROR
    }

    var status: Status = Status.STOPPED
        private set

    var transport: String = ""
        private set

    var connectedClients: Int = 0
        private set

    /** Short reason for [Status.ERROR], shown in the tool window. Null in every other state. */
    var errorMessage: String? = null
        private set

    val isRunning: Boolean
        get() = status == Status.RUNNING

    /** A start attempt is in flight: the port is claimed but the engine has not bound yet. */
    fun starting(port: Int) {
        this.status = Status.STARTING
        this.transport = "HTTP :$port"
        this.errorMessage = null
        McpActivityLog.getInstance(project).log("MCP server starting on port $port")
        notifyStateChanged()
    }

    /** The engine reported a bound connector — only now is the server reachable. */
    fun start(transport: String) {
        this.status = Status.RUNNING
        this.transport = transport
        this.errorMessage = null
        McpActivityLog.getInstance(project).log("MCP server started ($transport)")
        notifyStateChanged()
    }

    /**
     * The start attempt failed. Kept distinct from STOPPED so the tool window can show why,
     * instead of silently reverting to a state that looks like "the user never pressed start".
     */
    fun failed(message: String) {
        this.status = Status.ERROR
        this.connectedClients = 0
        this.errorMessage = message
        McpActivityLog.getInstance(project).log("MCP server failed to start: $message")
        notifyStateChanged()
    }

    fun stop() {
        this.status = Status.STOPPED
        this.connectedClients = 0
        this.errorMessage = null
        McpActivityLog.getInstance(project).log("MCP server stopped")
        notifyStateChanged()
    }

    fun clientConnected() {
        connectedClients++
        McpActivityLog.getInstance(project).log("Client connected (total: $connectedClients)")
        notifyStateChanged()
    }

    fun clientDisconnected() {
        connectedClients = (connectedClients - 1).coerceAtLeast(0)
        McpActivityLog.getInstance(project).log("Client disconnected (total: $connectedClients)")
        notifyStateChanged()
    }

    private fun notifyStateChanged() {
        project.messageBus.syncPublisher(McpServerStateListener.TOPIC).stateChanged()
    }

    companion object {
        fun getInstance(project: Project): McpServerState {
            return project.getService(McpServerState::class.java)
        }
    }
}
