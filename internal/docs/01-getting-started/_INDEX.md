# 01 — Getting Started

Plugin development basics: prerequisites, project setup, Gradle configuration, and dev workflow.

## Docs

- [required-experience.md](required-experience.md) — JVM/Kotlin/Java knowledge requirements for IntelliJ plugin development
- [creating-a-plugin-gradle-project.md](creating-a-plugin-gradle-project.md) — Setting up a new plugin project via New Project Wizard or template
- [configuring-gradle-plugin.md](configuring-gradle-plugin.md) — IntelliJ Platform Gradle Plugin (2.x) configuration: target platform, dependencies, tasks
- [developing-plugin.md](developing-plugin.md) — Development workflow: coding, running, debugging your plugin
- [ide-dev-instance.md](ide-dev-instance.md) — How the IDE Development Instance works (sandbox for testing your plugin)
- [kotlin-support.md](kotlin-support.md) — Configuring Kotlin for plugin development (recommended by JetBrains)

## Key Takeaways for Our Plugin

- Use the IntelliJ Platform Gradle Plugin 2.x (not the legacy 1.x)
- Target PhpStorm specifically via `phpstorm()` in the dependencies block
- Kotlin is the recommended language for new plugins — aligns with both JetBrains direction and the Kotlin MCP SDK
