# API Quick Reference

## Core Classes

All paths relative to: `../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/`

| Class | Description | Path |
|-------|-------------|------|
| `XDebuggerManager` | Project-level entry point: get sessions, breakpoint manager | [`XDebuggerManager.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/XDebuggerManager.java) |
| `XDebugSession` | Active debug session: control execution, query state, listen for events | [`XDebugSession.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/XDebugSession.java) |
| `XDebugProcess` | Language-specific debug process (PHP plugin implements this) | [`XDebugProcess.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/XDebugProcess.java) |
| `XDebugProcessStarter` | Factory that creates XDebugProcess for a session | [`XDebugProcessStarter.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/XDebugProcessStarter.java) |
| `AbstractDebuggerSession` | Base interface: `isStopped()`, `isPaused()` | [`AbstractDebuggerSession.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/AbstractDebuggerSession.java) |
| `XDebuggerUtil` | Utility: toggle breakpoints, create positions, find types | [`XDebuggerUtil.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/XDebuggerUtil.java) |
| `XSourcePosition` | File + line (0-based) + offset | [`XSourcePosition.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/XSourcePosition.java) |
| `XExpression` | Expression text + language + evaluation mode | [`XExpression.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/XExpression.java) |

## Listener Interfaces

| Class | Description | Path |
|-------|-------------|------|
| `XDebugSessionListener` | Per-session events: paused, resumed, stopped, frame changed | [`XDebugSessionListener.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/XDebugSessionListener.java) |
| `XDebuggerManagerListener` | Manager events: process started/stopped, current session changed | [`XDebuggerManagerListener.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/XDebuggerManagerListener.java) |
| `XBreakpointListener` | Breakpoint CRUD events: added, removed, changed | [`breakpoints/XBreakpointListener.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/breakpoints/XBreakpointListener.java) |

## Breakpoint Classes

| Class | Description | Path |
|-------|-------------|------|
| `XBreakpointManager` | Breakpoint CRUD: add, remove, list, find breakpoints | [`breakpoints/XBreakpointManager.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/breakpoints/XBreakpointManager.java) |
| `XBreakpoint<P>` | A breakpoint instance: enabled, condition, suspend policy | [`breakpoints/XBreakpoint.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/breakpoints/XBreakpoint.java) |
| `XLineBreakpoint<P>` | Line breakpoint: file URL + line number | [`breakpoints/XLineBreakpoint.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/breakpoints/XLineBreakpoint.java) |
| `XBreakpointType<B,P>` | Breakpoint type definition (extension point) | [`breakpoints/XBreakpointType.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/breakpoints/XBreakpointType.java) |
| `XLineBreakpointType<P>` | Line breakpoint type (registered by language plugins) | [`breakpoints/XLineBreakpointType.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/breakpoints/XLineBreakpointType.java) |
| `XBreakpointHandler<B>` | Registers/unregisters breakpoints in debugger engine | [`breakpoints/XBreakpointHandler.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/breakpoints/XBreakpointHandler.java) |
| `XBreakpointProperties` | Base for breakpoint-type-specific properties | [`breakpoints/XBreakpointProperties.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/breakpoints/XBreakpointProperties.java) |
| `SuspendPolicy` | Enum: ALL, THREAD, NONE | [`breakpoints/SuspendPolicy.kt`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/breakpoints/SuspendPolicy.kt) |

## Frame/Variable Classes

| Class | Description | Path |
|-------|-------------|------|
| `XSuspendContext` | Suspended state: contains execution stacks (threads) | [`frame/XSuspendContext.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/frame/XSuspendContext.java) |
| `XExecutionStack` | Thread's call stack: top frame + computed frames | [`frame/XExecutionStack.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/frame/XExecutionStack.java) |
| `XStackFrame` | Single stack frame: source position, evaluator, variables | [`frame/XStackFrame.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/frame/XStackFrame.java) |
| `XValueContainer` | Base class with `computeChildren(XCompositeNode)` | [`frame/XValueContainer.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/frame/XValueContainer.java) |
| `XValue` | A debug value: presentation, children, modifier, navigation | [`frame/XValue.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/frame/XValue.java) |
| `XNamedValue` | XValue with a name (most variables) | [`frame/XNamedValue.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/frame/XNamedValue.java) |
| `XValueNode` | Callback for value presentation: `setPresentation(icon, type, value, hasChildren)` | [`frame/XValueNode.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/frame/XValueNode.java) |
| `XValueCallback` | Error callback: `errorOccurred(String)` | [`frame/XValueCallback.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/frame/XValueCallback.java) |
| `XCompositeNode` | Callback for children: `addChildren(list, last)`, `tooManyChildren()` | [`frame/XCompositeNode.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/frame/XCompositeNode.java) |
| `XValueChildrenList` | List of name-value pairs for `addChildren()` | [`frame/XValueChildrenList.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/frame/XValueChildrenList.java) |
| `XValueModifier` | Set variable values: `setValue(expression, callback)` | [`frame/XValueModifier.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/frame/XValueModifier.java) |
| `XFullValueEvaluator` | Lazy full value loading for large values | [`frame/XFullValueEvaluator.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/frame/XFullValueEvaluator.java) |

## Evaluation Classes

| Class | Description | Path |
|-------|-------------|------|
| `XDebuggerEvaluator` | Expression evaluation: `evaluate(expression, callback, position)` | [`evaluation/XDebuggerEvaluator.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/evaluation/XDebuggerEvaluator.java) |
| `XDebuggerEditorsProvider` | Language-aware expression editor documents | [`evaluation/XDebuggerEditorsProvider.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/evaluation/XDebuggerEditorsProvider.java) |
| `XInstanceEvaluator` | Evaluate by object identity | [`evaluation/XInstanceEvaluator.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/evaluation/XInstanceEvaluator.java) |

## Stepping Classes

| Class | Description | Path |
|-------|-------------|------|
| `XSmartStepIntoHandler<V>` | Smart step into: list callable targets on a line | [`stepping/XSmartStepIntoHandler.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/stepping/XSmartStepIntoHandler.java) |
| `XSmartStepIntoVariant` | A specific call target for smart step into | [`stepping/XSmartStepIntoVariant.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/stepping/XSmartStepIntoVariant.java) |
| `XDropFrameHandler` | Drop/reset current frame (experimental) | [`frame/XDropFrameHandler.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/frame/XDropFrameHandler.java) |

## MCP Tool Mapping

Quick mapping of XDebugger API to likely MCP tools:

| MCP Tool | Primary API |
|----------|-------------|
| `debugger/sessions` | `XDebuggerManager.getDebugSessions()` |
| `debugger/stepOver` | `XDebugSession.stepOver(false)` |
| `debugger/stepInto` | `XDebugSession.stepInto()` |
| `debugger/stepOut` | `XDebugSession.stepOut()` |
| `debugger/resume` | `XDebugSession.resume()` |
| `debugger/pause` | `XDebugSession.pause()` |
| `debugger/stop` | `XDebugSession.stop()` |
| `debugger/addBreakpoint` | `XBreakpointManager.addLineBreakpoint()` or `XDebuggerUtil.toggleLineBreakpoint()` |
| `debugger/removeBreakpoint` | `XBreakpointManager.removeBreakpoint()` |
| `debugger/listBreakpoints` | `XBreakpointManager.getAllBreakpoints()` |
| `debugger/stackTrace` | `XSuspendContext.getActiveExecutionStack()` -> `computeStackFrames()` |
| `debugger/variables` | `XStackFrame.computeChildren()` |
| `debugger/evaluate` | `XDebuggerEvaluator.evaluate()` |
| `debugger/setVariable` | `XValue.getModifier()` -> `XValueModifier.setValue()` |
| `debugger/state` | `XDebugSession.isSuspended()`, `getCurrentPosition()` |
