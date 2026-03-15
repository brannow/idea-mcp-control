# Expression Evaluation

## Overview

Expression evaluation lets you execute code in the context of the current debug frame. This powers the "Evaluate Expression" dialog, watch expressions, conditional breakpoints, and log expressions.

## XDebuggerEvaluator

**Source:** [`../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/evaluation/XDebuggerEvaluator.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/evaluation/XDebuggerEvaluator.java)

### Getting an Evaluator

The evaluator is tied to a specific stack frame:

```java
// Option 1: From the current stack frame
XStackFrame frame = session.getCurrentStackFrame();
XDebuggerEvaluator evaluator = frame != null ? frame.getEvaluator() : null;

// Option 2: From the debug process (uses current frame internally)
XDebuggerEvaluator evaluator = session.getDebugProcess().getEvaluator();
```

Both paths end at the same place: `XStackFrame.getEvaluator()`. The evaluator is frame-specific because variable scope depends on where you are in the call stack.

### Core Method: evaluate()

```java
public abstract void evaluate(@NotNull String expression,
                              @NotNull XEvaluationCallback callback,
                              @Nullable XSourcePosition expressionPosition);

// Overload that accepts XExpression (with language and mode info)
public void evaluate(@NotNull XExpression expression,
                     @NotNull XEvaluationCallback callback,
                     @Nullable XSourcePosition expressionPosition);
```

- **`expression`** -- The code to evaluate (e.g., `"$user->getName()"`, `"count($items)"`)
- **`callback`** -- Reports result or error (see below)
- **`expressionPosition`** -- Source position context for the evaluation. Usually the current frame's position, or `null`.

### XEvaluationCallback

```java
public interface XEvaluationCallback extends XValueCallback {
    // Success: result is an XValue you can inspect (get type, value, children)
    void evaluated(@NotNull XValue result);

    // The expression is invalid in this context (e.g., wrong scope)
    default void invalidExpression(@NotNull String error) {
        errorOccurred(error);
    }
}

// From XValueCallback:
// void errorOccurred(@NotNull String errorMessage);
```

The result is an `XValue` -- the same type used for variables in the Variables view. This means you can:
- Get its string representation via `computePresentation()`
- Explore its children via `computeChildren()`
- Get its type, modify it, etc.

### Additional Capabilities

```java
// Does the evaluator support multi-line code fragments?
public boolean isCodeFragmentEvaluationSupported()  // default: true

// Find the evaluatable expression at a cursor position (for hover evaluation)
public @Nullable TextRange getExpressionRangeAtOffset(Project project, Document document,
                                                       int offset, boolean sideEffectsAllowed)

// Async version
public @NotNull Promise<ExpressionInfo> getExpressionInfoAtOffsetAsync(...)

// Format text before showing in Evaluate dialog
public @NotNull String formatTextForEvaluation(@NotNull String text)

// Determine evaluation mode for given text
public EvaluationMode getEvaluationMode(@NotNull String text, int startOffset,
                                        int endOffset, @Nullable PsiFile psiFile)
```

## XDebuggerEditorsProvider

**Source:** [`../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/evaluation/XDebuggerEditorsProvider.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/evaluation/XDebuggerEditorsProvider.java)

The editors provider creates language-aware documents for expression editing (e.g., PHP syntax highlighting in the Evaluate dialog). Obtained from `XDebugProcess.getEditorsProvider()`.

```java
public abstract class XDebuggerEditorsProvider {
    // File type for the expression editor
    public abstract @NotNull FileType getFileType();

    // Create a document for an expression (with code completion, syntax highlighting, etc.)
    public @NotNull Document createDocument(@NotNull Project project,
                                            @NotNull XExpression expression,
                                            @Nullable XSourcePosition sourcePosition,
                                            @NotNull EvaluationMode mode)

    // Languages supported for evaluation
    public @NotNull Collection<Language> getSupportedLanguages(@NotNull Project project,
                                                               @Nullable XSourcePosition sourcePosition)

    // Create an XExpression from a document
    public @NotNull XExpression createExpression(@NotNull Project project, @NotNull Document document,
                                                  @Nullable Language language, @NotNull EvaluationMode mode)
}
```

For our MCP plugin, we generally don't need the editors provider since we're sending raw expression strings, not creating editor documents.

## XExpression

**Source:** [`../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/XExpression.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/XExpression.java)

```java
public interface XExpression {
    @NotNull String getExpression();
    @Nullable Language getLanguage();
    @Nullable String getCustomInfo();
    @NotNull EvaluationMode getMode();
}
```

Create one via `XDebuggerUtil`:

```java
XExpression expr = XDebuggerUtil.getInstance().createExpression(
    "$user->getName()",   // expression text
    phpLanguage,          // language (nullable)
    null,                 // custom info (nullable)
    EvaluationMode.EXPRESSION  // or CODE_FRAGMENT
);
```

## Practical Example: MCP "Evaluate" Tool

```java
public ToolResult handleEvaluate(Project project, String expression) {
    XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
    if (session == null) return error("No active debug session");
    if (!session.isSuspended()) return error("Not suspended");

    XStackFrame frame = session.getCurrentStackFrame();
    if (frame == null) return error("No stack frame available");

    XDebuggerEvaluator evaluator = frame.getEvaluator();
    if (evaluator == null) return error("Evaluator not available");

    CompletableFuture<XValue> future = new CompletableFuture<>();
    evaluator.evaluate(expression, new XDebuggerEvaluator.XEvaluationCallback() {
        @Override
        public void evaluated(@NotNull XValue result) {
            future.complete(result);
        }
        @Override
        public void errorOccurred(@NotNull String errorMessage) {
            future.completeExceptionally(new RuntimeException(errorMessage));
        }
    }, frame.getSourcePosition());

    XValue result = future.get(10, TimeUnit.SECONDS);

    // Now get the string representation
    CompletableFuture<String> presentation = new CompletableFuture<>();
    ApplicationManager.getApplication().invokeLater(() -> {
        result.computePresentation(new XValueNode() {
            @Override
            public void setPresentation(@Nullable Icon icon, @Nullable String type,
                                        @NotNull String value, boolean hasChildren) {
                String display = type != null ? "(" + type + ") " + value : value;
                presentation.complete(display);
            }
            // ... other required methods
            @Override public boolean isObsolete() { return false; }
        }, XValuePlace.TREE);
    });

    return success(presentation.get(5, TimeUnit.SECONDS));
}
```

## XValueModifier: Setting Variable Values

**Source:** [`../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/frame/XValueModifier.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/frame/XValueModifier.java)

If a variable supports modification (check `xValue.getModifier() != null`):

```java
XValueModifier modifier = xValue.getModifier();
XExpression newValue = XDebuggerUtil.getInstance().createExpression(
    "42", null, null, EvaluationMode.EXPRESSION);

modifier.setValue(newValue, new XValueModifier.XModificationCallback() {
    @Override
    public void valueModified() {
        // Success -- variable now has the new value
    }
    @Override
    public void errorOccurred(@NotNull String errorMessage) {
        // Modification failed
    }
});
```

> **Relevant for MCP:** Expression evaluation maps to an MCP tool like `debugger/evaluate`. This is arguably the most powerful tool for an AI agent -- it can inspect any variable, call any function, check conditions, all in the live debug context. The callback pattern requires the same CompletableFuture bridging as variable reading. Consider supporting both `EXPRESSION` mode (single expressions) and `CODE_FRAGMENT` mode (multiple statements) to give the agent maximum flexibility.
