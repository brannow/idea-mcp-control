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
├── gradle.properties                                   # Build target: PhpStorm 2026.1.3 (compat down to 2025.3)
├── internal/                                           # Documentation & reference (not shipped with plugin)
│   ├── INDEX.md                                        # ** START HERE ** — master documentation index
│   ├── docs/                                           # Organized documentation
│   │   ├── 03-debugger-api/                            # XDebugger framework docs (primary focus)
│   │   ├── 04-mcp-sdk/                                 # MCP Kotlin/Java SDK overviews
│   │   └── ...                                         # See INDEX.md for full list
│   ├── tools/                                          # Tool design specifications
│   │   └── ToolDesign.md                               # Final tool specs (13 tools, snapshot concept)
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
- **Target IDE**: this is `main`, the current line — built against PhpStorm 2026.1.3, `sinceBuild 261`
  with **no upper limit**. PhpStorm 2025.1 – 2025.3 is served by the `2025.x` branch.
  _(toolchain: Kotlin 2.3.21, IntelliJ Gradle Plugin 2.16, Gradle 9.5.1, MCP SDK 0.13, ktor 3.5)_

## Branching & Compatibility

Two lines, split by IDE build. **The ranges must not overlap** — Marketplace serves the *highest
version* among builds compatible with the user's IDE, so any overlap means 2025.x users get served
the newer artifact, which is exactly the build that cannot start there.

| Branch   | Version / tag | sinceBuild | untilBuild | Runtime stack                       |
|----------|---------------|------------|------------|-------------------------------------|
| `2025.x` | `2025.3.x`    | 251        | 253.\*     | ktor 3.2.3, MCP SDK 0.9, Kotlin 2.1 |
| `main`   | `2026.2.x`    | 261        | **open**   | ktor 3.5, MCP SDK 0.13, Kotlin 2.3  |

**Why the split exists:** the 0.7.0 stack fails to start the embedded MCP server on build 253. The
branch is not about IDE majors — it exists because a *runtime floor* broke. Rule: **branch only when
the newest build can no longer run on an older IDE you still want to serve**, not once per IDE
release. One artifact normally spans several majors.

**Versioning — one rule, both branches: the version names the newest PhpStorm release the build has
been launch-verified against.** `2025.3.1` was launched on 2025.3 (and 2025.1/2025.2); `2026.2.1` was
launched on 2026.1 and 2026.2. This replaces semver, which said nothing useful — "0.6.1 vs 0.7.0"
gave no hint which IDE a build was for while both carried identical tool code.

Deliberately *not* the floor. The floor is already stated by `since-build`, and naming the version
after it would make the number say nothing about what was tested. The cost is accepted knowingly:
the prefix moves when a new major is verified (2026.2.1 → 2026.3.1) even without a code change, and
the minimum required IDE is not visible in the number — `since-build` carries that. So the number
answers "how current is this?", not "what do I need?".

Because the version is a *verification* claim, bumping the prefix without launching that IDE makes
it a lie. Run the matching `runIde` task first.

`2026.2.1 > 0.7.1` under JetBrains' comparator so users upgrade normally — and it is a one-way door,
there is no going back to `0.x`. The git tag equals `pluginVersion` exactly; `release.yml` fails the
build if they disagree.

**The open ceiling is a promise with an obligation, and it takes TWO checks — neither one covers
the other.** When a new PhpStorm major appears, before releasing:

1. `./gradlew runIde26N` — catches **runtime** breakage. The verifier never loads a class or
   exercises the bundled ktor/coroutines/MCP-SDK stack, so it cannot see this. 0.7.0 passed
   verification and then failed to bind its server on 253.
2. `./gradlew verifyPlugin` — catches **API-policy** problems that run perfectly well. Widen the
   `pluginVerification` range in `build.gradle.kts` to the new major first, or the check silently
   skips it. 2026.2.1 launched cleanly on 2026.2 *and* was rejected by Marketplace at the same
   moment, for `PluginManagerCore.getPlugin` — legal on 2026.1, internal on 2026.2. A floor-only
   verification range could not have caught it; Marketplace verifies the whole declared range.

Then register the next `runIde` task and bump the `pluginVerification` ceiling together — they are
the same decision. If a future release does break the plugin, set `until-build` on the affected
*published* version in the Marketplace UI; compatibility of an uploaded build is editable after the
fact, which is what makes an open ceiling recoverable rather than a gamble.

- **Two release lines** — `main` serves 2026.1+; branch `2025.x` serves 2025.1 – 2025.3 (version
  2025.3.x, old runtime stack: Kotlin 2.1.10, MCP SDK 0.9.0, ktor 3.2.3, platform 2025.3). Backport
  tool features to the legacy branch by cherry-picking; never merge `main` into it, or the runtime
  stack comes with it.
  - **Why the split**: 0.7.0 declared `sinceBuild 253` but never started on build 253 — the ktor CIO server died before binding while the UI still flipped to "started". The cause is the bundled 2026.1-era runtime stack (ktor 3.5 / coroutines 1.11 / stdlib 2.3), not our own code: `git diff 0.6.0..0.7.0 -- src/.../server/` is empty. Building against an older platform would not help, because the bundled jars are the same either way — only reverting the dependency versions does, which is what the maintenance branch is.
  - **`verifyPlugin` cannot catch this.** It checks our bytecode against the platform API surface. It never loads a class, resolves `ServiceLoader`, or exercises bundled libraries — it reported 0.7.0 as "Compatible" with 253. The only real check is launching the target IDE (`runIde`).
- **Plugin type**: MCP Server — the AI agent is the MCP client
- **Debug sessions**: Human starts sessions, agent interacts with them

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
./gradlew runIde         # Launch sandboxed PhpStorm (2026.1.3, the build target)
./gradlew buildPlugin    # Build distributable .zip
./gradlew verifyPlugin   # Check our bytecode against the 261 floor (see caveat above)

# Launch a specific supported IDE — each has its own sandbox, so a stale cache
# in one cannot mask a first-run failure in another. Required before release:
# with an open ceiling, this is the only check that sees a broken new major.
./gradlew runIde261      # PhpStorm 2026.1
./gradlew runIde262      # PhpStorm 2026.2
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
