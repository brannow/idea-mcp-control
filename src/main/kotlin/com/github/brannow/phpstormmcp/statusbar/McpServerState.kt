package com.github.brannow.phpstormmcp.statusbar

import com.github.brannow.phpstormmcp.McpServerStateListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class McpServerState(private val project: Project) {

    enum class Status {
        STOPPED,
        RUNNING
    }

    var status: Status = Status.STOPPED
        private set

    var transport: String = ""
        private set

    var connectedClients: Int = 0
        private set

    val isRunning: Boolean
        get() = status == Status.RUNNING

    fun start(transport: String) {
        this.status = Status.RUNNING
        this.transport = transport
        McpActivityLog.getInstance(project).log("MCP server started ($transport)")
        notifyStateChanged()
    }

    fun stop() {
        this.status = Status.STOPPED
        this.connectedClients = 0
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
