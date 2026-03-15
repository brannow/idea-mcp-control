# Debugger Action IDs

## Why Action IDs Matter

IntelliJ actions can be invoked programmatically via `ActionManager`. For some operations, invoking an action is simpler than calling the underlying API directly -- the action handles all the context resolution, EDT dispatching, and error handling.

```java
ActionManager actionManager = ActionManager.getInstance();
AnAction action = actionManager.getAction("StepOver");
// Then invoke it with a DataContext
```

However, for our MCP plugin, **prefer the direct API** (`session.stepOver()`, etc.) over action invocation. Actions require a `DataContext` with UI state, which is tricky to construct from a background MCP handler. Use action IDs only when there's no clean API alternative.

## Core Stepping/Control Actions

Registered in [`../../reference-Repository/intellij-community/platform/platform-resources/src/idea/ExecutionActions.xml`](../../reference-Repository/intellij-community/platform/platform-resources/src/idea/ExecutionActions.xml)

| Action ID | Class | Description | API Alternative |
|-----------|-------|-------------|-----------------|
| `Resume` | `ResumeAction` | Resume execution | `session.resume()` |
| `Pause` | `PauseAction` | Pause execution | `session.pause()` |
| `StepOver` | `StepOverAction` | Step over current line | `session.stepOver(false)` |
| `ForceStepOver` | `ForceStepOverAction` | Step over (ignoring breakpoints) | `session.stepOver(true)` |
| `StepInto` | `StepIntoAction` | Step into function | `session.stepInto()` |
| `ForceStepInto` | `ForceStepIntoAction` | Force step into (including filtered) | `session.forceStepInto()` |
| `SmartStepInto` | `SmartStepIntoAction` | Choose which call to step into | `session.smartStepInto(handler, variant)` |
| `StepOut` | `StepOutAction` | Step out of current function | `session.stepOut()` |
| `RunToCursor` | `RunToCursorAction` | Run to cursor position | `session.runToPosition(pos, false)` |
| `ForceRunToCursor` | `ForceRunToCursorAction` | Run to cursor (ignoring breakpoints) | `session.runToPosition(pos, true)` |
| `EvaluateExpression` | `EvaluateAction` | Open Evaluate Expression dialog | `evaluator.evaluate(...)` |
| `ShowExecutionPoint` | `ShowExecutionPointAction` | Navigate to current execution line | `session.showExecutionPoint()` |

## Breakpoint Actions

| Action ID | Class | Description | API Alternative |
|-----------|-------|-------------|-----------------|
| `ToggleLineBreakpoint` | `ToggleLineBreakpointAction` | Toggle breakpoint at cursor | `XDebuggerUtil.toggleLineBreakpoint()` |
| `ToggleBreakpointEnabled` | `ToggleBreakpointEnabledAction` | Enable/disable breakpoint | `breakpoint.setEnabled(!bp.isEnabled())` |
| `ViewBreakpoints` | `ViewBreakpointsAction` | Open Breakpoints dialog | No direct API (UI action) |
| `XDebugger.MuteBreakpoints` | `MuteBreakpointAction` | Toggle breakpoint muting | `session.setBreakpointMuted(...)` |
| `Debugger.RemoveAllBreakpoints` | `RemoveAllBreakpointsAction` | Remove all breakpoints | Iterate `mgr.getAllBreakpoints()` + `removeBreakpoint()` |
| `Debugger.RemoveAllBreakpointsInFile` | `RemoveAllBreakpointsInFileAction` | Remove all BPs in current file | Filter + remove |
| `XDebugger.RemoveAllButThisBreakpoint` | `RemoveAllButThisBreakpointAction` | Remove all except selected | Filter + remove |
| `XDebugger.DisableAllButThisBreakpoint` | `DisableAllButThisBreakpointAction` | Disable all except selected | Filter + `setEnabled(false)` |

## Watch/Variable Actions

| Action ID | Class | Description |
|-----------|-------|-------------|
| `Debugger.AddToWatch` | `AddToWatchesAction` | Add variable to watches |
| `Debugger.AddInlineWatch` | `AddInlineWatchAction` | Add inline watch |
| `Debugger.EvaluateInConsole` | `EvaluateInConsoleAction` | Evaluate in debug console |
| `XDebugger.SetValue` | `XSetValueAction` | Set variable value |
| `XDebugger.CopyValue` | `XCopyValueAction$Simple` | Copy variable value |
| `XDebugger.CopyName` | `XCopyNameAction` | Copy variable name |
| `XDebugger.Inspect` | `XInspectAction` | Open value inspector |
| `XDebugger.NewWatch` | `XNewWatchAction` | Add a new watch expression |
| `XDebugger.EditWatch` | `XEditWatchAction` | Edit a watch expression |
| `XDebugger.RemoveWatch` | `XRemoveWatchAction` | Remove a watch |
| `XDebugger.RemoveAllWatches` | `XRemoveAllWatchesAction` | Remove all watches |

## Frame/Thread Actions

| Action ID | Class | Description |
|-----------|-------|-------------|
| `Debugger.ShowLibraryFrames` | `ShowLibraryFramesAction` | Toggle library frames visibility |
| `Debugger.CopyStack` | `XDebuggerFramesList$CopyStackAction` | Copy stack trace |
| `Debugger.FocusOnBreakpoint` | `FocusOnBreakpointAction` | Focus editor on breakpoint |
| `Debugger.ShowReferring` | `ShowReferringObjectsAction` | Show referring objects |
| `XDebugger.PinToTop` | `XDebuggerPinToTopAction` | Pin variable to top of tree |

## Thread Management Actions

| Action ID | Class | Description |
|-----------|-------|-------------|
| `Debugger.FreezeActiveThreadAction` | `FreezeActiveThreadAction` | Freeze current thread |
| `Debugger.ThawActiveThreadAction` | `ThawActiveThreadAction` | Thaw current thread |
| `Debugger.FreezeInactiveThreadsAction` | `FreezeInactiveThreadsAction` | Freeze all other threads |
| `Debugger.ThawAllThreadsAction` | `ThawAllThreadsAction` | Thaw all threads |

## Session Management

| Action ID | Class | Description |
|-----------|-------|-------------|
| `XDebugger.AttachToProcess` | `AttachToProcessAction` | Attach debugger to running process |

## UI/Settings Actions

| Action ID | Class | Description |
|-----------|-------|-------------|
| `XDebugger.ToggleSortValues` | `SortValuesToggleAction` | Alphabetically sort variables |
| `XDebugger.Inline` | `UseInlineDebuggerAction` | Toggle inline debugger values |
| `XDebugger.UnmuteOnStop` | `UnmuteOnStopAction` | Auto-unmute breakpoints when session ends |
| `XDebugger.ToggleEvaluateExpressionField` | (toggle action) | Toggle evaluate field in variables view |
| `Debugger.MarkObject` | `MarkObjectAction` | Mark/label an object instance |

## Programmatic Action Invocation

If you need to invoke an action (only when the direct API isn't available):

```java
AnAction action = ActionManager.getInstance().getAction("ViewBreakpoints");
if (action != null) {
    // You need a DataContext -- this is the hard part from a non-UI context
    DataContext context = DataManager.getInstance().getDataContext(component);
    AnActionEvent event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.UNKNOWN, context);
    action.actionPerformed(event);
}
```

> **Relevant for MCP:** For most debugger operations, use the direct XDebugSession/XBreakpointManager API -- it's cleaner and doesn't need UI context. Action IDs are useful as a fallback for operations that don't have a clean API (e.g., `ViewBreakpoints` to open the breakpoints dialog) or when you want to invoke the exact same behavior the user would trigger from a keyboard shortcut. They're also useful as reference when mapping MCP tool names to IntelliJ functionality.
