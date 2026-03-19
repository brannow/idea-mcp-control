package com.github.brannow.phpstormmcp.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

/**
 * Per-project settings for the MCP server plugin.
 * Persisted in .idea/mcp-hub.xml (kept for backwards compatibility).
 */
@Service(Service.Level.PROJECT)
@State(
    name = "com.github.brannow.phpstormmcp.McpSettings",
    storages = [Storage("mcp-hub.xml")]
)
class McpSettings : PersistentStateComponent<McpSettings.State> {

    data class State(
        var port: Int = DEFAULT_PORT,
        var autoStart: Boolean = false
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    var port: Int
        get() = myState.port
        set(value) { myState.port = value }

    var autoStart: Boolean
        get() = myState.autoStart
        set(value) { myState.autoStart = value }

    companion object {
        const val DEFAULT_PORT = 6969

        fun getInstance(project: Project): McpSettings {
            return project.getService(McpSettings::class.java)
        }
    }
}
