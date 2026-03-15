# 06 — Actions and UI

The IntelliJ Action System. Lower priority for now — relevant when we add UI elements or invoke IDE actions programmatically.

## Docs

- [action-system.md](action-system.md) — AnAction, ActionManager, action registration, update/actionPerformed lifecycle

## Key Takeaways for Our Plugin

- `ActionManager.getInstance().getAction("Debugger.StepOver")` can invoke any registered action
- See [03-debugger-api/debugger-action-ids.md](../03-debugger-api/debugger-action-ids.md) for the full list of debugger action IDs
- Actions run on EDT — use `ApplicationManager.getApplication().invokeLater()` when calling from MCP handler threads
