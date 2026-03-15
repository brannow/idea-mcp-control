# Breakpoints API

## Core Concepts

Breakpoints in the XDebugger framework have two independent lifecycles:

1. **Persistent breakpoints** -- managed by `XBreakpointManager`, survive IDE restarts, exist without a debug session
2. **Runtime breakpoints** -- registered in the actual debugger engine via `XBreakpointHandler` when a debug session is active

For our MCP plugin, we mainly work with persistent breakpoints (CRUD) and observe when they're hit (via session events).

## Key Classes

### XBreakpointManager

**Source:** [`../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/breakpoints/XBreakpointManager.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/breakpoints/XBreakpointManager.java)

Obtained via `XDebuggerManager.getInstance(project).getBreakpointManager()`.

```java
@ApiStatus.NonExtendable
public interface XBreakpointManager {

    // Add a non-line breakpoint (e.g., exception breakpoint)
    @NotNull <T extends XBreakpointProperties> XBreakpoint<T>
        addBreakpoint(XBreakpointType<XBreakpoint<T>, T> type, @Nullable T properties);

    // Add a line breakpoint
    @NotNull <T extends XBreakpointProperties> XLineBreakpoint<T>
        addLineBreakpoint(XLineBreakpointType<T> type,
                          @NotNull String fileUrl,   // VirtualFile URL format
                          int line,                   // 0-based
                          @Nullable T properties);

    // Add a temporary line breakpoint (removed after first hit)
    @NotNull <T extends XBreakpointProperties> XLineBreakpoint<T>
        addLineBreakpoint(XLineBreakpointType<T> type,
                          @NotNull String fileUrl,
                          int line,
                          @Nullable T properties,
                          boolean temporary);

    // Remove
    void removeBreakpoint(@NotNull XBreakpoint<?> breakpoint);

    // Query
    XBreakpoint<?> @NotNull [] getAllBreakpoints();

    @NotNull <B extends XBreakpoint<?>> Collection<? extends B>
        getBreakpoints(@NotNull XBreakpointType<B, ?> type);

    @NotNull <B extends XBreakpoint<?>> Collection<? extends B>
        getBreakpoints(@NotNull Class<? extends XBreakpointType<B, ?>> typeClass);

    @NotNull <B extends XLineBreakpoint<P>, P extends XBreakpointProperties> Collection<B>
        findBreakpointsAtLine(@NotNull XLineBreakpointType<P> type,
                              @NotNull VirtualFile file, int line);

    boolean isDefaultBreakpoint(@NotNull XBreakpoint<?> breakpoint);

    // Listeners
    <B extends XBreakpoint<P>, P extends XBreakpointProperties> void
        addBreakpointListener(@NotNull XBreakpointType<B, P> type,
                              @NotNull XBreakpointListener<B> listener);

    // Presentation update
    void updateBreakpointPresentation(@NotNull XLineBreakpoint<?> breakpoint,
                                      @Nullable Icon icon, @Nullable String errorMessage);
}
```

### XBreakpoint

**Source:** [`../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/breakpoints/XBreakpoint.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/breakpoints/XBreakpoint.java)

The breakpoint instance. `@ApiStatus.NonExtendable` -- the framework creates these, not plugins.

```java
public interface XBreakpoint<P extends XBreakpointProperties> extends UserDataHolder {

    boolean isEnabled();
    void setEnabled(boolean enabled);

    @NotNull XBreakpointType<?, P> getType();
    P getProperties();

    @Nullable XSourcePosition getSourcePosition();

    @NotNull SuspendPolicy getSuspendPolicy();       // ALL, THREAD, NONE
    void setSuspendPolicy(@NotNull SuspendPolicy policy);

    // Logging
    boolean isLogMessage();
    void setLogMessage(boolean logMessage);
    boolean isLogStack();
    void setLogStack(boolean logStack);
    void setLogExpression(@Nullable String expression);
    @Nullable XExpression getLogExpressionObject();

    // Conditions
    void setCondition(@Nullable String condition);
    @Nullable XExpression getConditionExpression();
    void setConditionExpression(@Nullable XExpression condition);

    long getTimeStamp();
}
```

### XLineBreakpoint

**Source:** [`../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/breakpoints/XLineBreakpoint.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/breakpoints/XLineBreakpoint.java)

Extends `XBreakpoint` with line-specific data:

```java
public interface XLineBreakpoint<P extends XBreakpointProperties> extends XBreakpoint<P> {

    int getLine();                    // 0-based line number
    String getFileUrl();              // VirtualFile URL (e.g., "file:///path/to/file.php")
    String getShortFilePath();        // Just the filename
    String getPresentableFilePath();  // Shortened path for display

    @NotNull XLineBreakpointType<P> getType();

    boolean isTemporary();
    void setTemporary(boolean temporary);
}
```

### SuspendPolicy

**Source:** [`../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/breakpoints/SuspendPolicy.kt`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/breakpoints/SuspendPolicy.kt)

```kotlin
enum class SuspendPolicy { ALL, THREAD, NONE }
```

- `ALL` -- suspend all threads (default)
- `THREAD` -- suspend only the hitting thread
- `NONE` -- don't suspend (useful for logging breakpoints)

### XBreakpointType / XLineBreakpointType

**Source:** [`../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/breakpoints/XBreakpointType.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/breakpoints/XBreakpointType.java)

Registered via extension point `com.intellij.xdebugger.breakpointType`. The PHP plugin registers its own line breakpoint type. You can find available types:

```java
// Get all registered line breakpoint types
XLineBreakpointType<?>[] types = XDebuggerUtil.getInstance().getLineBreakpointTypes();

// Find a specific type by class
XBreakpointType myType = XDebuggerUtil.getInstance().findBreakpointType(SomeType.class);
```

Key methods on `XBreakpointType`:
- `getId()` -- unique string ID
- `getTitle()` -- human-readable title
- `getDisplayText(B breakpoint)` -- description for display

### XBreakpointHandler

**Source:** [`../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/breakpoints/XBreakpointHandler.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/breakpoints/XBreakpointHandler.java)

This is the bridge between persistent breakpoints and the runtime debugger. The PHP plugin implements this. We don't need to implement it, but understanding it helps:

```java
public abstract class XBreakpointHandler<B extends XBreakpoint<?>> {
    // Called when debugger session starts or breakpoint is added during session
    public abstract void registerBreakpoint(@NotNull B breakpoint);

    // Called when breakpoint is removed or session ends
    // temporary=true means it may be re-registered (e.g., disabled then re-enabled)
    public abstract void unregisterBreakpoint(@NotNull B breakpoint, boolean temporary);
}
```

### XBreakpointListener

**Source:** [`../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/breakpoints/XBreakpointListener.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/breakpoints/XBreakpointListener.java)

For observing breakpoint CRUD operations (not to be confused with "breakpoint was hit" events):

```java
public interface XBreakpointListener<B extends XBreakpoint<?>> extends EventListener {
    // Also available as project-level Topic:
    // Topic<XBreakpointListener> TOPIC = ...;

    default void breakpointAdded(@NotNull B breakpoint) {}
    default void breakpointRemoved(@NotNull B breakpoint) {}
    default void breakpointChanged(@NotNull B breakpoint) {}
    default void breakpointPresentationUpdated(@NotNull B breakpoint, @Nullable XDebugSession session) {}
}
```

## Common Operations

### List All Breakpoints

```java
XBreakpointManager mgr = XDebuggerManager.getInstance(project).getBreakpointManager();
XBreakpoint<?>[] all = mgr.getAllBreakpoints();
for (XBreakpoint<?> bp : all) {
    XSourcePosition pos = bp.getSourcePosition();
    boolean enabled = bp.isEnabled();
    // ...
}
```

### Add a Line Breakpoint

The simplest approach -- toggle (add if absent, remove if present):

```java
XDebuggerUtil.getInstance().toggleLineBreakpoint(project, virtualFile, line);
// line is 0-based
```

For more control, use the manager directly. You need the breakpoint type first:

```java
XBreakpointManager mgr = XDebuggerManager.getInstance(project).getBreakpointManager();
XLineBreakpointType<?>[] types = XDebuggerUtil.getInstance().getLineBreakpointTypes();
// Find the PHP line breakpoint type from the array

String fileUrl = virtualFile.getUrl();
XLineBreakpoint<?> bp = mgr.addLineBreakpoint(phpLineBreakpointType, fileUrl, line, null);
```

### Remove a Breakpoint

```java
XBreakpointManager mgr = XDebuggerManager.getInstance(project).getBreakpointManager();
mgr.removeBreakpoint(breakpoint);

// Or via utility:
XDebuggerUtil.getInstance().removeBreakpoint(project, breakpoint);
```

### Enable/Disable a Breakpoint

```java
breakpoint.setEnabled(false);  // disable
breakpoint.setEnabled(true);   // enable
```

### Set a Condition

```java
breakpoint.setCondition("$userId > 100");
// Or with XExpression for language-awareness:
breakpoint.setConditionExpression(expression);
```

### Mute All Breakpoints

```java
XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
if (session != null) {
    session.setBreakpointMuted(true);   // mute
    session.setBreakpointMuted(false);  // unmute
    boolean muted = session.areBreakpointsMuted();
}
```

### Check If a Breakpoint Can Be Placed

```java
boolean canPlace = XDebuggerUtil.getInstance().canPutBreakpointAt(project, virtualFile, line);
```

### Find Breakpoints at a Line

```java
XBreakpointManager mgr = XDebuggerManager.getInstance(project).getBreakpointManager();
Collection<...> bps = mgr.findBreakpointsAtLine(phpLineBreakpointType, virtualFile, line);
```

> **Relevant for MCP:** Breakpoint management maps cleanly to MCP tools: `debugger/addBreakpoint`, `debugger/removeBreakpoint`, `debugger/listBreakpoints`, `debugger/toggleBreakpoint`. The `XBreakpointManager` works without an active session, so breakpoints can be set before debugging starts -- which is exactly what an AI agent would want to do as part of a "set up and debug" workflow.
