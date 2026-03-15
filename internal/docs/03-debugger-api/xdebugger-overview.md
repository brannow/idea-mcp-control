# XDebugger Framework: Architecture Overview

## Why This Matters for MCP

The XDebugger framework is the abstraction layer IntelliJ uses to support debugging across all languages. Our MCP plugin doesn't implement a debugger -- PHP/Xdebug already does that. Instead, we interact with **the same API the IDE's UI uses** to control an active debug session. Think of it as: the MCP plugin becomes another "client" of the debug session, alongside the human using the Debug tool window.

## The Three Layers

```
XDebuggerManager  (project-level service -- entry point)
    |
    +-- XDebugSession  (one per active debug run -- the control surface)
    |       |
    |       +-- XDebugProcess  (language-specific -- PHP plugin provides this)
    |               |
    |               +-- XBreakpointHandler[]  (registers breakpoints in Xdebug)
    |               +-- XDebuggerEditorsProvider  (language-aware expression editing)
    |               +-- XSuspendContext  (thread/stack state when paused)
    +-- XBreakpointManager  (breakpoint CRUD, independent of sessions)
```

### Layer 1: XDebuggerManager

**Source:** [`../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/XDebuggerManager.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/XDebuggerManager.java)

This is the top-level entry point. It's a project-level service (`@ApiStatus.NonExtendable`), obtained via:

```java
XDebuggerManager manager = XDebuggerManager.getInstance(project);
```

Key methods:

| Method | Returns | Purpose |
|--------|---------|---------|
| `getDebugSessions()` | `XDebugSession[]` | All active debug sessions |
| `getCurrentSession()` | `XDebugSession?` | The session currently selected in the UI |
| `getBreakpointManager()` | `XBreakpointManager` | Breakpoint CRUD (works without active session) |
| `getDebugProcesses(Class<T>)` | `List<T>` | Find debug processes by type |
| `getDebugSession(ExecutionConsole)` | `XDebugSession?` | Find session by its console |
| `newSessionBuilder(XDebugProcessStarter)` | `XDebugSessionBuilder` | Start a new debug session (requires EDT) |

The manager also exposes a message bus topic for lifecycle events:

```java
public static final Topic<XDebuggerManagerListener> TOPIC = ...;
```

### Layer 2: XDebugSession

**Source:** [`../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/XDebugSession.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/XDebugSession.java)

This is the **control surface** -- the interface our MCP plugin uses most. It extends `AbstractDebuggerSession` which provides `isStopped()` and `isPaused()`.

The session is created by the framework (not by plugins) when debugging starts. You get it from:
- `XDebuggerManager.getCurrentSession()`
- `XDebuggerManager.getDebugSessions()`
- `XDebugProcess.getSession()`

Key capabilities:
- **Execution control:** `stepOver()`, `stepInto()`, `stepOut()`, `resume()`, `pause()`, `stop()`
- **State inspection:** `isSuspended()`, `getCurrentStackFrame()`, `getSuspendContext()`, `getCurrentPosition()`
- **Breakpoint feedback:** `breakpointReached()`, `setBreakpointVerified()`, `setBreakpointInvalid()`
- **Listeners:** `addSessionListener(XDebugSessionListener)`
- **Breakpoint muting:** `setBreakpointMuted(boolean)`, `areBreakpointsMuted()`

### Layer 3: XDebugProcess

**Source:** [`../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/XDebugProcess.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/XDebugProcess.java)

This is where language plugins (like the PHP plugin with Xdebug) implement their debugger. For our MCP plugin, we don't extend this -- **we consume the session it's attached to**.

The process provides:
- `getBreakpointHandlers()` -- how breakpoints are registered in the actual debugger engine
- `getEditorsProvider()` -- language-aware expression editing
- `getEvaluator()` -- expression evaluation in current context
- `getSmartStepIntoHandler()` -- smart step into support
- Stepping implementations: `startStepOver(XSuspendContext)`, `startStepInto(XSuspendContext)`, etc.

Important contract: The `XDebugProcess` methods like `startStepOver()` are called **by the session** in response to session-level calls. You should never call them directly.

## How PHP/Xdebug Hooks In

The PHP plugin:
1. Implements `XDebugProcess` subclass that talks to Xdebug over DBGp protocol
2. Implements `XBreakpointHandler` to register/unregister breakpoints in Xdebug
3. Provides `XDebuggerEditorsProvider` for PHP expression editing
4. Provides `XDebuggerEvaluator` to evaluate PHP expressions in debug context
5. Creates `XSuspendContext` / `XExecutionStack` / `XStackFrame` objects when Xdebug hits a breakpoint

## Entry Points for Our MCP Plugin

Our plugin doesn't need to start debug sessions (the user does that via Run/Debug). Instead, we:

1. **Discover sessions:** `XDebuggerManager.getInstance(project).getDebugSessions()`
2. **Control execution:** Call `session.stepOver()`, `session.resume()`, etc.
3. **Manage breakpoints:** Use `XDebuggerManager.getInstance(project).getBreakpointManager()` or `XDebuggerUtil.getInstance().toggleLineBreakpoint()`
4. **Read state:** Traverse `session.getSuspendContext()` -> stacks -> frames -> variables
5. **Evaluate expressions:** Get evaluator from `session.getDebugProcess().getEvaluator()` or from `session.getCurrentStackFrame().getEvaluator()`
6. **Listen for events:** `session.addSessionListener(...)` and `XDebuggerManager.TOPIC`

> **Relevant for MCP:** Every debugger control action we expose as an MCP tool maps directly to one of these entry points. The XDebugSession interface IS our tool API -- we're just wrapping it in MCP's JSON-RPC protocol.

## Threading Model (Quick Summary)

- `XDebuggerManager.getInstance()` -- safe from any thread
- `session.stepOver()`, `session.resume()`, etc. -- can be called from any thread (the framework handles dispatch)
- `XValueContainer.computeChildren()`, `XValue.computePresentation()` -- called on EDT, must return quickly (async pattern)
- `XDebuggerManager.newSessionBuilder()` -- `@RequiresEdt`

See [stepping-and-control.md](./stepping-and-control.md) for the full threading story.

## Supporting Types

| Class | Role | Source |
|-------|------|--------|
| `AbstractDebuggerSession` | Base interface: `isStopped()`, `isPaused()` | [`../../reference-Repository/.../AbstractDebuggerSession.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/AbstractDebuggerSession.java) |
| `XDebugProcessStarter` | Factory for `XDebugProcess`, passed to session builder | [`../../reference-Repository/.../XDebugProcessStarter.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/XDebugProcessStarter.java) |
| `XSourcePosition` | File + line (0-based) + offset | [`../../reference-Repository/.../XSourcePosition.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/XSourcePosition.java) |
| `XExpression` | Expression text + language + evaluation mode | [`../../reference-Repository/.../XExpression.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/XExpression.java) |
| `XDebuggerUtil` | Utility: toggle breakpoints, create positions, find breakpoint types | [`../../reference-Repository/.../XDebuggerUtil.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/XDebuggerUtil.java) |
