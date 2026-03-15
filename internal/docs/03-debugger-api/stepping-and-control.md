# Stepping and Execution Control

## The Control Surface: XDebugSession

**Source:** [`../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/XDebugSession.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/XDebugSession.java)

All execution control goes through `XDebugSession`. This is the interface the IDE's toolbar buttons use, and it's what our MCP tools call.

## Method Signatures

### Stepping

```java
void stepOver(boolean ignoreBreakpoints);
void stepInto();
void stepOut();
void forceStepInto();
<V extends XSmartStepIntoVariant> void smartStepInto(XSmartStepIntoHandler<V> handler, V variant);
void runToPosition(@NotNull XSourcePosition position, boolean ignoreBreakpoints);
```

- **`stepOver(boolean ignoreBreakpoints)`** -- Execute the current line, stepping over function calls. If `ignoreBreakpoints` is `true`, breakpoints hit during the step are skipped.
- **`stepInto()`** -- Step into the function call on the current line. If there's no call, behaves like step over.
- **`stepOut()`** -- Run until the current function returns.
- **`forceStepInto()`** -- Step into even when the function would normally be skipped (e.g., library code).
- **`smartStepInto(handler, variant)`** -- Step into a specific function call when a line has multiple calls. Requires `XSmartStepIntoHandler` from the debug process.
- **`runToPosition(position, ignoreBreakpoints)`** -- Resume execution until the given position is reached. `XSourcePosition` contains file + 0-based line.

### Suspend/Resume

```java
void pause();
void resume();
void stop();
```

- **`pause()`** -- Interrupt the running process. The debug process must support this (`setPauseActionSupported(true)`).
- **`resume()`** -- Continue execution after being paused.
- **`stop()`** -- Terminate the debug session entirely.

### State Queries

```java
boolean isSuspended();
boolean isStopped();   // inherited from AbstractDebuggerSession
boolean isPaused();    // inherited from AbstractDebuggerSession

@Nullable XStackFrame getCurrentStackFrame();
@Nullable XSuspendContext getSuspendContext();
@Nullable XSourcePosition getCurrentPosition();   // position of current frame
@Nullable XSourcePosition getTopFramePosition();   // position of top frame
```

### Other Control

```java
void showExecutionPoint();                          // Navigate editor to current position
void setCurrentStackFrame(@NotNull XExecutionStack executionStack,
                          @NotNull XStackFrame frame,
                          boolean isTopFrame);       // Switch to a different frame
void setBreakpointMuted(boolean muted);
boolean areBreakpointsMuted();
void setPauseActionSupported(boolean isSupported);
void initBreakpoints();                             // Trigger breakpoint registration
void rebuildViews();                                // Force UI refresh
```

## How Stepping Works Internally

When you call `session.stepOver(false)`, this is what happens:

1. `XDebugSession` validates state (`checkCanPerformCommands()`)
2. Session calls `XDebugProcess.startStepOver(XSuspendContext context)` on the debug process
3. The PHP debug process sends the step-over command to Xdebug via DBGp
4. Xdebug executes until next line, then notifies the process
5. The process creates a new `XSuspendContext` with fresh stack/frame data
6. The process calls `session.positionReached(suspendContext)` to report the new position
7. Session fires `XDebugSessionListener.sessionPaused()` event
8. UI updates to show the new position

The key insight: stepping is **asynchronous**. You call `stepOver()`, it returns immediately, and the pause notification comes later via the listener. For our MCP plugin, this means we need to either:
- Wait for the `sessionPaused()` callback before returning from the MCP tool call
- Or return immediately and let the MCP client poll state

## How Resume/Pause Works Internally

`session.resume()`:
1. Fires `XDebugSessionListener.beforeSessionResume()`
2. Calls `XDebugProcess.resume(XSuspendContext context)`
3. Fires `XDebugSessionListener.sessionResumed()`

`session.pause()`:
1. Calls `XDebugProcess.startPausing()`
2. The process interrupts execution and eventually calls `session.positionReached(suspendContext)`
3. Which fires `sessionPaused()`

## Threading Model

The XDebugger API has specific threading requirements, but they're more relaxed than you might expect:

| Operation | Thread Requirement | Notes |
|-----------|-------------------|-------|
| `session.stepOver()`, `resume()`, etc. | Any thread | Framework dispatches internally |
| `XDebuggerManager.getInstance()` | Any thread | It's a project service lookup |
| `manager.newSessionBuilder()` | EDT (`@RequiresEdt`) | Only for starting new sessions |
| `XValueContainer.computeChildren()` | Called on EDT | Must return quickly, use callbacks |
| `XValue.computePresentation()` | Called on EDT | Must return quickly, use callbacks |
| `XExecutionStack.computeStackFrames()` | Called on EDT | Must return quickly, use callbacks |

For our MCP plugin, the stepping/control methods are the easy case -- they can be called from our MCP handler threads directly. The tricky part is reading values (see [stack-frames-and-variables.md](./stack-frames-and-variables.md)).

## The XDebugProcess Side (For Context)

**Source:** [`../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/XDebugProcess.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/XDebugProcess.java)

We don't call these directly, but understanding the contract helps:

```java
// All stepping methods receive the current suspend context
public void startStepOver(@Nullable XSuspendContext context)  // don't call directly
public void startStepInto(@Nullable XSuspendContext context)  // don't call directly
public void startStepOut(@Nullable XSuspendContext context)   // don't call directly
public void startForceStepInto(@Nullable XSuspendContext context)
public void startPausing()                                     // don't call directly
public void resume(@Nullable XSuspendContext context)          // don't call directly
public void stop()                                             // don't call directly

// The process reports back to the session:
// session.positionReached(suspendContext)  -- when stepping completes
// session.breakpointReached(breakpoint, logExpr, suspendContext)  -- when a breakpoint is hit
// session.sessionResumed()  -- when externally resumed (e.g., debugger console)
```

The `@Nullable XSuspendContext` parameter lets the process know which thread context to step in. This matters for multi-threaded debugging.

## Practical Example: MCP "Step Over" Tool

```java
// In our MCP tool handler:
public ToolResult handleStepOver(Project project) {
    XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
    if (session == null) return error("No active debug session");
    if (!session.isSuspended()) return error("Debug session is not suspended");

    // Set up a latch to wait for the step to complete
    CompletableFuture<XSourcePosition> future = new CompletableFuture<>();
    session.addSessionListener(new XDebugSessionListener() {
        @Override
        public void sessionPaused() {
            future.complete(session.getCurrentPosition());
            session.removeSessionListener(this);
        }
        @Override
        public void sessionResumed() {
            // Step might have been interrupted
        }
        @Override
        public void sessionStopped() {
            future.completeExceptionally(new Exception("Session stopped"));
            session.removeSessionListener(this);
        }
    });

    session.stepOver(false);

    // Wait for pause (with timeout)
    XSourcePosition pos = future.get(10, TimeUnit.SECONDS);
    return success("Stepped to " + pos.getFile().getName() + ":" + (pos.getLine() + 1));
}
```

> **Relevant for MCP:** The stepping/control API is the most straightforward part to wrap as MCP tools. Each method maps to one tool: `debugger/stepOver`, `debugger/stepInto`, `debugger/stepOut`, `debugger/resume`, `debugger/pause`, `debugger/stop`. The async nature means our tools should wait for the `sessionPaused()` callback before returning a result, so the AI agent gets the new position in the response.
