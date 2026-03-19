package com.github.brannow.phpstormmcp.server

import com.github.brannow.phpstormmcp.settings.McpSettings
import com.github.brannow.phpstormmcp.settings.McpSettingsConfigurable
import com.github.brannow.phpstormmcp.statusbar.McpServerState
import com.github.brannow.phpstormmcp.tools.registerBreakpointTools
import com.github.brannow.phpstormmcp.tools.registerDebugTools
import com.github.brannow.phpstormmcp.tools.registerNavigationTools
import com.github.brannow.phpstormmcp.tools.registerSessionTools
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.options.ShowSettingsUtil
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.util.collections.ConcurrentMap
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StreamableHttpServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val MCP_SESSION_ID_HEADER = "mcp-session-id"

@Service(Service.Level.PROJECT)
class McpServerService(private val project: Project) : Disposable {

    private var server: EmbeddedServer<*, *>? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var port: Int = 0
    private val transports = ConcurrentMap<String, StreamableHttpServerTransport>()

    companion object {
        fun getInstance(project: Project): McpServerService {
            return project.getService(McpServerService::class.java)
        }
    }

    val isRunning: Boolean
        get() = server != null

    fun start() {
        if (isRunning) return

        val settings = McpSettings.getInstance(project)
        port = settings.port
        val state = McpServerState.getInstance(project)

        server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            install(ContentNegotiation) {
                json(McpJson)
            }
            install(SSE)

            routing {
                route("/mcp") {
                    sse {
                        val transport = findTransport(call) ?: return@sse
                        transport.handleRequest(this, call)
                    }

                    post {
                        val transport = getOrCreateTransport(call, state) ?: return@post
                        transport.handleRequest(null, call)
                    }

                    delete {
                        val transport = findTransport(call) ?: return@delete
                        transport.handleRequest(null, call)
                    }
                }
            }
        }

        scope.launch {
            try {
                server?.start(wait = false)
            } catch (e: Exception) {
                // Port bind failed — clean up and notify on EDT
                server = null
                port = 0
                ApplicationManager.getApplication().invokeLater {
                    notifyPortConflict(settings.port, e)
                }
                return@launch
            }

            // Only mark as started after successful bind
            ApplicationManager.getApplication().invokeLater {
                state.start("HTTP :$port")
            }
        }
    }

    fun stop() {
        transports.clear()
        server?.stop(500, 1000)
        server = null
        port = 0
        McpServerState.getInstance(project).stop()
    }

    override fun dispose() {
        stop()
        scope.cancel()
    }

    private fun notifyPortConflict(port: Int, cause: Exception) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("MCP Control")
            .createNotification(
                "MCP Server: Port $port is already in use",
                "Another PhpStorm instance or process may be using it.",
                NotificationType.ERROR
            )
        notification.addAction(object : AnAction("Change Port") {
            override fun actionPerformed(e: AnActionEvent) {
                notification.expire()
                ShowSettingsUtil.getInstance().showSettingsDialog(
                    project, McpSettingsConfigurable::class.java
                )
            }
        })
        notification.addAction(object : AnAction("Retry") {
            override fun actionPerformed(e: AnActionEvent) {
                notification.expire()
                start()
            }
        })
        notification.notify(project)
    }

    private suspend fun findTransport(call: ApplicationCall): StreamableHttpServerTransport? {
        val sessionId = call.request.header(MCP_SESSION_ID_HEADER)
        if (sessionId.isNullOrEmpty()) {
            call.respond(HttpStatusCode.BadRequest, "Bad Request: No valid session ID provided")
            return null
        }
        val transport = transports[sessionId]
        if (transport == null) {
            call.respond(HttpStatusCode.NotFound, "Session not found")
        }
        return transport
    }

    private suspend fun getOrCreateTransport(
        call: ApplicationCall,
        state: McpServerState
    ): StreamableHttpServerTransport? {
        val sessionId = call.request.header(MCP_SESSION_ID_HEADER)
        if (sessionId != null) {
            val transport = transports[sessionId]
            if (transport == null) {
                call.respond(HttpStatusCode.NotFound, "Session not found")
            }
            return transport
        }

        val configuration = StreamableHttpServerTransport.Configuration(enableJsonResponse = true)
        val transport = StreamableHttpServerTransport(configuration)

        transport.setOnSessionInitialized { initializedSessionId ->
            transports[initializedSessionId] = transport
            state.clientConnected()
        }

        transport.setOnSessionClosed { closedSessionId ->
            transports.remove(closedSessionId)
            state.clientDisconnected()
        }

        val mcpServer = createMcpServer()
        mcpServer.onClose {
            transport.sessionId?.let { transports.remove(it) }
        }
        mcpServer.createSession(transport)

        return transport
    }

    private fun pluginVersion(): String {
        return com.intellij.ide.plugins.PluginManagerCore.getPlugin(
            com.intellij.openapi.extensions.PluginId.getId("com.github.brannow.phpstormmcp")
        )?.version ?: "unknown"
    }

    private fun createMcpServer(): Server {
        return Server(
            serverInfo = Implementation(
                name = "mcp-control",
                version = pluginVersion()
            ),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = true),
                )
            )
        ).apply {
            registerBreakpointTools(project)
            registerSessionTools(project)
            registerDebugTools(project)
            registerNavigationTools(project)
        }
    }
}
