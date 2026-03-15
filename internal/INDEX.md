# ![logo.svg](logo.svg) PhpStorm MCP Plugin — Internal Documentation Index

## Project Goal

Build a PhpStorm plugin that exposes an MCP (Model Context Protocol) server, allowing AI agents to control the IDE's debugging features: set breakpoints, step through code, inspect variables, evaluate expressions, and read the execution context — as if they were a human developer using the debugger.

## Start Here

| If you need to... | Go to |
|---|---|
| Understand the debugger API (our core) | [03 — Debugger API](docs/03-debugger-api/_INDEX.md) |
| Understand MCP and the SDK we're using | [04 — MCP SDK](docs/04-mcp-sdk/_INDEX.md) |
| Read code around breakpoints (PSI/Editor) | [05 — Editor and PSI](docs/05-editor-and-psi/_INDEX.md) |
| Set up the plugin project | [01 — Getting Started](docs/01-getting-started/_INDEX.md) |
| Understand plugin.xml, services, extensions | [02 — Plugin Structure](docs/02-plugin-structure/_INDEX.md) |
| Invoke IDE actions programmatically | [06 — Actions and UI](docs/06-actions-and-ui/_INDEX.md) |
| Build and publish the plugin | [07 — Publishing](docs/07-publishing/_INDEX.md) |

## Documentation Sections

### [01 — Getting Started](docs/01-getting-started/_INDEX.md)
Prerequisites, project setup, Gradle configuration, dev workflow. (6 docs)

### [02 — Plugin Structure](docs/02-plugin-structure/_INDEX.md)
Plugin anatomy: plugin.xml, services, extensions, listeners, class loading. (11 docs)

### [03 — Debugger API](docs/03-debugger-api/_INDEX.md) ★ Primary Focus
The IntelliJ XDebugger framework. Breakpoints, stepping, stack frames, variables, evaluation, session events. (8 docs)

### [04 — MCP SDK](docs/04-mcp-sdk/_INDEX.md)
Kotlin and Java MCP SDK overviews. Server setup, tool/resource registration, transports. (2 docs)

### [05 — Editor and PSI](docs/05-editor-and-psi/_INDEX.md)
Reading source code programmatically: VirtualFile, Document, PsiFile, PsiElement. (1 doc)

### [06 — Actions and UI](docs/06-actions-and-ui/_INDEX.md)
IntelliJ Action System: registering and invoking IDE actions. (1 doc)

### [07 — Publishing](docs/07-publishing/_INDEX.md)
Building, signing, publishing to JetBrains Marketplace. (3 docs)

## Reference Repositories

| Repository | Path | Purpose |
|---|---|---|
| IntelliJ Community | `reference-Repository/intellij-community/` | Full IDE source. Key: `platform/xdebugger-api/`, `platform/xdebugger-impl/` |
| SDK Code Samples | `reference-Repository/intellij-sdk-code-samples/` | Official plugin examples (actions, editors, PSI, settings, etc.) |
| MCP Kotlin SDK | `reference-Repository/kotlin-sdk/` | Kotlin MCP SDK — our primary SDK choice |
| MCP Java SDK | `reference-Repository/java-sdk/` | Java MCP SDK — kept as reference |

## Dev Tips

### Internal Mode
Enable for powerful debugging tools during plugin development:
```
# Add to idea.properties (Help → Edit Custom Properties)
idea.is.internal=true
```
Gives you: UI Inspector (right-click any UI element to see action IDs and component classes), internal actions menu, extra debugging info.

### Plugin DevKit
Install "Plugin DevKit" in PhpStorm for plugin development support: templates, SDK helpers, inspections.

### Finding Debugger Internals
The best way to understand undocumented behavior:
1. Open `reference-Repository/intellij-community/` in IntelliJ
2. Search for `XDebugSession`, `XBreakpointManager`, etc.
3. Use "Find Usages" to see how the platform itself uses these APIs

### Key Action IDs for Quick Testing
```
Debugger.StepOver
Debugger.StepInto
Debugger.Resume
Debugger.ToggleLineBreakpoint
```
See [debugger-action-ids.md](docs/03-debugger-api/debugger-action-ids.md) for the full list.
