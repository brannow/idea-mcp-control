# Stack Frames and Variables

## The Data Model

When the debugger is paused, the state is organized as a hierarchy:

```
XSuspendContext          (the "paused" state -- may contain multiple threads)
  |
  +-- XExecutionStack    (one per thread -- e.g., "Main Thread", "Worker #3")
  |     |
  |     +-- XStackFrame  (one per call in the stack -- e.g., "foo() at line 42")
  |     |     |
  |     |     +-- XValue / XNamedValue  (variables visible in this frame)
  |     |           |
  |     |           +-- XValue  (child properties/fields, recursively)
  |     |
  |     +-- XStackFrame  (next frame down...)
  |
  +-- XExecutionStack    (another thread...)
```

For PHP/Xdebug, there's typically one execution stack (PHP is single-threaded), but the API is general.

## XSuspendContext

**Source:** [`../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/frame/XSuspendContext.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/frame/XSuspendContext.java)

Represents the entire suspended state. Obtained from `session.getSuspendContext()`.

```java
public abstract class XSuspendContext {

    // The "active" thread -- selected in the Frames panel
    public @Nullable XExecutionStack getActiveExecutionStack()

    // All threads (synchronous)
    public XExecutionStack @NotNull [] getExecutionStacks()

    // All threads (async -- for when thread enumeration is slow)
    public void computeExecutionStacks(XExecutionStackContainer container)
}
```

The `XExecutionStackContainer` callback interface:
```java
public interface XExecutionStackContainer extends XValueCallback, Obsolescent {
    void addExecutionStack(@NotNull List<? extends XExecutionStack> executionStacks, boolean last);
}
```

## XExecutionStack

**Source:** [`../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/frame/XExecutionStack.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/frame/XExecutionStack.java)

Represents a thread's call stack.

```java
public abstract class XExecutionStack {

    public final @NotNull String getDisplayName()   // Thread name
    public final @Nullable Icon getIcon()

    // Get the top frame synchronously
    public abstract @Nullable XStackFrame getTopFrame();

    // Get all frames asynchronously
    // firstFrameIndex: 1 = just below top frame (top frame is returned by getTopFrame())
    // Called on EDT -- must return quickly
    public abstract void computeStackFrames(int firstFrameIndex, XStackFrameContainer container);
}
```

The `XStackFrameContainer` callback:
```java
public interface XStackFrameContainer extends Obsolescent, XValueCallback {
    void addStackFrames(@NotNull List<? extends XStackFrame> stackFrames, boolean last);
}
```

## XStackFrame

**Source:** [`../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/frame/XStackFrame.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/frame/XStackFrame.java)

Represents a single frame in the call stack. Extends `XValueContainer`.

```java
public abstract class XStackFrame extends XValueContainer {

    // Source location of this frame
    public @Nullable XSourcePosition getSourcePosition()

    // Evaluator for this frame's context (expressions, watches, conditions)
    public @Nullable XDebuggerEvaluator getEvaluator()

    // Identity for detecting if frame changed after stepping
    public @Nullable Object getEqualityObject()

    // How to render this frame in the Frames list
    public void customizePresentation(@NotNull ColoredTextContainer component)
}
```

The `computeChildren()` method (inherited from `XValueContainer`) is how variables are loaded:

```java
// From XValueContainer:
public void computeChildren(@NotNull XCompositeNode node) {
    node.addChildren(XValueChildrenList.EMPTY, true);
}
```

## XValue and XNamedValue

**Source:** [`../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/frame/XValue.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/frame/XValue.java)

`XNamedValue` extends `XValue` with a name. Most debugger variables are `XNamedValue`.

```java
public abstract class XNamedValue extends XValue {
    protected XNamedValue(@NotNull String name)
    public final @NotNull String getName()
}

public abstract class XValue extends XValueContainer {

    // Start computing the string representation
    // Called on EDT, must return quickly, reports via callback
    public abstract void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place);

    // Expression that evaluates to this value (for "Copy as Expression")
    public @Nullable String getEvaluationExpression()

    // Modifier for "Set Value" functionality
    public @Nullable XValueModifier getModifier()

    // Source navigation
    public void computeSourcePosition(@NotNull XNavigatable navigatable)
    public boolean canNavigateToSource()
}
```

### XValueNode -- The Presentation Callback

**Source:** [`../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/frame/XValueNode.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/frame/XValueNode.java)

When you call `computePresentation()`, the value reports back via `XValueNode`:

```java
public interface XValueNode extends Obsolescent {
    int MAX_VALUE_LENGTH = 1000;

    void setPresentation(@Nullable Icon icon,
                         @Nullable String type,     // e.g., "string", "int", "App\\User"
                         @NotNull String value,      // e.g., "\"hello\"", "42"
                         boolean hasChildren);       // true = expandable (object/array)

    void setPresentation(@Nullable Icon icon,
                         @NotNull XValuePresentation presentation,
                         boolean hasChildren);

    // For values exceeding MAX_VALUE_LENGTH
    void setFullValueEvaluator(@NotNull XFullValueEvaluator fullValueEvaluator);
}
```

### XValueCallback -- Error Reporting

**Source:** [`../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/frame/XValueCallback.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/frame/XValueCallback.java)

```java
public interface XValueCallback {
    void errorOccurred(@NotNull String errorMessage);
}
```

Used by `XCompositeNode`, `XStackFrameContainer`, `XExecutionStackContainer` to report errors.

### XCompositeNode -- Children Callback

**Source:** [`../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/frame/XCompositeNode.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/frame/XCompositeNode.java)

```java
public interface XCompositeNode extends Obsolescent {
    int MAX_CHILDREN_TO_SHOW = 100;

    void addChildren(@NotNull XValueChildrenList children, boolean last);
    void tooManyChildren(int remaining, @NotNull Runnable addNextChildren);
    void setAlreadySorted(boolean alreadySorted);
    void setErrorMessage(@NotNull String errorMessage);
}
```

### XValueChildrenList

**Source:** [`../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/frame/XValueChildrenList.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/frame/XValueChildrenList.java)

A list of name-value pairs:

```java
public class XValueChildrenList {
    public static final XValueChildrenList EMPTY = ...;

    public void add(String name, @NotNull XValue value)
    public void add(@NotNull XNamedValue value)
    public int size()
    public String getName(int i)
    public XValue getValue(int i)

    // Grouping
    public void addTopGroup(@NotNull XValueGroup group)
    public void addBottomGroup(@NotNull XValueGroup group)
}
```

### XValueModifier

**Source:** [`../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/frame/XValueModifier.java`](../../reference-Repository/intellij-community/platform/xdebugger-api/src/com/intellij/xdebugger/frame/XValueModifier.java)

For "Set Value" functionality:

```java
public abstract class XValueModifier {
    public void setValue(@NotNull XExpression expression, @NotNull XModificationCallback callback)
    public @Nullable String getInitialValueEditorText()

    public interface XModificationCallback extends XValueCallback {
        void valueModified();
    }
}
```

## The Async Challenge

Almost every data-reading operation in the XDebugger framework is **callback-based**:

1. `computeChildren(XCompositeNode node)` -- node receives children via `addChildren()`
2. `computePresentation(XValueNode node, ...)` -- node receives type/value via `setPresentation()`
3. `computeStackFrames(int, XStackFrameContainer)` -- container receives frames via `addStackFrames()`

These are all called on the EDT and must return quickly. The actual data loading happens asynchronously, and results are delivered via callbacks.

For our MCP plugin, we need to bridge this callback-based API to synchronous MCP responses. Pattern:

```java
// Collect stack frames from an execution stack
CompletableFuture<List<XStackFrame>> future = new CompletableFuture<>();
ApplicationManager.getApplication().invokeLater(() -> {
    List<XStackFrame> collected = new ArrayList<>();
    stack.computeStackFrames(0, new XExecutionStack.XStackFrameContainer() {
        @Override
        public void addStackFrames(@NotNull List<? extends XStackFrame> frames, boolean last) {
            collected.addAll(frames);
            if (last) future.complete(collected);
        }
        @Override
        public void errorOccurred(@NotNull String errorMessage) {
            future.completeExceptionally(new RuntimeException(errorMessage));
        }
        @Override
        public boolean isObsolete() { return false; }
    });
});
List<XStackFrame> frames = future.get(5, TimeUnit.SECONDS);
```

Same pattern applies for reading variable values:

```java
// Read a variable's presentation
CompletableFuture<String[]> future = new CompletableFuture<>();
ApplicationManager.getApplication().invokeLater(() -> {
    value.computePresentation(new XValueNode() {
        @Override
        public void setPresentation(@Nullable Icon icon, @Nullable String type,
                                    @NotNull String value, boolean hasChildren) {
            future.complete(new String[]{type, value, String.valueOf(hasChildren)});
        }
        // ... implement other methods
        @Override public boolean isObsolete() { return false; }
    }, XValuePlace.TREE);
});
String[] result = future.get(5, TimeUnit.SECONDS);
```

## Traversing the Full State

Putting it all together to read the complete debug state:

```java
XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
if (session == null || !session.isSuspended()) return;

XSuspendContext ctx = session.getSuspendContext();
XExecutionStack activeStack = ctx.getActiveExecutionStack();

// Top frame is available synchronously
XStackFrame topFrame = activeStack.getTopFrame();
XSourcePosition pos = topFrame.getSourcePosition();
// pos.getFile(), pos.getLine()

// The current frame selected in the UI (may differ from top)
XStackFrame currentFrame = session.getCurrentStackFrame();

// Variables in the current frame: use computeChildren() async pattern above
```

> **Relevant for MCP:** This is the data pipeline for MCP resources like `debugger/stackTrace`, `debugger/variables`, `debugger/variableDetails`. The async callback pattern is the main engineering challenge -- we need to turn it into blocking calls with timeouts for MCP responses. Consider wrapping the common patterns (get frames, get children, get presentation) into utility methods that handle the EDT dispatch and future-based waiting.
