# 02 — Plugin Structure

How IntelliJ plugins are structured: configuration, services, extensions, listeners, and class loading.

## Docs

- [plugin-types.md](plugin-types.md) — Types of plugins (UI themes, custom language, framework integration, etc.)
- [plugin-config-file.md](plugin-config-file.md) — plugin.xml configuration file structure and all elements
- [plugin-content.md](plugin-content.md) — Plugin JAR structure and content layout
- [plugin-dependencies.md](plugin-dependencies.md) — Declaring dependencies on other plugins and platform modules
- [plugin-services.md](plugin-services.md) — Application/project/module-level services (singletons managed by the platform)
- [plugin-extensions.md](plugin-extensions.md) — Registering extensions to existing extension points
- [plugin-extension-points.md](plugin-extension-points.md) — Defining your own extension points for other plugins
- [plugin-listeners.md](plugin-listener.md) — Message bus listeners for platform events
- [plugin-class-loaders.md](plugin-class-loaders.md) — How plugin class loading works
- [plugin-openapi-sources.md](plugin-openapi-sources.md) — Bundling API sources for dependent plugins
- [plugin-logo.md](plugin-logo.md) — Plugin icon requirements

## Key Takeaways for Our Plugin

- Our plugin needs a **project-level service** to manage the MCP server lifecycle
- We'll register **listeners** on the message bus for debugger events (XDebuggerManager topic)
- plugin.xml must declare dependency on `com.intellij.modules.xdebugger` and the PHP plugin
