# Session Events

## Design Philosophy

The XDebugger framework is **event-driven, not polling-based**. You don't ask "is the session paused?" in a loop -- you register a listener and get notified when state changes. This is critical for our MCP plugin: we can translate these events into MCP notifications or use them to complete pending tool calls.

There are two listener interfaces at different levels:

1. **`XDebugSessionListener`** -- per-session events (paused, resumed, stopped)
2. **`XDebuggerManagerListener`** -- manager-level events (session started/stopped, current session changed)
3. **`XBreakpointListener`** -- breakpoint CRUD events (added, removed, changed)

## XDebugSessionListener

**Source:** [`../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/XDebugSessionListener.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/XDebugSessionListener.java)

```java
public interface XDebugSessionListener extends EventListener {

    // Fired when the session suspends (breakpoint hit, step completed, pause)
    default void sessionPaused() {}

    // Fired when the session resumes execution
    default void sessionResumed() {}

    // Fired when the debug session terminates
    default void sessionStopped() {}

    // Fired when the user (or code) selects a different stack frame
    default void stackFrameChanged() {}

    // Same as above, but tells you whether the user did it manually
    @ApiStatus.Experimental
    default void stackFrameChanged(boolean changedByUser) {
        stackFrameChanged();
    }

    // Fired just before resume (useful for cleanup before state changes)
    default void beforeSessionResume() {}

    // Fired when debugger settings change
    default void settingsChanged() {}

    // Fired when breakpoints are muted/unmuted
    default void breakpointsMuted(boolean muted) {}
}
```

### Registration

```java
XDebugSession session = ...;

// Option 1: With explicit removal
session.addSessionListener(listener);
session.removeSessionListener(listener);

// Option 2: Auto-removed when parentDisposable is disposed
session.addSessionListener(listener, parentDisposable);
```

### Event Sequence Examples

**Breakpoint hit:**
```
beforeSessionResume() -- (if was previously paused and auto-resumed)
sessionResumed()
... execution runs ...
sessionPaused()        -- breakpoint reached
```

**Step over:**
```
beforeSessionResume()
sessionResumed()       -- step begins
... one line executes ...
sessionPaused()        -- step complete, new position reached
```

**Resume then hit breakpoint:**
```
beforeSessionResume()
sessionResumed()
... execution runs ...
sessionPaused()        -- hit a breakpoint
```

**Session ends:**
```
sessionStopped()
```

**Mute breakpoints:**
```
breakpointsMuted(true)
```

## XDebuggerManagerListener

**Source:** [`../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/XDebuggerManagerListener.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/XDebuggerManagerListener.java)

Manager-level events, delivered via the message bus:

```java
public interface XDebuggerManagerListener {

    // A new debug process has started
    default void processStarted(@NotNull XDebugProcess debugProcess) {}

    // A debug process has stopped
    default void processStopped(@NotNull XDebugProcess debugProcess) {}

    // The "current" session changed (user switched tabs, session started/ended)
    default void currentSessionChanged(@Nullable XDebugSession previousSession,
                                       @Nullable XDebugSession currentSession) {}
}
```

### Registration

Via the project-level message bus:

```java
project.getMessageBus().connect(disposable)
    .subscribe(XDebuggerManager.TOPIC, new XDebuggerManagerListener() {
        @Override
        public void processStarted(@NotNull XDebugProcess debugProcess) {
            // A debug session just started -- register our session listener
            XDebugSession session = debugProcess.getSession();
            session.addSessionListener(mySessionListener);
        }

        @Override
        public void processStopped(@NotNull XDebugProcess debugProcess) {
            // Clean up
        }

        @Override
        public void currentSessionChanged(@Nullable XDebugSession prev,
                                          @Nullable XDebugSession current) {
            // Active session in UI changed
        }
    });
```

## XBreakpointListener

**Source:** [`../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/breakpoints/XBreakpointListener.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/breakpoints/XBreakpointListener.java)

For observing breakpoint changes (CRUD operations, not "breakpoint hit"):

```java
public interface XBreakpointListener<B extends XBreakpoint<?>> extends EventListener {

    default void breakpointAdded(@NotNull B breakpoint) {}
    default void breakpointRemoved(@NotNull B breakpoint) {}
    default void breakpointChanged(@NotNull B breakpoint) {}
    default void breakpointPresentationUpdated(@NotNull B breakpoint,
                                                @Nullable XDebugSession session) {}

    // Experimental -- currently JVM-only
    default void breakpointError(@NotNull B breakpoint, @NotNull XDebugSession session,
                                 @NotNull BreakpointErrorData error) {}
    default void breakpointLogMessage(@NotNull B breakpoint, @NotNull XDebugSession session,
                                      @NotNull String message) {}
}
```

### Registration

Two options:

```java
// Option 1: Type-specific listener via XBreakpointManager
XBreakpointManager mgr = XDebuggerManager.getInstance(project).getBreakpointManager();
mgr.addBreakpointListener(breakpointType, listener);
mgr.addBreakpointListener(breakpointType, listener, disposable);

// Option 2: Global listener via message bus (receives all breakpoint types)
project.getMessageBus().connect(disposable)
    .subscribe(XBreakpointListener.TOPIC, new XBreakpointListener<>() {
        @Override
        public void breakpointAdded(@NotNull XBreakpoint<?> breakpoint) { ... }
        // ...
    });
```

## Practical Pattern: MCP Event Bridge

Our MCP plugin should register listeners when the plugin activates and forward relevant events:

```java
public class DebuggerEventBridge implements Disposable {
    private final Project project;

    public DebuggerEventBridge(Project project) {
        this.project = project;

        // Listen for new debug sessions
        project.getMessageBus().connect(this)
            .subscribe(XDebuggerManager.TOPIC, new XDebuggerManagerListener() {
                @Override
                public void processStarted(@NotNull XDebugProcess process) {
                    attachToSession(process.getSession());
                }
            });

        // Also attach to any already-running sessions
        for (XDebugSession session : XDebuggerManager.getInstance(project).getDebugSessions()) {
            attachToSession(session);
        }
    }

    private void attachToSession(XDebugSession session) {
        session.addSessionListener(new XDebugSessionListener() {
            @Override
            public void sessionPaused() {
                XSourcePosition pos = session.getCurrentPosition();
                // -> Emit MCP notification: "debugger/paused"
                //    with position, reason (breakpoint/step/pause)
            }

            @Override
            public void sessionResumed() {
                // -> Emit MCP notification: "debugger/resumed"
            }

            @Override
            public void sessionStopped() {
                // -> Emit MCP notification: "debugger/stopped"
            }

            @Override
            public void stackFrameChanged() {
                // -> Emit MCP notification: "debugger/frameChanged"
            }
        }, this);
    }

    @Override
    public void dispose() {}
}
```

> **Relevant for MCP:** Events are essential for a responsive debugging experience. Without them, an AI agent would have to poll `session.isSuspended()` in a loop, which is wasteful and slow. Map these events to MCP notifications (if the MCP protocol supports them) or use them internally to complete pending tool calls. The `sessionPaused` event is the most important -- it's fired after every breakpoint hit and step completion, and it's the signal that debug state is ready to inspect.
