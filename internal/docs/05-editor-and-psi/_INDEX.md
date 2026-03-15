# 05 — Editor and PSI

APIs for reading code programmatically. Needed because when the debugger pauses, the AI agent needs to see the source code around the current position.

## Docs

- [editor-and-psi-basics.md](editor-and-psi-basics.md) — Three-layer model (VirtualFile → Document → PSI), reading code around breakpoints, finding containing methods/classes

## Key Takeaways for Our Plugin

- Use `Document` to read raw lines around a breakpoint position
- Use `PsiFile`/`PsiElement` to get structural info (method name, class name, function signature)
- All PSI access requires a **read action** (`ReadAction.compute()`)
- Watch out for 0-based (Document) vs 1-based (UI) line numbers
