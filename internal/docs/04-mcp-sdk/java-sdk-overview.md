# Java MCP SDK -- Server-Side Overview

> Source: `../../reference-Repository/java-sdk/`

## When to Use This vs. Kotlin SDK

The Java SDK is the original MCP SDK for JVM. The Kotlin SDK is newer and more idiomatic for Kotlin/coroutine-based codebases. Both implement the same MCP protocol spec. Since IntelliJ plugins are increasingly Kotlin-first and the Kotlin SDK has native coroutine support, **the Kotlin SDK is likely the better fit for our plugin**. This doc exists as a reference in case we need to fall back or interop.

## Architecture

The SDK uses a layered design:

```
McpServer (API layer -- sync or async)
    |
McpSession (protocol state, message correlation)
    |
McpTransport (JSON-RPC serialization, wire format)
```

### Module Structure

| Module | Artifact | Purpose |
|--------|----------|---------|
| `mcp-core` | `io.modelcontextprotocol.sdk:mcp-core` | Core impl: STDIO, JDK HttpClient, Servlet, Streamable HTTP transports |
| `mcp-json-jackson2` | same group | Jackson 2.x serialization |
| `mcp-json-jackson3` | same group | Jackson 3.x serialization |
| `mcp` | same group | Convenience bundle: `mcp-core` + `mcp-json-jackson3` |
| `mcp-test` | same group | Test utilities |

Spring integrations (`mcp-spring-webflux`, `mcp-spring-webmvc`) moved to Spring AI 2.0+ and are not part of this SDK anymore. Not relevant for our IDE plugin.

## Server Setup

The Java SDK offers both sync and async APIs. The async API returns `Mono<T>` (Project Reactor).

### Sync Server

```java
McpSyncServer server = McpServer.sync(transportProvider)
    .serverInfo("phpstorm-mcp", "0.1.0")
    .capabilities(ServerCapabilities.builder()
        .tools(true)             // tool support + listChanged notifications
        .resources(false, true)  // subscribe=false, listChanged=true
        .prompts(true)
        .logging()
        .build())
    .build();
```

### Async Server

```java
McpAsyncServer server = McpServer.async(transportProvider)
    .serverInfo("phpstorm-mcp", "0.1.0")
    .capabilities(ServerCapabilities.builder()
        .tools(true)
        .resources(false, true)
        .prompts(true)
        .logging()
        .build())
    .build();
```

## Transport Providers

### STDIO

```java
StdioServerTransportProvider transportProvider =
    new StdioServerTransportProvider(new ObjectMapper());
```

Bidirectional JSON-RPC over stdin/stdout. Same use case as the Kotlin SDK's `StdioServerTransport`.

### Streamable HTTP (Servlet-based)

```java
HttpServletStreamableServerTransportProvider transportProvider =
    HttpServletStreamableServerTransportProvider.builder()
        .jsonMapper(jsonMapper)
        .mcpEndpoint("/mcp")
        .build();
```

This is a raw Servlet -- you register it with any Servlet container. Features: session management, configurable keep-alive, security validation hooks.

### SSE HTTP (Legacy)

`HttpServletSseServerTransportProvider` -- backward compatible with older MCP clients. Prefer Streamable HTTP.

## Tool Registration

```java
var toolSpec = SyncToolSpecification.builder()
    .tool(Tool.builder()
        .name("set_breakpoint")
        .description("Set a breakpoint in a PHP file")
        .inputSchema(schema)  // JSON Schema as Map
        .build())
    .callHandler((exchange, request) -> {
        String file = (String) request.arguments().get("file");
        int line = (int) request.arguments().get("line");
        // ... debugger interaction ...
        return CallToolResult.builder()
            .content(List.of(new McpSchema.TextContent("Breakpoint set")))
            .build();
    })
    .build();

server.addTool(toolSpec);
```

The handler receives two args:
1. `McpSyncServerExchange` / `McpAsyncServerExchange` -- access to client capabilities, sampling, elicitation, logging
2. `CallToolRequest` -- contains `arguments()` map

Shorthand via builder:

```java
var server = McpServer.sync(transportProvider)
    .toolCall(tool, (exchange, request) -> result)
    .build();
```

## Resource Registration

```java
var resourceSpec = new McpServerFeatures.SyncResourceSpecification(
    Resource.builder()
        .uri("debugger://stacktrace")
        .name("Stack Trace")
        .description("Current debug stack frames")
        .mimeType("application/json")
        .build(),
    (exchange, request) -> new ReadResourceResult(contents)
);

server.addResource(resourceSpec);
```

Resource templates for parameterized URIs:

```java
ResourceTemplate.builder()
    .uriTemplate("file://{path}")
    .name("File Resource")
    .build();
```

## Prompt Registration

```java
var promptSpec = new McpServerFeatures.SyncPromptSpecification(
    new Prompt("debug-analysis", "Analyze debug state", List.of(
        new PromptArgument("context", "Additional context", false)
    )),
    (exchange, request) -> new GetPromptResult(description, messages)
);

server.addPrompt(promptSpec);
```

## Logging

Log messages are sent via the exchange object within tool/resource/prompt handlers:

```java
exchange.loggingNotification(
    McpSchema.LoggingMessageNotification.builder()
        .level(McpSchema.LoggingLevel.INFO)
        .logger("debugger")
        .data("Breakpoint hit")
        .build()
);
```

Clients control minimum level via `logging/setLevel`.

## Sampling (Server-Initiated LLM Calls)

If the connected client supports sampling, the server can request LLM completions:

```java
if (exchange.getClientCapabilities().sampling() != null) {
    CreateMessageRequest req = CreateMessageRequest.builder()
        .messages(List.of(new McpSchema.SamplingMessage(
            McpSchema.Role.USER,
            new McpSchema.TextContent("Analyze this stack trace..."))))
        .maxTokens(500)
        .build();

    CreateMessageResult result = exchange.createMessage(req);
    String answer = ((McpSchema.TextContent) result.content()).text();
}
```

## Key Differences from Kotlin SDK

| Aspect | Kotlin SDK | Java SDK |
|--------|-----------|----------|
| Async model | Kotlin coroutines (`suspend`) | Project Reactor (`Mono<T>`) |
| Serialization | kotlinx.serialization (`McpJson`) | Jackson (2.x or 3.x) |
| Transport setup | Ktor extensions | Raw Servlet / transport provider |
| Tool registration | `server.addTool(name, desc, schema) { }` | Builder pattern + `SyncToolSpecification` |
| Multiplatform | JVM, Native, JS, Wasm | JVM only |

For our IntelliJ plugin (Kotlin codebase, coroutine-friendly), the Kotlin SDK is the more natural choice.
