# Kotlin MCP SDK -- Server-Side Overview

> Source: `../../reference-Repository/kotlin-sdk/`

## Architecture

The SDK is split into three modules:

| Module | Artifact | Purpose |
|--------|----------|---------|
| **core** | `kotlin-sdk-core` | Protocol types, JSON-RPC framing, transport abstractions, `McpJson` serialization config |
| **client** | `kotlin-sdk-client` | Client runtime + transports (not needed for our plugin) |
| **server** | `kotlin-sdk-server` | Server runtime, session management, tool/resource/prompt registries, Ktor integration |

For the plugin we only need `kotlin-sdk-server` (and its transitive dependency on `core`).

The SDK targets Kotlin Multiplatform (JVM, Native, JS, Wasm). We only care about JVM 11+.

## Gradle Setup

```kotlin
dependencies {
    implementation("io.modelcontextprotocol:kotlin-sdk-server:$mcpVersion")
    // Ktor engine -- pick one; CIO is lightweight and has no native deps
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
}
```

The SDK uses Ktor but does **not** bundle an engine transitively -- you must declare one yourself.

## Server Setup

Create a `Server` instance with declared capabilities, register features, then bind to a transport:

```kotlin
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.*

val server = Server(
    serverInfo = Implementation(name = "phpstorm-mcp", version = "0.1.0"),
    options = ServerOptions(
        capabilities = ServerCapabilities(
            tools = ServerCapabilities.Tools(listChanged = true),
            resources = ServerCapabilities.Resources(subscribe = true, listChanged = true),
            prompts = ServerCapabilities.Prompts(listChanged = true),
            logging = ServerCapabilities.Logging,
        ),
    )
)
```

`listChanged = true` means the server will emit `notifications/*/list_changed` when the catalog changes at runtime. Only enable what you actually need.

## Transports (Relevant to IDE Plugin)

### Stdio Transport

Tunnels JSON-RPC over stdin/stdout. Ideal for editor/IDE plugins that spawn MCP as a child process.

```kotlin
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered

val transport = StdioServerTransport(
    inputStream = System.`in`.asSource().buffered(),
    outputStream = System.out.asSink().buffered()
)
val session = server.createSession(transport)
```

This is likely the simplest option for a first iteration: the AI client (Claude, etc.) launches PhpStorm's MCP process, and communication happens over pipes.

### Streamable HTTP Transport

Single HTTP endpoint with optional SSE streaming. Better for remote or multi-client scenarios.

```kotlin
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp

embeddedServer(CIO, host = "127.0.0.1", port = 3000) {
    mcpStreamableHttp(path = "/mcp") {
        server // factory returns Server per session
    }
}.start(wait = true)
```

The Ktor helper `mcpStreamableHttp` handles session lifecycle, content negotiation, and SSE upgrade. Default path is `/mcp`.

A **stateless** variant (`mcpStatelessStreamableHttp`) exists for servers that don't maintain session state.

### SSE Transport (Legacy)

Still available via `mcp { }` Ktor extension for backward compatibility. Prefer Streamable HTTP for new work.

### Decision for Our Plugin

For an IDE-embedded server, **stdio** is the natural fit if the AI client spawns the process. If the plugin starts an HTTP listener inside the IDE process, **Streamable HTTP on localhost** works too. Both are valid; stdio is simpler, HTTP is more flexible for multiple clients.

## Tool Registration

Tools are the primary way the AI agent interacts with the debugger.

```kotlin
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonPrimitive

server.addTool(
    name = "set_breakpoint",
    description = "Set a breakpoint in a PHP file",
    inputSchema = ToolSchema(
        properties = buildJsonObject {
            put("file", buildJsonObject { put("type", "string") })
            put("line", buildJsonObject { put("type", "integer") })
        },
        required = listOf("file", "line")
    )
) { request ->
    val file = request.arguments?.get("file")?.jsonPrimitive?.content ?: ""
    val line = request.arguments?.get("line")?.jsonPrimitive?.content?.toInt() ?: 0
    // ... interact with XDebugSession API ...
    CallToolResult(content = listOf(TextContent("Breakpoint set at $file:$line")))
}
```

Key points:
- `inputSchema` uses JSON Schema. The `ToolSchema` type wraps `properties` (JsonObject) and optional `required` list.
- The handler is a `suspend` lambda -- safe to call coroutine-based IDE APIs.
- Return `CallToolResult` with a list of `TextContent` (or `ImageContent`, `EmbeddedResource`).
- For errors, return `CallToolResult(content = listOf(TextContent("error message")), isError = true)`.

Tools can be added/removed at runtime. After mutation, call the appropriate notification if `listChanged` is enabled.

## Resource Registration

Resources expose read-only data to the AI (e.g., current stack frames, variable values).

```kotlin
server.addResource(
    uri = "debugger://stacktrace",
    name = "Current Stack Trace",
    description = "Stack frames of the paused debugger session",
    mimeType = "application/json",
) { request ->
    ReadResourceResult(
        contents = listOf(
            TextResourceContents(
                text = getStackTraceAsJson(),
                uri = request.uri,
                mimeType = "application/json",
            )
        )
    )
}
```

Resources use stable URIs. For parameterized access, use resource templates with URI templates like `file://{path}`.

## Prompt Registration

Prompts are reusable templates the AI client can discover and invoke.

```kotlin
server.addPrompt(
    name = "debug-analysis",
    description = "Analyze the current debug state",
    arguments = listOf(
        PromptArgument(name = "context", description = "Additional context", required = false),
    ),
) { request ->
    GetPromptResult(
        description = "Debug state analysis prompt",
        messages = listOf(
            PromptMessage(
                role = Role.User,
                content = TextContent(text = "Analyze the current debug state..."),
            ),
        ),
    )
}
```

## Logging

The server can emit structured log messages to the client:

```kotlin
session.sendLoggingMessage(
    LoggingMessageNotification(
        LoggingMessageNotificationParams(
            level = LoggingLevel.Info,
            logger = "debugger",
            data = buildJsonObject { put("message", "Breakpoint hit at MyClass.php:42") },
        )
    )
)
```

Levels follow RFC 5424: `Debug`, `Info`, `Notice`, `Warning`, `Error`, `Critical`, `Alert`, `Emergency`.

## Pagination

List operations (`tools/list`, `resources/list`, etc.) support cursor-based pagination via `nextCursor`. The cursor is opaque -- don't parse or persist it. Return `nextCursor = null` to signal end of list.

## Key Types Reference

| Type | Package | Purpose |
|------|---------|---------|
| `Server` | `sdk.server` | Main server runtime |
| `ServerOptions` | `sdk.server` | Wraps capabilities for server construction |
| `ServerCapabilities` | `sdk.types` | Declares what the server supports |
| `Implementation` | `sdk.types` | Server name + version |
| `CallToolResult` | `sdk.types` | Tool execution result |
| `TextContent` | `sdk.types` | Text content in results |
| `ToolSchema` | `sdk.types` | JSON Schema for tool input |
| `ReadResourceResult` | `sdk.types` | Resource read result |
| `GetPromptResult` | `sdk.types` | Prompt fetch result |
| `StdioServerTransport` | `sdk.server` | Stdio transport |
| `McpJson` | `sdk.types` | Pre-configured kotlinx.serialization JSON |
