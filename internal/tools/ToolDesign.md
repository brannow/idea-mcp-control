# MCP Tool Design for PhpStorm Debugger Plugin

## Design Principle

The tools should provide the same comfort and QoL to the Agent as PHPStorm to the user,
They should reflect the same intuitive and refined nature of what is really important without dumping raw information at the agent.

A tool is not a thin wrapper for an api call it a refined tool. like an Application for an Human. A human don't know what api in the background is called or how that api works, he use the tool and the tool handle the processing.

A good tool can be found by a simple question: "Did the Agent need knowledge about the API/System internally in order use that tool?" If the answer is yes the tool is might be not good.

The agent should not need internal API/system knowledge to use a tool. Think of tools as applications for the agent, the same way PhpStorm is an application for the human.

**Key decision**: Every tool that changes or inspects debug state returns a **Debug Snapshot** — a rich, standardized response that gives the agent the same context a human gets by looking at the debug panel.

## Core Concept: Debug Snapshot

The snapshot is not a tool itself — it's the **standard response format** returned by most tools. It mirrors what a human sees when paused at a breakpoint:

```
Debug Snapshot:
├── session:      { id, name, status, active }
├── position:     { file, line, method, class, namespace }
├── source:       scope-aware code context (the containing method,
│                 or ~10 lines above/below if method is large)
│                 with current line marked
├── variables:    top-level variables with type + value preview
│                 (scalars show value, objects show class name,
│                  arrays show count)
└── stacktrace:   [ { depth, file, line, method, class }, ... ]
```

All snapshot-returning tools accept optional `include` parameter to request
only specific parts (e.g., `include: ["source", "variables"]`).
Session + position are always included (minimal overhead, always needed).

**Scope-aware source context**: Instead of blindly showing N lines, detect the containing method/function. If it's ≤ 30 lines, show the whole method. If larger, show ~10 lines above and below the current line. Always include method signature for context.

**Variable previews**: Keep it scannable. `$count = 42`, `$request = {ServerRequest}`, `$items = array(15)`, `$name = "hello world"`. The agent can use `variable_detail` to dig deeper.

---

## Tools

### 1. Breakpoints

These work independently of debug sessions — breakpoints exist in the project regardless of whether debugging is active.

#### `breakpoint_list`
List all breakpoints in the project.

**Input**: (none, or optional file filter)
**Output**: List of breakpoints, each with:
- id, file, line, enabled, condition (if any), log_expression (if any), suspend (true/false), hit_count

---

#### `breakpoint_add`
Add a line breakpoint.

**Input**:
- `file` (required) — file path
- `line` (required) — line number
- `condition` (optional) — PHP expression, e.g. `$request === null`
- `log_expression` (optional) — expression to evaluate and log when hit
- `suspend` (optional, default true) — whether to pause execution

**Output**: The created breakpoint with its id

---

#### `breakpoint_update`
Modify an existing breakpoint.

**Input**:
- `id` (required) — breakpoint id
- `enabled` (optional) — true/false
- `condition` (optional) — new condition (empty string to remove)
- `log_expression` (optional) — new log expression
- `suspend` (optional) — true/false

**Output**: Updated breakpoint

---

#### `breakpoint_remove`
Remove breakpoint(s).

**Input**:
- `id` (optional) — specific breakpoint id
- `all` (optional) — true to clear all breakpoints

**Output**: Confirmation + remaining breakpoint count

---

### 2. Session Management

#### `session_list`
List active debug sessions.

**Input**: (none)
**Output**: List of sessions, each with:
- id, name, status (paused/running/stopped), current_file, current_line, active (true/false)

---

#### `session_stop`
Stop debug session(s).

**Input**:
- `session_id` (optional) — specific session. If omitted + only one session → stops that one. If omitted + multiple → error asking to specify.
- `all` (optional) — true to stop all sessions

**Output**: Confirmation

---

### 3. Navigation / Control

All navigation tools:
- Accept optional `session_id` (defaults to active session)
- Return a **Debug Snapshot** after the action completes
- Are async: they trigger the action, wait for the debugger to pause again, then return the snapshot

#### `debug_continue`
Resume execution until next breakpoint or end.

**Input**: optional `session_id`, optional `include`
**Output**: Debug Snapshot (at next breakpoint) or session-ended status

---

#### `debug_step_over`
Execute current line, stop at next line in same scope.

**Input**: optional `session_id`, optional `include`
**Output**: Debug Snapshot

---

#### `debug_step_into`
Step into the function call on current line.

**Input**: optional `session_id`, optional `include`
**Output**: Debug Snapshot

---

#### `debug_step_out`
Run until current function returns, stop in caller.

**Input**: optional `session_id`, optional `include`
**Output**: Debug Snapshot

---

#### `debug_run_to_line`
Continue execution until reaching a specific line (temporary breakpoint).

**Input**:
- `file` (required)
- `line` (required)
- optional `session_id`, optional `include`

**Output**: Debug Snapshot (at target line) or session-ended if line not reached

---

### 4. Inspection

#### `debug_snapshot`
Get the current debug state without changing anything. This is the "just show me where we are" tool.

**Input**: optional `session_id`, optional `include`
**Output**: Debug Snapshot

---

#### `debug_inspect_frame`
Switch inspection to a different stack frame. Like clicking a row in the stacktrace panel — shows variables and code at that frame's location.

**Input**:
- `frame_index` (required) — 0 = current (top), 1 = caller, etc.
- optional `session_id`, optional `include`

**Output**: Debug Snapshot (for the selected frame — source + variables at that frame's scope)

---

#### `debug_variable_detail`
Expand a variable to see its children/properties. For drilling into nested objects and arrays.

**Input**:
- `path` (required) — variable path, e.g. `$request`, `$request.headers`, `$request.attributes.0`
- `depth` (optional, default 1) — how many levels of children to return
- optional `session_id`

**Output**: Variable tree from the specified path:
```
$request.headers = {array(19)}
  ├── host = "example.com"
  ├── accept = "text/html"
  ├── cookie = "session=abc..."
  └── ... (16 more)
```

---

#### `debug_evaluate`
Evaluate a PHP expression in the current debug context.

**Input**:
- `expression` (required) — PHP expression, e.g. `$request->getMethod()`, `count($items)`, `$user->getName()`
- optional `session_id`

**Output**: Result with type and value (same format as variable preview, expandable via `variable_detail`)

---

#### `debug_set_value`
Modify a variable's value at runtime.

**Input**:
- `path` (required) — variable path, e.g. `$count`, `$request.method`
- `value` (required) — new value as string (e.g. `42`, `"hello"`, `null`)
- optional `session_id`

**Output**: Confirmation + updated Debug Snapshot

---

## Tool Count Summary

| Category | Tools | Count |
|---|---|---|
| Breakpoints | list, add, update, remove | 4 |
| Sessions | list, stop | 2 |
| Navigation | continue, step_over, step_into, step_out, run_to_line | 5 |
| Inspection | snapshot, inspect_frame, variable_detail, evaluate, set_value | 5 |
| **Total** | | **16** |

---

## Active Session Convention

PhpStorm maintains an **active session** — the one currently selected in the debug tab. All session-scoped tools default to the active session:

- **0 sessions**: Error "No active debug session"
- **1 session**: That session is always active
- **N sessions**: The one the user has focused (or last interacted with) is active. Agent can switch by passing a different `session_id`.

The snapshot always includes which session is active:
```
session: { id: "abc", name: "index.php", status: "paused", active: true }
```

`session_list` marks which session is active. The agent only needs to think about session IDs when it wants to work with a non-active session.

## Snapshot Customization

All tools that return a snapshot accept an optional `include` parameter to request only specific parts:

```
include: ["variables"]              → only variables
include: ["source", "variables"]    → source + variables, no stacktrace
include: ["stacktrace"]             → only the stack
```

**Default (no `include`)**: full snapshot (position + source + variables + stacktrace).

This matters for token efficiency — if the agent is stepping through 10 lines, it probably doesn't need the full stacktrace every time. Just `include: ["source", "variables"]` to see what changed.

---

## What We're NOT Including (v1)

- **Watches**: `evaluate` covers this — watches are a UI persistence concept for humans
- **Starting debug sessions**: Human controls when debugging begins
- **Mute all breakpoints**: Edge case, add later if needed
- **Exception breakpoints**: Only line breakpoints for v1
- **Conditional stepping (smart step into)**: Too complex for v1

## Implementation Notes

- Navigation tools are **async by nature**: they trigger an action in PhpStorm, then wait for the debugger to pause. The MCP response returns only when paused (or timed out / session ended).
- All tools run their IDE operations on EDT (Event Dispatch Thread) as required by IntelliJ, but the MCP request handling itself is on a background thread.
- Variable preview generation should be smart: truncate long strings, limit array previews, show class names for objects.
