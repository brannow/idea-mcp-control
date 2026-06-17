package com.github.brannow.phpstormmcp.tools

import com.github.brannow.phpstormmcp.statusbar.McpActivityLog
import com.intellij.execution.console.ConsoleViewWrapperBase
import com.intellij.execution.console.DuplexConsoleView
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.frame.XStackFrame
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.*

/**
 * Tracks which session the agent is working with.
 * Detects when the active session changes externally (e.g., user clicks a different debug tab)
 * and informs the agent via a notice prepended to the next tool response.
 *
 * Updated by: resolvePausedSession (on success), session_stop (auto-track remaining)
 * Cleared by: session_stop (all), session ended
 */
internal object AgentSessionTracker {
    @Volatile
    var lastSessionId: String? = null

    @Volatile
    var pendingNotice: String? = null

    fun track(session: XDebugSession) {
        lastSessionId = System.identityHashCode(session).toString()
    }

    fun trackById(sessionId: String) {
        lastSessionId = sessionId
    }

    fun clear() {
        lastSessionId = null
        pendingNotice = null
    }

    fun consumeNotice(): String? {
        val n = pendingNotice
        pendingNotice = null
        return n
    }

    /**
     * Check if the active session changed unexpectedly.
     * If switched, auto-tracks the new session and sets [pendingNotice].
     * Returns the notice text, or null if no switch occurred.
     */
    fun checkSessionSwitch(
        currentSessionId: String,
        currentSessionName: String,
        previousSessionStatus: (lastId: String) -> String
    ): String? {
        val lastId = lastSessionId ?: return null
        if (lastId == currentSessionId) return null

        // Auto-track the new session
        lastSessionId = currentSessionId

        val status = previousSessionStatus(lastId)
        val notice = "Note: Active session changed from #$lastId ($status) to #$currentSessionId \"$currentSessionName\".\n" +
            "Ask the user to switch debug tabs in PhpStorm if you need session #$lastId."
        pendingNotice = notice
        return notice
    }
}

/**
 * Wraps a successful [CallToolResult] with a session switch notice if one is pending.
 * Consumes the notice so it only appears once.
 */
internal fun withSessionNotice(result: CallToolResult): CallToolResult {
    val notice = AgentSessionTracker.consumeNotice() ?: return result
    if (result.isError == true) return result
    val first = result.content.firstOrNull()
    if (first is TextContent) {
        return CallToolResult(
            content = listOf(TextContent("$notice\n\n${first.text}")) + result.content.drop(1),
            isError = result.isError
        )
    }
    return result
}

/**
 * Resolve the active debug session (must not be stopped, but can be running or paused).
 * Includes session switch detection.
 */
internal fun resolveActiveSession(project: Project): Pair<XDebugSession?, CallToolResult?> {
    val manager = XDebuggerManager.getInstance(project)
    val sessionService = SessionService.getInstance(project)
    val session = manager.currentSession

    if (session == null || session.isStopped) {
        AgentSessionTracker.clear()
        val alive = sessionService.listSessions()
        if (alive.isEmpty()) {
            return null to err("No debug session")
        }
        return null to err("Session ended. Other sessions:\n\n${formatSessionList(alive)}\n\nThe IDE does not auto-activate a remaining session. Ask the user to select the desired session in the IDE's Debug tool window, then call your tool again.")
    }

    // Check for unexpected session switch
    val currentId = System.identityHashCode(session).toString()
    val lastId = AgentSessionTracker.lastSessionId
    if (lastId != null && lastId != currentId) {
        // Previous session terminated? Auto-track the new one — no need to block.
        val previousSession = manager.debugSessions.firstOrNull {
            System.identityHashCode(it).toString() == lastId
        }
        val previousAlive = previousSession != null && !previousSession.isStopped

        if (previousAlive) {
            // Previous session is still alive — inform the agent but don't block
            val status = if (previousSession!!.isSuspended) "paused" else "running"
            AgentSessionTracker.checkSessionSwitch(
                currentSessionId = currentId,
                currentSessionName = session.sessionName
            ) { status }
            // Notice is stored on AgentSessionTracker.pendingNotice,
            // consumed by withSessionNotice() in the tool handler
        } else {
            // Previous session terminated — let the agent know
            AgentSessionTracker.pendingNotice = "Note: Previous session #$lastId terminated. Now using session #$currentId \"${session.sessionName}\"."
        }
    }

    AgentSessionTracker.track(session)
    return session to null
}

/**
 * Resolve the active paused debug session, or return an error result.
 * Always uses the current (active) session — the one focused in the IDE's debug panel.
 *
 * If the active session changed externally (user switched tabs), a notice is stored
 * on [AgentSessionTracker.pendingNotice] and consumed by [withSessionNotice].
 */
internal fun resolvePausedSession(project: Project): Pair<XDebugSession?, CallToolResult?> {
    val (session, error) = resolveActiveSession(project)
    if (error != null) return null to error
    if (!session!!.isSuspended) return null to err("Session is running — not paused at a breakpoint")
    return session to null
}

/**
 * A stack frame the tool resolved to operate on, plus its depth in the stack
 * (used to mark the active frame in a snapshot's stacktrace).
 */
internal data class ResolvedFrame(val frame: XStackFrame, val index: Int)

/**
 * Resolve which stack frame a debug tool should read/evaluate against.
 *
 * - [frameIndex] != null → that exact frame, and switch the IDE's Frames panel to it
 *   (like clicking the row) so the human's panel and the agent's view stay in sync.
 * - [frameIndex] == null → the frame currently selected in the session
 *   (`session.currentStackFrame`), falling back to the top frame. This is what makes
 *   "evaluate where I'm looking" work: if the human clicked a deeper frame — or a prior
 *   `debug_snapshot(frame_index=N)` selected one — evaluate/variable_detail/snapshot all
 *   follow that selection instead of silently reverting to frame #0.
 *
 * `currentStackFrame` reflects UI/session state, so it's read on the EDT.
 * [computeIndex] (snapshot only) determines the active-frame marker; evaluate and
 * variable_detail don't care about depth, so they skip the extra stack walk.
 */
internal fun resolveFrame(
    session: XDebugSession,
    stackFrameService: StackFrameService,
    frameIndex: Int?,
    computeIndex: Boolean = false
): Pair<ResolvedFrame?, CallToolResult?> {
    val suspendContext = session.suspendContext
        ?: return null to err("No suspend context — session may be between steps")
    val executionStack = suspendContext.activeExecutionStack
        ?: return null to err("No execution stack available")

    if (frameIndex != null) {
        if (frameIndex < 0) return null to err("frame_index must be >= 0")
        val rawFrames = stackFrameService.getRawFrames(suspendContext)
        if (rawFrames.isEmpty()) return null to err("No stack frames available")
        if (frameIndex >= rawFrames.size) {
            return null to err("frame_index $frameIndex out of range (0..${rawFrames.size - 1})")
        }
        val target = rawFrames[frameIndex]
        ApplicationManager.getApplication().invokeAndWait {
            session.setCurrentStackFrame(executionStack, target, frameIndex == 0)
        }
        return ResolvedFrame(target, frameIndex) to null
    }

    // Follow the selected frame; fall back to the top frame.
    var selected: XStackFrame? = null
    ApplicationManager.getApplication().invokeAndWait {
        selected = session.currentStackFrame
    }
    val topFrame = executionStack.topFrame
    val frame = selected ?: topFrame
        ?: return null to err("No stack frame available")

    val index = when {
        !computeIndex -> 0
        selected == null || selected === topFrame -> 0
        else -> {
            val rawFrames = stackFrameService.getRawFrames(suspendContext)
            val byRef = rawFrames.indexOfFirst { it === frame }
            if (byRef >= 0) byRef else {
                // Fall back to source-position match when frame instances aren't identical.
                val pos = frame.sourcePosition
                rawFrames.indexOfFirst {
                    it.sourcePosition?.file == pos?.file && it.sourcePosition?.line == pos?.line
                }.let { if (it >= 0) it else 0 }
            }
        }
    }
    return ResolvedFrame(frame, index) to null
}

/**
 * Shared data extraction from a paused session, scoped to a specific frame.
 * Used by debug_snapshot, debug_variable_detail, and the navigation snapshot builder.
 */
internal fun extractSourceContext(
    frame: XStackFrame,
    sourceService: SourceContextService
): SourceContext? {
    val position = frame.sourcePosition ?: return null
    return sourceService.getSourceContext(position.file, position.line + 1)
}

internal fun extractStackFrames(
    session: XDebugSession,
    stackFrameService: StackFrameService
): List<FrameInfo>? {
    val suspendContext = session.suspendContext ?: return null
    return stackFrameService.getStackFrames(suspendContext)
}

internal fun extractVariables(
    frame: XStackFrame,
    variableService: VariableService
): List<VariableInfo>? {
    return variableService.getVariables(frame)
}

/**
 * Recursively unwrap a ConsoleView to find the underlying ConsoleViewImpl.
 * Handles DuplexConsoleView (PHP debug uses this) and ConsoleViewWrapperBase.
 */
internal fun findConsoleViewImpl(view: ConsoleView?): ConsoleViewImpl? {
    return when (view) {
        is ConsoleViewImpl -> view
        is DuplexConsoleView<*, *> -> {
            findConsoleViewImpl(view.primaryConsoleView as? ConsoleView)
                ?: findConsoleViewImpl(view.secondaryConsoleView as? ConsoleView)
        }
        is ConsoleViewWrapperBase -> findConsoleViewImpl(view.delegate)
        else -> null
    }
}

/**
 * Read the session's console buffer (stdout/stderr), optionally tailed to the last [tail] lines.
 * Flushes pending output on the EDT first. Returns null when there's no console or it's empty.
 * Defensive: never throws — a console read must not break the caller (e.g. the "Session ended"
 * message, where the process just terminated and the view may be mid-teardown).
 */
internal fun readConsoleOutput(session: XDebugSession, tail: Int = 0): String? {
    return try {
        val consoleImpl = findConsoleViewImpl(session.consoleView as? ConsoleView) ?: return null
        ApplicationManager.getApplication().invokeAndWait {
            consoleImpl.flushDeferredText()
        }
        val text = consoleImpl.editor?.document?.text ?: ""
        if (text.isBlank()) return null
        if (tail > 0) text.lines().takeLast(tail).joinToString("\n") else text
    } catch (e: ProcessCanceledException) {
        throw e
    } catch (_: Exception) {
        null
    }
}

/** Lines of console output appended to a "Session ended" result (the last chance to see it — once
 *  the session is stopped, debug_console can no longer reach it). */
internal const val CONSOLE_TAIL_ON_END = 50

private val readOnlyAnnotations = ToolAnnotations(
    readOnlyHint = true,
    destructiveHint = false,
    idempotentHint = false,
    openWorldHint = false,
)

/**
 * debug_snapshot is overwhelmingly a pure read, but with the absorbed frame_index it can
 * switch the IDE's Frames panel (the behavior inherited from the old debug_inspect_frame).
 * Because tool annotations are static (can't flip per-call), we honestly mark it non-readonly.
 */
private val snapshotAnnotations = ToolAnnotations(
    readOnlyHint = false,
    destructiveHint = false,
    idempotentHint = false,
    openWorldHint = false,
)


fun Server.registerDebugTools(project: Project) {
    val activityLog = McpActivityLog.getInstance(project)
    val sourceService = SourceContextService.getInstance(project)
    val stackFrameService = StackFrameService.getInstance(project)
    val variableService = VariableService.getInstance(project)
    val sessionService = SessionService.getInstance(project)

    // --- debug_variable_detail ---
    addTool(
        name = "debug_variable_detail",
        description = "Expand a variable's properties and nested children. " +
                "Use when debug_snapshot shows a type preview like {User} or array(5) that you need to see inside. " +
                "Requires a paused session.",
        toolAnnotations = readOnlyAnnotations,
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("path") {
                    put("type", "string")
                    put("description", "Variable path(s) using dot notation. Comma-separated for multiple. " +
                            "Omit to show all variables. " +
                            "Examples: \"\$engine\", \"\$engine.pattern\", \"\$engine, \$result\"")
                }
                putJsonObject("depth") {
                    put("type", "integer")
                    put("description", "How many levels of children to expand. Default: 1. " +
                            "Use 0 for just type and value without expanding children.")
                }
                putJsonObject("offset") {
                    put("type", "integer")
                    put("description", "Skip this many children of the target node before expanding. Default: 0. " +
                            "Use with limit to page through large collections.")
                }
                putJsonObject("limit") {
                    put("type", "integer")
                    put("description", "Max children of the target node to expand. Default: ${VariableService.DEFAULT_CHILD_LIMIT}. " +
                            "Larger collections show a \"... N more children\" hint.")
                }
                putJsonObject("frame_index") {
                    put("type", "integer")
                    put("description", "Stack frame to read from: 0 = top, 1 = caller, etc. " +
                            "Omit to use the currently selected frame (default: top, or whatever you/the user selected).")
                }
                putJsonObject("globals") {
                    put("type", "boolean")
                    put("description", "Include PHP superglobals (\$_SERVER, \$_ENV, etc.). Default: false. Only applies when no path is specified.")
                }
            },
            required = emptyList()
        )
    ) { request ->
        activityLog.log(formatToolCall("debug_variable_detail", request.arguments))
        try {
            val path = request.arguments?.get("path")?.jsonPrimitive?.content
            val depth = request.arguments?.get("depth")?.jsonPrimitive?.intOrNull ?: 1
            val offset = request.arguments?.get("offset")?.jsonPrimitive?.intOrNull ?: 0
            val limit = request.arguments?.get("limit")?.jsonPrimitive?.intOrNull ?: VariableService.DEFAULT_CHILD_LIMIT
            val frameIndex = request.arguments?.get("frame_index")?.jsonPrimitive?.intOrNull
            val includeGlobals = request.arguments?.get("globals")?.jsonPrimitive?.booleanOrNull ?: false
            val (session, error) = resolvePausedSession(project)
            if (error != null) return@addTool error

            val (resolved, frameError) = resolveFrame(session!!, stackFrameService, frameIndex)
            if (frameError != null) return@addTool frameError
            val frame = resolved!!.frame

            if (path == null) {
                // No path → expand all top-level variables
                val nodes = variableService.getAllVariableDetails(frame, depth)
                withSessionNotice(ok(formatVariableDetailList(filterGlobalNodes(nodes, includeGlobals))))
            } else {
                // Split comma-separated paths
                val paths = path.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (paths.size == 1) {
                    val node = variableService.getVariableDetail(frame, paths.first(), depth, offset, limit)
                    withSessionNotice(ok(formatVariableDetail(node, paths.first())))
                } else {
                    val nodes = paths.map { p ->
                        p to variableService.getVariableDetail(frame, p, depth, offset, limit)
                    }
                    withSessionNotice(ok(nodes.joinToString("\n\n") { (p, node) -> formatVariableDetail(node, p) }))
                }
            }
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: VariablePathException) {
            err(e.message ?: "Variable not found")
        } catch (e: Exception) {
            err(e.message ?: "Unknown error")
        }
    }

    // --- debug_evaluate ---
    addTool(
        name = "debug_evaluate",
        description = "Evaluate a PHP expression in the selected frame's scope — " +
                "test ideas, call methods, or modify variables. " +
                "Evaluates in the currently selected frame (frame #0 by default, or whichever frame " +
                "you/the user selected); pass frame_index to target a specific frame. " +
                "Note: Xdebug can't evaluate in non-top frames — to read a caller's state use " +
                "debug_variable_detail or debug_snapshot with frame_index instead. Requires a paused session.",
        toolAnnotations = ToolAnnotations(
            readOnlyHint = false,
            destructiveHint = false,
            idempotentHint = false,
            openWorldHint = true,
        ),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("expression") {
                    put("type", "string")
                    put("description", "PHP expression to evaluate. Examples: " +
                            "\"count(\$items)\", \"\$user->getName()\", " +
                            "\"\$this->repository->findAll()\", \"array_keys(\$config)\"")
                }
                putJsonObject("depth") {
                    put("type", "integer")
                    put("description", "Expansion depth for object/array results. Default: 1 (shows immediate properties). " +
                            "Use 0 for just type and value, 2+ for deeper nesting.")
                }
                putJsonObject("offset") {
                    put("type", "integer")
                    put("description", "Skip this many children of the result before expanding. Default: 0. " +
                            "Use with limit to page through large arrays/collections.")
                }
                putJsonObject("limit") {
                    put("type", "integer")
                    put("description", "Max children of the result to expand. Default: ${VariableService.DEFAULT_CHILD_LIMIT}. " +
                            "Larger results show a \"... N more children\" hint.")
                }
                putJsonObject("frame_index") {
                    put("type", "integer")
                    put("description", "Stack frame to evaluate in: 0 = top, 1 = caller, etc. " +
                            "Omit to use the currently selected frame (default: top, or whatever you/the user selected).")
                }
            },
            required = listOf("expression")
        )
    ) { request ->
        activityLog.log(formatToolCall("debug_evaluate", request.arguments))
        try {
            val expression = request.arguments?.get("expression")?.jsonPrimitive?.content
                ?: return@addTool err("Missing required parameter: expression")
            val depth = request.arguments?.get("depth")?.jsonPrimitive?.intOrNull ?: 1
            val offset = request.arguments?.get("offset")?.jsonPrimitive?.intOrNull ?: 0
            val limit = request.arguments?.get("limit")?.jsonPrimitive?.intOrNull ?: VariableService.DEFAULT_CHILD_LIMIT
            val frameIndex = request.arguments?.get("frame_index")?.jsonPrimitive?.intOrNull
            val (session, error) = resolvePausedSession(project)
            if (error != null) return@addTool error

            // computeIndex so we know whether the eval ran in a non-top frame. That case is the
            // ambiguous one — the hybrid frame model makes eval follow mutable session state, so
            // we both (a) confirm where a non-top eval ran and (b) redirect when Xdebug refuses it.
            val (resolved, frameError) = resolveFrame(session!!, stackFrameService, frameIndex, computeIndex = true)
            if (frameError != null) return@addTool frameError
            val rf = resolved!!
            val frame = rf.frame

            val node = try {
                variableService.evaluateExpression(frame, expression, depth, offset, limit)
            } catch (e: EvaluationException) {
                val msg = (e.message ?: "Unknown error")
                    .removePrefix("error evaluating code: ")
                    .removePrefix("Error evaluating code: ")
                // Xdebug can't evaluate in a non-top frame — turn the dead-end into a redirect
                // toward the read-only tools, which read frame state without evaluating.
                val hint = if (rf.index != 0) {
                    "\n\nXdebug can't evaluate in a non-top frame. To read frame #${rf.index}'s state, use " +
                        "debug_variable_detail or debug_snapshot with frame_index=${rf.index} " +
                        "(those read variables without evaluating)."
                } else ""
                return@addTool err(msg + hint)
            }

            // For a non-top frame, prepend a one-line frame header so the result isn't ambiguous
            // about which scope produced it. Top-frame evals stay headerless (the common, lean case).
            val result = formatEvaluationResult(expression, node)
            val output = if (rf.index != 0) {
                val header = extractSourceContext(frame, sourceService)
                    ?.let { "frame #${rf.index}: ${formatSourceHeader(it)}" }
                    ?: "frame #${rf.index}"
                "$header\n\n$result"
            } else {
                result
            }
            withSessionNotice(ok(output))
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            err(e.message ?: "Unknown error")
        }
    }

    // --- debug_snapshot ---
    addTool(
        name = "debug_snapshot",
        description = "Get the current debug state: position, source code, variables, and call stack. " +
                "Pass frame_index to inspect a caller's scope (like clicking a row in the Frames panel). " +
                "Use debug_variable_detail to expand variables shown as previews. Requires a paused session.",
        toolAnnotations = snapshotAnnotations,
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("include") {
                    put("type", "array")
                    put("description", "Parts to include: \"source\", \"variables\", \"stacktrace\". " +
                            "Omit for full snapshot. Session info is always included.")
                    putJsonObject("items") {
                        put("type", "string")
                        putJsonArray("enum") {
                            add("source")
                            add("variables")
                            add("stacktrace")
                        }
                    }
                }
                putJsonObject("frame_index") {
                    put("type", "integer")
                    put("description", "Inspect a different call-stack depth: 0 = current (top), 1 = caller, etc. " +
                            "Omit to use the currently selected frame. Switches the IDE's Frames panel to match.")
                }
                putJsonObject("globals") {
                    put("type", "boolean")
                    put("description", "Include PHP superglobals (\$_SERVER, \$_ENV, \$_GET, etc.). Default: false.")
                }
                putJsonObject("expand_stack") {
                    put("type", "boolean")
                    put("description", "Show all stack frames including library frames. " +
                            "Default: false (consecutive library frames are collapsed).")
                }
            },
            required = emptyList()
        )
    ) { request ->
        activityLog.log(formatToolCall("debug_snapshot", request.arguments))
        try {
            val (session, error) = resolvePausedSession(project)
            if (error != null) return@addTool error

            val includeParam = request.arguments?.get("include")?.jsonArray
                ?.map { it.jsonPrimitive.content }
                ?.toSet()
                ?.ifEmpty { null }
            val frameIndex = request.arguments?.get("frame_index")?.jsonPrimitive?.intOrNull
            val includeGlobals = request.arguments?.get("globals")?.jsonPrimitive?.booleanOrNull ?: false
            val expandStack = request.arguments?.get("expand_stack")?.jsonPrimitive?.booleanOrNull ?: false
            // null = include everything
            val includeSource = includeParam == null || "source" in includeParam
            val includeVars = includeParam == null || "variables" in includeParam
            val includeStack = includeParam == null || "stacktrace" in includeParam

            val (resolved, frameError) = resolveFrame(session!!, stackFrameService, frameIndex, computeIndex = true)
            if (frameError != null) return@addTool frameError
            val frame = resolved!!.frame

            val sessionInfo = sessionService.listSessions().firstOrNull {
                it.id == System.identityHashCode(session).toString()
            }

            val source = if (includeSource) extractSourceContext(frame, sourceService) else null
            val filtered = if (includeVars) {
                extractVariables(frame, variableService)?.let { filterGlobals(it, includeGlobals) }
            } else null
            val frames = if (includeStack) extractStackFrames(session, stackFrameService) else null

            withSessionNotice(ok(formatSnapshot(sessionInfo, source, filtered?.variables, frames, activeDepth = resolved.index, collapseLibrary = !expandStack, hiddenGlobalCount = filtered?.hiddenGlobalCount ?: 0)))
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            err(e.message ?: "Unknown error")
        }
    }

    // --- debug_console ---
    addTool(
        name = "debug_console",
        description = "Read the debug session's console output (stdout/stderr). " +
                "Useful when the PHP process runs in Docker or a remote environment where the agent can't see stdout directly.",
        toolAnnotations = readOnlyAnnotations,
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("tail") {
                    put("type", "integer")
                    put("description", "Return only the last N lines. Default: 0 (all output).")
                }
            },
            required = emptyList()
        )
    ) { request ->
        activityLog.log(formatToolCall("debug_console", request.arguments))
        try {
            val tail = request.arguments?.get("tail")?.jsonPrimitive?.intOrNull ?: 0
            val (session, error) = resolveActiveSession(project)
            if (error != null) return@addTool error

            if (findConsoleViewImpl(session!!.consoleView as? ConsoleView) == null) {
                return@addTool err("Console not available")
            }
            val output = readConsoleOutput(session, tail)
            withSessionNotice(ok(output ?: "(empty)"))
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            err(e.message ?: "Unknown error")
        }
    }
}
