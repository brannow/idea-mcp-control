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
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.options.ShowSettingsUtil
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.application.serverConfig
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.connector
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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val MCP_SESSION_ID_HEADER = "mcp-session-id"

/**
 * How long to wait for the engine to report a bound connector before calling the start failed.
 * Binding to loopback is near-instant; this only exists so a hung engine surfaces as an error
 * instead of leaving the UI stuck in STARTING forever.
 */
private const val BIND_TIMEOUT_MS = 15_000L

@Service(Service.Level.PROJECT)
class McpServerService(private val project: Project) : Disposable {

    private var server: EmbeddedServer<*, *>? = null
    private var port: Int = 0
    private val transports = ConcurrentMap<String, StreamableHttpServerTransport>()

    /**
     * Catches anything thrown out of a coroutine we own *or* out of ktor's engine coroutines
     * (it is also passed as the engine's parentCoroutineContext below).
     *
     * This handler is not optional plumbing — without it these throwables are destroyed. We bundle
     * kotlinx-coroutines, so our plugin classloader holds its own kotlinx.coroutines.* Class
     * objects. On the first uncaught coroutine exception, coroutines' global fallback runs
     * ServiceLoader.load(CoroutineExceptionHandler) with our loader, which also sees the
     * platform's META-INF/services registration of IntelliJ's CoroutineExceptionHandlerImpl.
     * That class implements the *platform's* CoroutineExceptionHandler — a different Class object
     * than ours — so the lookup dies with "ServiceConfigurationError: ... not a subtype",
     * permanently poisoning the handler class. Every later uncaught throwable is then replaced by
     * an unrelated NoClassDefFoundError and the real one is never logged. That is exactly why the
     * 0.7.0 start failure on PhpStorm 2025.3 produced no diagnosable error.
     *
     * handleCoroutineException() consults a handler present in the CoroutineContext first and
     * returns, so carrying one here keeps us off that path entirely — no dependency change needed.
     */
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        ApplicationManager.getApplication().invokeLater { handleStartFailure(throwable) }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)

    companion object {
        private val logger = Logger.getInstance(McpServerService::class.java)

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

        // Announce STARTING before anything can fail, so the toolbar has a real state to render
        // rather than inferring one from `server != null`.
        state.starting(port)

        val bindPort = port

        // Built via serverConfig{} rather than the embeddedServer(host, port) shorthand purely so
        // parentCoroutineContext can be set: it makes ktor's engine coroutines inherit our handler.
        // The 2025.3 failure threw inside ktor's module-init coroutine, not one of ours, so a
        // handler on `scope` alone would still have lost it to the poisoned global fallback.
        val rootConfig = serverConfig {
            parentCoroutineContext = exceptionHandler
            module {
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
        }

        val started = embeddedServer(CIO, rootConfig) {
            connector {
                host = "127.0.0.1"
                port = bindPort
            }
        }
        server = started

        scope.launch {
            val boundPort = try {
                started.start(wait = false)
                // start(wait = false) returns before the socket is bound, so it is not evidence of
                // anything. resolvedConnectors() suspends until the engine has actually bound and
                // rethrows the bind failure here. 0.7.0 treated the return of start() as success,
                // which is how it reported a running server with nothing listening.
                val connectors = withTimeoutOrNull(BIND_TIMEOUT_MS) { started.engine.resolvedConnectors() }
                    ?: throw IllegalStateException(
                        "Engine did not report a bound port within ${BIND_TIMEOUT_MS / 1000}s"
                    )
                connectors.firstOrNull()?.port ?: port
            } catch (throwable: Throwable) {
                // Deliberately Throwable, not Exception: the failures seen in the field were
                // LinkageError/NoClassDefFoundError from bundled-library mismatches, which a
                // `catch (e: Exception)` lets straight through into the coroutine machinery.
                //
                // If *we* are the ones being cancelled (project closing -> dispose -> scope.cancel),
                // the throwable is our own shutdown, not a start failure. Reporting it would leave
                // a closing project with a bogus ERROR banner.
                if (!isActive) return@launch
                ApplicationManager.getApplication().invokeLater { handleStartFailure(throwable) }
                return@launch
            }

            ApplicationManager.getApplication().invokeLater {
                // Stop pressed while the engine was still binding: `stop()` already tore the
                // engine down, so flipping to RUNNING here would put the toolbar back to the exact
                // lie this state machine exists to prevent.
                if (state.status != McpServerState.Status.STARTING) return@invokeLater
                port = boundPort
                state.start("HTTP :$boundPort")
            }
        }
    }

    /**
     * Write the failure to idea.log. The error notification tells the user to look there, so this
     * is what makes that true — before this existed the start-failure path caught the throwable
     * and reported it to the UI without ever logging it, which is why a field report of this bug
     * arrived with a one-line message and nothing else.
     *
     * warn(), not error(): error() raises the IDE's "internal error" badge, which is a lie for an
     * occupied port. The stack trace lands in the log either way, which is all we promise the user.
     *
     * Plugin version and IDE build go in the line so a pasted excerpt identifies itself — with an
     * open until-build, "which IDE was this?" is the first question every report raises.
     *
     * [attemptedPort] is passed in rather than read from the field, which [handleStartFailure]
     * zeroes during teardown — the port is the one detail this line exists to carry.
     */
    private fun logFailure(attemptedPort: Int, throwable: Throwable) {
        logger.warn(
            "MCP server failed on port $attemptedPort " +
                "(plugin ${pluginVersion()}, IDE ${ApplicationInfo.getInstance().build.asString()})",
            throwable
        )
    }

    /** Tear down a half-started server and surface the reason. Must run on the EDT. */
    private fun handleStartFailure(throwable: Throwable) {
        val state = McpServerState.getInstance(project)
        // ERROR: already reported, and the first report carried the real cause.
        // STOPPED: the user stopped it (or it never started) -- pressing Stop mid-bind cancels the
        // engine, and that cancellation is the consequence of their action, not a failure. Logging
        // it at warn() with a stack trace would put a scary trace in idea.log for a normal click.
        // debug() keeps it reachable when someone is actually chasing something.
        if (state.status == McpServerState.Status.ERROR || state.status == McpServerState.Status.STOPPED) {
            logger.debug("MCP server failure ignored in state ${state.status}", throwable)
            return
        }

        val attemptedPort = port
        logFailure(attemptedPort, throwable)
        transports.clear()
        try {
            server?.stop(0, 0)
        } catch (secondary: Throwable) {
            logger.warn("Stopping the failed MCP server threw", secondary)
        }
        server = null
        port = 0

        // The notification already special-cased a port conflict; the status line and activity log
        // did not, so the one thing a reporter can copy out of the UI read
        // "JobCancellationException: LazyStandaloneCoroutine is cancelling" for a busy port.
        // Both surfaces now say the same thing.
        val reason = if (isPortConflict(throwable)) "Port $attemptedPort is already in use"
        else describeFailure(throwable)
        state.failed(reason)
        notifyStartFailure(attemptedPort, throwable)
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

    /**
     * Show the real exception (see [describeFailure] for why the raw one is usually a useless
     * wrapper) and point at the log, where [logFailure] writes the stack trace.
     */
    private fun notifyStartFailure(port: Int, cause: Throwable) {
        val portConflict = isPortConflict(cause)
        val title = if (portConflict) "MCP Server: Port $port is already in use"
        else "MCP Server failed to start"
        // The field report this wording comes from: a second PhpStorm instance was already serving
        // MCP on this port, and the agent connected to *that* one. The start failure is visible;
        // the agent quietly driving the other project is not, so name it here.
        val body = if (portConflict) "Another PhpStorm instance or process may be using it. " +
            "An agent connecting to this port reaches that instance, not this project."
        else "${describeFailure(cause)}<br/>See Help &gt; Show Log in Finder for the full stack trace."

        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("MCP Control")
            .createNotification(title, body, NotificationType.ERROR)
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

    // Baked in at build time -- see generatePluginVersion in build.gradle.kts.
    private fun pluginVersion(): String = com.github.brannow.phpstormmcp.PLUGIN_VERSION

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
