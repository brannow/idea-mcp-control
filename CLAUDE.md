# PhpStorm MCP Plugin

## What This Is

A PhpStorm plugin that acts as an MCP (Model Context Protocol) server, exposing the IDE's debugging features to AI agents. The agent can set breakpoints, step through code, inspect variables, evaluate expressions — the same workflow a human developer uses with xdebug.

## Project Structure

```
phpstorm-mcp/
├── src/main/kotlin/com/github/brannow/phpstormmcp/     # Plugin source (Kotlin)
├── src/main/resources/META-INF/plugin.xml              # Plugin configuration
├── roadmap.md                                          # Project Roadmap (todo list)
├── build.gradle.kts                                    # Gradle build (IntelliJ Platform Gradle Plugin 2.x)
├── gradle.properties                                   # Build target: PhpStorm 2025.3 (compat 2025.1 – 2025.3)
├── internal/                                           # Documentation & reference (not shipped with plugin)
│   ├── INDEX.md                                        # ** START HERE ** — master documentation index
│   ├── docs/                                           # Organized documentation
│   │   ├── 03-debugger-api/                            # XDebugger framework docs (primary focus)
│   │   ├── 04-mcp-sdk/                                 # MCP Kotlin/Java SDK overviews
│   │   └── ...                                         # See INDEX.md for full list
│   ├── tools/                                          # Tool design specifications
│   │   └── ToolDesign.md                               # Final tool specs (14 tools, snapshot concept)
│   └── reference-Repository/                           # Cloned reference repos
│       ├── intellij-community/                         # IntelliJ Platform source (xdebugger-api, xdebugger-impl)
│       ├── intellij-sdk-code-samples/                  # Official plugin examples
│       ├── kotlin-sdk/                                 # MCP Kotlin SDK (our primary SDK)
│       └── java-sdk/                                   # MCP Java SDK (reference only)
```

## Key Documentation

| Need to understand...                                 | Read this                                              |
|-------------------------------------------------------|--------------------------------------------------------|
| Documentation overview & navigation                   | `internal/INDEX.md`                                    |
| Project Roadmap (todo list) and what we already build | `roadmap.md`                                           |
| XDebugger API (our core integration)                  | `internal/docs/03-debugger-api/_INDEX.md`              |
| MCP SDK (how we expose tools)                         | `internal/docs/04-mcp-sdk/_INDEX.md`                   |
| Tool design & specifications                          | `internal/tools/ToolDesign.md`                         |
| Tool design philosophy                                | `internal/tools/Tools.md`                              |
| Plugin structure (plugin.xml, services)               | `internal/docs/02-plugin-structure/_INDEX.md`          |
| Editor/PSI APIs (reading code context)                | `internal/docs/05-editor-and-psi/_INDEX.md`            |
| Debugger action IDs                                   | `internal/docs/03-debugger-api/debugger-action-ids.md` |
| API quick reference (all key classes)                 | `internal/docs/03-debugger-api/api-quick-reference.md` |

## Architecture Decisions

- **Language**: Kotlin (JetBrains recommendation, aligns with Kotlin MCP SDK)
- **MCP SDK**: Kotlin SDK (`internal/reference-Repository/kotlin-sdk/`)
- **Target IDE**: this is the **`2025.x` legacy branch** — PhpStorm 2025.1 – 2025.3 (`sinceBuild 251`,
  `untilBuild 253.*`), built against 2025.3 with the older runtime stack (ktor 3.2.3, MCP SDK 0.9,
  Kotlin 2.1). PhpStorm 2026.1+ is served by `main`.
- **Plugin type**: MCP Server — the AI agent is the MCP client
- **Debug sessions**: Human starts sessions, agent interacts with them

## Branching & Compatibility

Two lines, split by IDE build. **The ranges must partition** — Marketplace serves the *highest
version* among builds compatible with the user's IDE, so any overlap means 2025.x users get served
the newer artifact, which is exactly the build that cannot start there.

| Branch    | Version / tag | sinceBuild | untilBuild | Runtime stack                       |
|-----------|---------------|------------|------------|-------------------------------------|
| `2025.x`  | `2025.3.x`    | 251        | **253.\*** | ktor 3.2.3, MCP SDK 0.9, Kotlin 2.1 |
| `main`    | 0.7.x (TBD)   | 261        | 2026.x     | ktor 3.5, MCP SDK 0.13, Kotlin 2.3  |

**Versioning (decided for `2025.x`; main's exact scheme still open):** from `2025.3.1` onward this
branch's version tracks the newest PhpStorm line it serves, replacing semver (0.6.x). Ceiling-based
works here precisely because this line is frozen — its ceiling is pinned at 253 forever, so the
prefix can never go stale. A living branch whose ceiling widens (main) would need floor-based
numbering instead, or it would have to be renumbered mid-line.

`2025.3.1 > 0.7.0` under JetBrains' comparator, so existing users upgrade normally — but it is a
one-way door: you can never go back to `0.x`. The git tag equals `pluginVersion` exactly;
`release.yml` fails the build if they disagree.

**Why the split exists:** the 0.7.0 stack (built against 2026.1.3) fails to start the embedded MCP
server on build 253. The branch is not about IDE majors — it exists because a *runtime floor*
broke. Rule: **branch only when the newest build can no longer run on an older IDE you still want
to serve**, not once per IDE release. One artifact normally spans several majors via `untilBuild`.

**Verification: `verifyPlugin` is necessary, launching is sufficient.** verifyPlugin checks our
bytecode against the platform API and never loads the bundled libraries — 0.7.0 passed it green and
then failed to bind on 253. Use `./gradlew runIde251 / runIde252 / runIde253` (own sandbox each) and
confirm the server actually reaches "Running" before widening `sinceBuild` or cutting a release.

## Core Design Concept: Debug Snapshot

Every tool that changes or inspects debug state returns a standardized **Debug Snapshot** — the same context a human sees in the debug panel:
- Session info (id, name, status, active)
- Position (file, line, method, class)
- Source code (scope-aware: shows containing method or ~10 lines around current position)
- Variables (top-level with type + value preview)
- Stacktrace (full call stack)

Snapshots are customizable via `include` parameter for token efficiency.

See `internal/tools/ToolDesign.md` for full tool specifications.

## Build & Run

```bash
./gradlew build          # Compile + test
./gradlew runIde         # Launch sandboxed PhpStorm (2025.3, the build target)
./gradlew buildPlugin    # Build distributable .zip
./gradlew verifyPlugin   # Check our bytecode against the 251 floor (see caveat above)

# Launch a specific supported IDE — each has its own sandbox, so a stale cache
# in one cannot mask a first-run failure in another.
./gradlew runIde251      # PhpStorm 2025.1
./gradlew runIde252      # PhpStorm 2025.2
./gradlew runIde253      # PhpStorm 2025.3
```

Requires JDK 21 (JBR recommended). IDE for development: IntelliJ IDEA Community Edition.

## Key Source Locations in Reference Repos

XDebugger API (the interfaces we call):
```
internal/reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/
├── XDebuggerManager.java          # Entry point — get debug sessions
├── XDebugSession.java             # Session control (step, resume, etc.)
├── XDebugProcess.java             # Language-specific debug process
├── breakpoints/                   # Breakpoint management
├── frame/                         # Stack frames, variables (XStackFrame, XValue)
├── evaluation/                    # Expression evaluation
└── stepping/                      # Smart step into
```

MCP Kotlin SDK (how we build the server):
```
internal/reference-Repository/kotlin-sdk/
├── README.md                      # Comprehensive SDK reference
├── kotlin-sdk-server/             # Server module (our primary dependency)
├── kotlin-sdk-core/               # Protocol types, transports
└── samples/                       # Example implementations
```
