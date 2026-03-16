# Roadmap

Build incrementally — each milestone is testable on its own before moving to the next.

---

## Milestone 0: Project Foundation ✅

- [x] Project scaffold (Gradle, plugin.xml, Kotlin)
- [x] Hello World plugin loads in sandboxed PhpStorm
- [x] Documentation organized with index
- [x] Tool design spec finalized (16 tools, snapshot concept)
- [x] Logo, .gitignore, initial commit

---

## Milestone 0.5: Status Bar Widget ✅

Give the human visibility into the MCP server state — a small indicator in PhpStorm's status bar.

- [x] Status bar widget with plugin icon (grayed out = inactive, colored = active)
- [x] Click to open popup with: connection status, server port/transport info
- [x] Activity log: lightweight rolling log of recent MCP events ("Client connected", "breakpoint_add called", etc.)
- [x] Server start/stop toggle from the widget

**Test**: Plugin loads → icon appears in status bar (grayed out). Server starts → icon lights up. Click icon → see status popup. Later when tools are implemented, verify tool calls appear in the activity log.

---

## Milestone 1: MCP Server Infrastructure ✅

Get a working MCP server running inside the plugin that an external client can connect to.

- [x] Add Kotlin MCP SDK dependency to build.gradle.kts
- [x] Create MCP server service (project-level, starts with project)
- [x] Transport: Streamable HTTP on localhost (fixed port 6969, fallback to random). See `internal/docs/04-mcp-sdk/stdio-proxy-architecture.md` for stdio proxy analysis.
- [x] Register a single dummy tool (`ping` → returns `pong`) to verify the protocol works
- [x] Connect from an external MCP client (MCP Inspector) and call `ping`
- [x] Client connect/disconnect notifications in activity log and widget

**Test**: MCP client connects → calls `ping` → gets `pong` response. ✅ Verified with MCP Inspector.

---

## Milestone 2: Breakpoint Tools ✅

First real tools — breakpoints work without an active debug session, so they're the simplest to test.

- [x] `breakpoint_list` — list all line breakpoints, optional file filter
- [x] `breakpoint_add` — add a line breakpoint (file + line), with optional condition, log expression, suspend toggle
- [x] `breakpoint_update` — enable/disable, change condition/log expression/suspend by ID or file:line
- [x] `breakpoint_remove` — remove by ID(s), file:line(s) (comma-separated), or `all=true` to remove all
- [x] Flexible file paths: accepts absolute or project-relative paths, returns project-relative
- [x] Flexible IDs: numeric timestamp ID or `file:line` reference (e.g. `src/index.php:5`)
- [x] Line validation: rejects line 0, negative lines, and lines beyond the file's actual line count
- [x] Library detection via `ProjectFileIndex.isInLibrary()` (not string matching on "vendor/")
- [x] Multi-breakpoint-line hint: groups same-line breakpoints with `(multi-breakpoint-line)` label everywhere
- [x] Two output modes: full detail for direct list, compact index (`#ID file:line`) for context hints
- [x] Not-found errors always show all project breakpoints (no substring filtering)
- [x] Update validates ID exists before checking for missing changes (most actionable error first)
- [x] No-changes guard on update: rejects calls with only an ID and no change params
- [x] Ambiguous file:line handling with guidance message
- [x] Unit tests: parameterized test suite covering list, add, update, remove with all edge cases

**Test**: Breakpoints added via MCP Inspector appear in PhpStorm gutter. List matches Breakpoints dialog. Remove clears them. ✅ Verified.

---

## Milestone 3: Session Management ✅

- [x] `session_list` — list active debug sessions with status and active flag
- [x] `session_stop` — stop a specific session or all sessions
- [x] Active session detection (which session is currently focused in the UI)
- [x] Smart stop: no params + no sessions = ok (no-op), specific ID not found = error
- [x] Consistent `ok`/`err` behavior across all stop scenarios
- [x] Unit tests: parameterized test suite for list and stop with edge cases

**Test**: Manually start 1-2 debug sessions in PhpStorm. Call `session_list` → verify output matches the debug tabs. Call `session_stop` → verify session ends. ✅ Verified.

---

## Milestone 3.5: Output Design & Code Quality ✅

Natural language output system, testability refactoring, and platform compliance.

- [x] Natural language output: tools respond with human-readable text, not JSON
- [x] Shared response pattern: result → context → error, consistent across all tools
- [x] Services made final: Platform interface pattern for testability without `open` classes
- [x] IntelliJ platform compliance: `JBList` usage, final light services, dead code removal
- [x] Tool design spec (ToolDesign.md) updated with all output patterns and validation rules

---

## Milestone 4a: Source Context

When paused at a breakpoint, show the agent where it is and the surrounding code. This is the read-the-file part of the snapshot.

- [ ] Get current position from paused session (`XDebugSession.currentPosition`)
- [ ] Read source file content around current line
- [ ] Scope-aware extraction: detect containing method/function boundaries
  - Method ≤ 30 lines → show the whole method
  - Method > 30 lines → show ±10 lines around current position
  - Always include method signature for context
- [ ] Mark the current line in output (e.g. `→` prefix or similar)
- [ ] Handle edge cases: top-level code (no method), file not found, binary files

**Test**: Manually pause at a breakpoint inside a method. Verify the source context shows the method with the current line marked. Pause at top-level code → verify reasonable context is shown.

---

## Milestone 4b: Stack Frames

Walk the call stack from `XSuspendContext` and present it as readable output.

- [ ] Extract execution stack from `XSuspendContext` → `XExecutionStack`
- [ ] Iterate `XStackFrame[]` with async `computeStackFrames()` callback
- [ ] For each frame: extract file, line, function/method name, class
- [ ] Format as readable stacktrace (deepest first or shallowest first — decide based on what's most useful)
- [ ] Handle edge cases: native frames, eval'd code, very deep stacks (truncation?)

**Test**: Pause at a breakpoint 3+ calls deep. Verify the stack matches PhpStorm's Frames panel. Check that file paths are project-relative.

---

## Milestone 4c: Variables

The hardest part — `XStackFrame.computeChildren()` is async/callback-based, values are lazy-loaded.

- [ ] Extract top-level variables from current `XStackFrame`
- [ ] Handle async `computeChildren()` callback pattern
- [ ] Variable preview generation:
  - Scalars → value (`$count = 42`, `$name = "hello"`)
  - Objects → class name (`$request = {ServerRequest}`)
  - Arrays → count (`$items = array(15)`)
  - Null → `$foo = null`
  - Truncate long strings, limit preview depth
- [ ] Handle superglobals, special PHP variables
- [ ] Foundation for later `debug_variable_detail` (expanding nested values)

**Test**: Pause at a breakpoint with mixed variable types. Verify previews match PhpStorm's Variables panel. Check that objects show class names, arrays show counts, strings are truncated sensibly.

---

## Milestone 4d: Debug Snapshot Tool

Compose the pieces from 4a-4c into the `debug_snapshot` tool.

- [ ] `debug_snapshot` tool — returns full snapshot of current paused state
- [ ] Snapshot data model composing: session info + source context + stack frames + variables
- [ ] `include` parameter — filter snapshot to only requested parts (e.g. `["source", "variables"]`)
- [ ] Session + position always included regardless of `include` (minimal overhead, always needed)
- [ ] Handle "not paused" state gracefully (session running, no session, session stopped)

**Test**: Pause at a breakpoint. Call `debug_snapshot` → verify the response matches PhpStorm's debug panel. Test `include: ["variables"]` returns only variables + position. Test with no active session → meaningful error.

---

## Milestone 5: Navigation / Stepping

Each tool triggers an action and returns a snapshot when the debugger pauses again.

- [ ] Async wait pattern: trigger action → listen for pause event → return snapshot
- [ ] `debug_step_over` — step over + return snapshot
- [ ] `debug_step_into` — step into + return snapshot
- [ ] `debug_step_out` — step out + return snapshot
- [ ] `debug_continue` — resume + return snapshot (or session-ended)
- [ ] `debug_run_to_line` — run to specific line + return snapshot
- [ ] Timeout handling: what if `debug_continue` never hits a breakpoint?

**Test**: Pause at a breakpoint. Call `debug_step_over` → verify response shows the next line. Call `debug_step_into` on a function call → verify you're inside the function. Call `debug_continue` → verify you hit the next breakpoint or session ends.

---

## Milestone 6: Deep Inspection

- [ ] `debug_inspect_frame` — switch to a different stack frame, return snapshot at that scope
- [ ] `debug_variable_detail` — expand nested variables by path (e.g. `$request.headers`)
- [ ] `debug_evaluate` — evaluate PHP expression in current context
- [ ] `debug_set_value` — modify a variable at runtime

**Test**: Pause at a breakpoint. Call `debug_inspect_frame(2)` → verify variables match that frame's scope. Call `debug_variable_detail("$request")` → verify children match the Variables panel. Call `debug_evaluate("count($items)")` → verify result. Call `debug_set_value("$count", "99")` → verify variable changed in PhpStorm.

---

## Milestone 7: Integration & Polish

- [ ] Error handling: graceful responses for no session, session running (not paused), invalid paths
- [ ] Multi-session: verify all tools work correctly with 2+ concurrent sessions
- [ ] Edge cases: very large stack traces, deeply nested objects, long string values
- [ ] Performance: snapshot generation should be fast, variable expansion should be lazy

---

## Future (Post-v1)

Not in scope now, but where this goes:

- Exception breakpoints
- Watch expressions (persistent across steps)
- Start/restart debug sessions from agent
- Mute/unmute all breakpoints
- Smart step into (choose which function to enter)
- **Beyond debugging**: refactoring tools (rename, extract method), code navigation (find usages, go to definition), run configurations, test runner integration
