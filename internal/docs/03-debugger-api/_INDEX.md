# 03 — Debugger API (Primary Focus)

The IntelliJ XDebugger framework — the core API our MCP plugin wraps. This is where 90% of our plugin logic lives.

## Architecture

```
XDebuggerManager (singleton per project)
  └── XDebugSession (one per active debug run)
        └── XDebugProcess (language-specific, e.g. PHP/Xdebug)
              ├── XBreakpointHandler (manages breakpoints)
              ├── XDebuggerEvaluator (expression eval)
              └── XSuspendContext → XExecutionStack → XStackFrame → XValue
```

## Docs

- [xdebugger-overview.md](xdebugger-overview.md) — Framework architecture, the Manager→Session→Process layers, entry points
- [breakpoints.md](breakpoints.md) — XBreakpointManager: add/remove/toggle breakpoints programmatically
- [stepping-and-control.md](stepping-and-control.md) — Session control: stepOver, stepInto, stepOut, resume, pause, stop
- [stack-frames-and-variables.md](stack-frames-and-variables.md) — Reading debug state: call stack, variables, values (async callback pattern)
- [evaluation.md](evaluation.md) — XDebuggerEvaluator: evaluate expressions in debug context
- [session-events.md](session-events.md) — XDebugSessionListener: react to breakpoint hits, pause, resume, stop
- [debugger-action-ids.md](debugger-action-ids.md) — 40+ IDE action IDs for debugger operations (Debugger.StepOver, etc.)
- [api-quick-reference.md](api-quick-reference.md) — Quick reference: all key classes with descriptions and file paths

## MCP Tool Mapping (Preview)

| MCP Tool | API Entry Point |
|---|---|
| `debug/breakpoints/list` | `XBreakpointManager.getAllBreakpoints()` |
| `debug/breakpoints/add` | `XBreakpointManager.addLineBreakpoint()` |
| `debug/step_over` | `XDebugSession.stepOver()` |
| `debug/step_into` | `XDebugSession.stepInto()` |
| `debug/resume` | `XDebugSession.resume()` |
| `debug/stack_frames` | `XSuspendContext → XExecutionStack.computeStackFrames()` |
| `debug/variables` | `XStackFrame.computeChildren()` |
| `debug/evaluate` | `XDebuggerEvaluator.evaluate()` |
