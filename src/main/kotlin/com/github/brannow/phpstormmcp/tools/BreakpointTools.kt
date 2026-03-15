package com.github.brannow.phpstormmcp.tools

import com.github.brannow.phpstormmcp.statusbar.McpActivityLog
import com.intellij.openapi.project.Project
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

private fun parseLocation(location: String): Pair<String, Int>? {
    val colonIndex = location.lastIndexOf(':')
    if (colonIndex <= 0) return null
    val file = location.substring(0, colonIndex)
    val line = location.substring(colonIndex + 1).toIntOrNull() ?: return null
    return file to line
}

/**
 * Not-found response: show the agent what exists so it can self-correct.
 */
private fun notFoundResponse(query: String, service: BreakpointService): CallToolResult {
    val all = service.listBreakpoints()
    if (all.isEmpty()) return err("No breakpoints in project found")

    // Try narrowing to breakpoints matching the query
    val filtered = service.listBreakpoints(query)
    return if (filtered.isNotEmpty()) {
        err("Breakpoint '$query' not found, matching breakpoints are:\n\n${formatBreakpointList(filtered)}")
    } else {
        err("Breakpoint '$query' not found, current breakpoints are:\n\n${formatBreakpointList(all)}")
    }
}

/**
 * Ambiguous response: list all at that location + guidance.
 */
private fun ambiguousResponse(query: String, breakpoints: List<BreakpointInfo>): CallToolResult {
    val location = if (breakpoints.isNotEmpty()) "${breakpoints[0].file}:${breakpoints[0].line}" else query
    return err("${formatBreakpointGroup(location, breakpoints)}\n\nChoose a breakpoint via #ID or remove other breakpoints first.")
}

fun Server.registerBreakpointTools(project: Project) {
    val service = BreakpointService.getInstance(project)
    val activityLog = McpActivityLog.getInstance(project)

    // --- breakpoint_list ---
    addTool(
        name = "breakpoint_list",
        description = "List all breakpoints. Optionally filter by file path substring.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("file") {
                    put("type", "string")
                    put("description", "Filter breakpoints by file path (substring match)")
                }
            },
            required = emptyList()
        )
    ) { request ->
        val fileFilter = request.arguments?.get("file")?.jsonPrimitive?.content
        activityLog.log("breakpoint_list" + if (fileFilter != null) " (file: $fileFilter)" else "")
        try {
            val breakpoints = service.listBreakpoints(fileFilter)
            when {
                breakpoints.isEmpty() && fileFilter != null && !service.fileExists(fileFilter) ->
                    ok("File '$fileFilter' not found")
                breakpoints.isEmpty() && fileFilter != null ->
                    ok("No breakpoints in $fileFilter found")
                breakpoints.isEmpty() ->
                    ok("No breakpoints in project found")
                else ->
                    ok(formatBreakpointList(breakpoints))
            }
        } catch (e: Exception) {
            err("Error: ${e.message}")
        }
    }

    // --- breakpoint_add ---
    addTool(
        name = "breakpoint_add",
        description = "Add a line breakpoint. Optionally set a condition, log expression, or disable suspension.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("location") {
                    put("type", "string")
                    put("description", "File path and line, e.g. \"src/index.php:15\"")
                }
                putJsonObject("condition") {
                    put("type", "string")
                    put("description", "PHP expression that must be true for the breakpoint to trigger, e.g. \"\$count > 10\"")
                }
                putJsonObject("log_expression") {
                    put("type", "string")
                    put("description", "PHP expression to evaluate and log when the breakpoint is hit, e.g. \"\$request->getUri()\"")
                }
                putJsonObject("suspend") {
                    put("type", "boolean")
                    put("description", "Whether to pause execution when hit (default: true). Set to false for logging-only breakpoints.")
                }
            },
            required = listOf("location")
        )
    ) { request ->
        val location = request.arguments?.get("location")?.jsonPrimitive?.content
            ?: return@addTool err("Error: 'location' is required")
        val (file, line) = parseLocation(location)
            ?: return@addTool err("Error: Invalid location format. Expected file:line, e.g. \"src/index.php:15\"")
        val condition = request.arguments?.get("condition")?.jsonPrimitive?.content
        val logExpression = request.arguments?.get("log_expression")?.jsonPrimitive?.content
        val suspend = request.arguments?.get("suspend")?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true

        activityLog.log("breakpoint_add $location")
        try {
            val result = service.addBreakpoint(file, line, condition, logExpression, suspend)
            val text = StringBuilder(formatBreakpoint(result.breakpoint))

            if (result.existingBreakpoints.isNotEmpty()) {
                val loc = "${result.breakpoint.file}:${result.breakpoint.line}"
                text.append("\n\n$loc also has other breakpoints:\n")
                text.append(formatBreakpointGroupChildren(result.existingBreakpoints))
            }

            ok(text.toString())
        } catch (e: Exception) {
            err("Error: ${e.message}")
        }
    }

    // --- breakpoint_update ---
    addTool(
        name = "breakpoint_update",
        description = "Update an existing breakpoint's properties. Only provided fields are changed.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("id") {
                    put("type", "string")
                    put("description", "Breakpoint #ID or file:line reference, e.g. \"src/index.php:5\"")
                }
                putJsonObject("enabled") {
                    put("type", "boolean")
                    put("description", "Enable or disable the breakpoint")
                }
                putJsonObject("condition") {
                    put("type", "string")
                    put("description", "New condition expression. Empty string to remove.")
                }
                putJsonObject("log_expression") {
                    put("type", "string")
                    put("description", "New log expression. Empty string to remove.")
                }
                putJsonObject("suspend") {
                    put("type", "boolean")
                    put("description", "Whether to pause execution when hit")
                }
            },
            required = listOf("id")
        )
    ) { request ->
        val id = request.arguments?.get("id")?.jsonPrimitive?.content
            ?: return@addTool err("Error: 'id' is required")
        val enabled = request.arguments?.get("enabled")?.jsonPrimitive?.content?.toBooleanStrictOrNull()
        val condition = request.arguments?.get("condition")?.jsonPrimitive?.content
        val logExpression = request.arguments?.get("log_expression")?.jsonPrimitive?.content
        val suspend = request.arguments?.get("suspend")?.jsonPrimitive?.content?.toBooleanStrictOrNull()

        activityLog.log("breakpoint_update $id")
        try {
            val bp = service.updateBreakpoint(id, enabled, condition, logExpression, suspend)
            ok(formatBreakpoint(bp))
        } catch (e: AmbiguousBreakpointException) {
            ambiguousResponse(id, e.breakpoints)
        } catch (e: BreakpointNotFoundException) {
            notFoundResponse(id, service)
        } catch (e: Exception) {
            err("Error: ${e.message}")
        }
    }

    // --- breakpoint_remove ---
    addTool(
        name = "breakpoint_remove",
        description = "Remove breakpoints by #ID, file:line, or file path (removes all breakpoints in that file). Comma-separated for multiple. Use all=true to remove ALL breakpoints.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("id") {
                    put("type", "string")
                    put("description", "Breakpoint #ID(s), file:line, or file path to purge. Comma-separated for multiple.")
                }
                putJsonObject("all") {
                    put("type", "boolean")
                    put("description", "Set to true to remove ALL breakpoints in the project")
                }
            },
            required = emptyList()
        )
    ) { request ->
        val idParam = request.arguments?.get("id")?.jsonPrimitive?.content
        val ids = idParam?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
        val all = request.arguments?.get("all")?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false

        if (!all && ids.isNullOrEmpty()) {
            return@addTool err("Specify breakpoint(s) to remove or use all=true to remove all breakpoints")
        }

        activityLog.log(if (all) "breakpoint_remove (all)" else "breakpoint_remove ${ids?.joinToString()}")
        try {
            val result = service.removeBreakpoints(if (all) null else ids)

            if (result.removed.isEmpty() && result.notFound.isEmpty()) {
                return@addTool ok("No breakpoints in project found")
            }

            val text = StringBuilder()

            if (result.removed.isNotEmpty()) {
                text.append(formatBreakpointList(result.removed))

                val remaining = service.listBreakpoints()
                if (remaining.isNotEmpty()) {
                    text.append("\n\n${remaining.size} breakpoint(s) remaining in project")
                }
            }

            if (result.notFound.isNotEmpty()) {
                if (text.isNotEmpty()) text.append("\n\n")
                val notFoundStr = result.notFound.joinToString(", ") { "'$it'" }

                val all = service.listBreakpoints()
                if (all.isEmpty() && result.removed.isNotEmpty()) {
                    text.append("Breakpoint $notFoundStr not found")
                } else if (all.isEmpty()) {
                    text.append("Breakpoint $notFoundStr not found, no breakpoints in project")
                } else {
                    // Try narrowing to breakpoints matching the not-found queries
                    val filtered = result.notFound.flatMap { q -> service.listBreakpoints(q) }.distinct()
                    if (filtered.isNotEmpty()) {
                        text.append("Breakpoint $notFoundStr not found, matching breakpoints are:\n\n${formatBreakpointList(filtered)}")
                    } else {
                        text.append("Breakpoint $notFoundStr not found, current breakpoints are:\n\n${formatBreakpointList(all)}")
                    }
                }
            }

            ok(text.toString())
        } catch (e: AmbiguousBreakpointException) {
            ambiguousResponse(idParam ?: "", e.breakpoints)
        } catch (e: Exception) {
            err("Error: ${e.message}")
        }
    }
}
