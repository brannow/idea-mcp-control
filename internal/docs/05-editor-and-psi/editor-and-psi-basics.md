# Editor, Document, PSI, and VirtualFile Basics

> Source: `../../reference-Repository/intellij-community/platform/`
>
> Relevant packages:
> - `platform/core-api/src/com/intellij/openapi/editor/Document.java`
> - `platform/editor-ui-api/src/com/intellij/openapi/editor/Editor.java`
> - `platform/core-api/src/com/intellij/psi/PsiFile.java`
> - `platform/core-api/src/com/intellij/psi/PsiElement.java`
> - `platform/core-api/src/com/intellij/psi/PsiManager.java`
> - `platform/core-api/src/com/intellij/psi/PsiDocumentManager.java`
> - `platform/core-api/src/com/intellij/openapi/vfs/VirtualFile.java`

This doc covers how to programmatically read source code inside the IDE. The primary use case: when a debugger breakpoint hits, the AI agent needs to see the surrounding code and understand its structure.

## The Three Layers

IntelliJ has three distinct representations of a file's content:

```
VirtualFile          -- file system abstraction (path, bytes, metadata)
    |
Document             -- text content in memory (line-oriented, mutable)
    |
PsiFile / PsiElement -- parsed syntax tree (language-aware structure)
```

Each layer has a different purpose. For our debugger MCP tools, we typically need all three:
- **VirtualFile** to identify *which* file a breakpoint is in
- **Document** to read raw text lines around a position
- **PsiFile/PsiElement** to find the method/class containing the breakpoint

## VirtualFile

`com.intellij.openapi.vfs.VirtualFile` is the IDE's file system abstraction. It wraps physical files, in-memory files, JARs, etc.

Key facts:
- Equal `VirtualFile` instances represent the same file for the entire IDE lifetime (until deleted).
- Check `isValid()` before use -- files can be deleted externally.
- `LightVirtualFile` exists for in-memory/temporary files.

### Getting a VirtualFile

```kotlin
// From a file path
val vf = LocalFileSystem.getInstance().findFileByPath("/path/to/file.php")

// From a PsiFile
val vf = psiFile.virtualFile  // may be null for in-memory files
// Safer: psiFile.viewProvider.virtualFile (never null)

// From a Document
val vf = FileDocumentManager.getInstance().getFile(document)
```

### Key Methods

```kotlin
vf.path          // full path (String)
vf.name          // file name only
vf.extension     // file extension
vf.isValid       // still exists?
vf.isDirectory   // is it a directory?
vf.contentsToByteArray()  // raw bytes
vf.inputStream   // InputStream for reading
```

## Document

`com.intellij.openapi.editor.Document` represents the text content of a file loaded into memory. Line breaks are normalized to `\n`.

### Getting a Document

```kotlin
// From a VirtualFile
val doc = FileDocumentManager.getInstance().getDocument(virtualFile)

// From an Editor
val doc = editor.document

// From a PsiFile
val doc = PsiDocumentManager.getInstance(project).getDocument(psiFile)
```

### Reading Lines Around a Position

This is the core operation for "show me the code around the breakpoint":

```kotlin
fun getCodeAroundLine(document: Document, line: Int, contextLines: Int = 5): String {
    val startLine = maxOf(0, line - contextLines)
    val endLine = minOf(document.lineCount - 1, line + contextLines)

    val startOffset = document.getLineStartOffset(startLine)
    val endOffset = document.getLineEndOffset(endLine)

    return document.getText(TextRange(startOffset, endOffset))
}
```

### Key Methods

```kotlin
doc.text                     // full text (String copy -- avoid for large files)
doc.charsSequence            // CharSequence view (no copy, but mutable)
doc.immutableCharSequence    // immutable snapshot (thread-safe, no read action needed)
doc.textLength               // length
doc.lineCount                // number of lines

// Line <-> offset conversion (0-based lines)
doc.getLineNumber(offset)        // offset -> line number
doc.getLineStartOffset(line)     // line -> start offset
doc.getLineEndOffset(line)       // line -> end offset (before \n)

// Substring
doc.getText(TextRange(start, end))
```

**Threading**: Reading a Document requires a read action (`ApplicationManager.getApplication().runReadAction { ... }`). The `immutableCharSequence` is an exception -- it's safe without a read action.

## PsiFile and PsiElement

The PSI (Program Structure Interface) is the parsed syntax tree. Unlike Document (plain text), PSI understands language structure: classes, methods, statements, expressions.

### Getting a PsiFile

```kotlin
// From a VirtualFile
val psiFile = PsiManager.getInstance(project).findFile(virtualFile)

// From a Document
val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
```

### Finding the Element at a Position

Given an offset (e.g., from a breakpoint), find the PSI element at that location:

```kotlin
// Find the leaf element at an offset
val element = psiFile.findElementAt(offset)  // may return whitespace, token, etc.
```

`findElementAt` returns the most specific (leaf) element. To find a containing structure (method, class), walk up:

```kotlin
// Walk up to find a parent of a specific type
import com.intellij.psi.util.PsiTreeUtil

val method = PsiTreeUtil.getParentOfType(element, PhpMethod::class.java)
val clazz = PsiTreeUtil.getParentOfType(element, PhpClass::class.java)
```

`PsiTreeUtil` (from `com.intellij.psi.util`) has many useful traversal methods:
- `getParentOfType(element, type)` -- walk up to find nearest ancestor of type
- `findChildOfType(element, type)` -- find first child of type
- `getChildrenOfType(element, type)` -- all children of type
- `collectElementsOfType(element, type)` -- recursively find all descendants of type

### Key PsiElement Methods

```kotlin
element.text                // text of this element
element.textRange           // TextRange in the document
element.textOffset          // offset in the file
element.textLength          // length of text
element.parent              // parent element
element.children            // child elements (composites only, not leaves)
element.containingFile      // the PsiFile
element.language            // the Language
element.firstChild / lastChild
element.nextSibling / prevSibling
element.findElementAt(relativeOffset) // find leaf at offset relative to this element
```

## From Breakpoint to Code: The Full Flow

When a breakpoint hits and the AI agent needs context, here's the chain:

```kotlin
fun getBreakpointContext(project: Project, filePath: String, line: Int): BreakpointContext {
    return ReadAction.compute<BreakpointContext, Throwable> {
        // 1. Get VirtualFile
        val vf = LocalFileSystem.getInstance().findFileByPath(filePath)
            ?: throw IllegalArgumentException("File not found: $filePath")

        // 2. Get Document (for raw text)
        val doc = FileDocumentManager.getInstance().getDocument(vf)
            ?: throw IllegalStateException("No document for $filePath")

        // 3. Get surrounding text (e.g., 10 lines before/after)
        val startLine = maxOf(0, line - 10)
        val endLine = minOf(doc.lineCount - 1, line + 10)
        val codeSnippet = doc.getText(TextRange(
            doc.getLineStartOffset(startLine),
            doc.getLineEndOffset(endLine)
        ))

        // 4. Get PsiFile (for structural info)
        val psiFile = PsiManager.getInstance(project).findFile(vf)

        // 5. Find the element at the breakpoint line
        val offset = doc.getLineStartOffset(line)
        val element = psiFile?.findElementAt(offset)

        // 6. Walk up to find containing method/function
        val method = element?.let {
            PsiTreeUtil.getParentOfType(it, /* PhpMethod::class.java or similar */)
        }

        BreakpointContext(
            filePath = filePath,
            line = line,
            codeSnippet = codeSnippet,
            methodName = (method as? PsiNamedElement)?.name,
            // ... more structured info
        )
    }
}
```

### Important Notes

1. **Read Action required**: All PSI and Document access must happen inside `ReadAction.compute { }` or `ApplicationManager.getApplication().runReadAction { }`. This is IntelliJ's threading model -- the read lock ensures the PSI tree is consistent.

2. **0-based vs 1-based lines**: `Document.getLineNumber()` and `getLineStartOffset()` use 0-based line numbers. Xdebug and most debugger UIs use 1-based. Remember to convert: `documentLine = xdebugLine - 1`.

3. **PHP-specific PSI**: For PhpStorm, the PHP PSI types live in the `com.jetbrains.php.lang.psi` package. Key types:
   - `PhpFile` -- PHP file
   - `PhpClass` -- class/interface/trait/enum
   - `Method` / `Function` -- methods and functions
   - `Statement`, `PhpExpression` -- code constructs

4. **PsiDocumentManager.commitDocument()**: If you've modified a Document and need the PSI to reflect changes, call `PsiDocumentManager.getInstance(project).commitDocument(doc)` first. For read-only access (our primary case), this isn't needed as long as `isCommitted()` returns true.

5. **Smart pointers**: If you need to hold a reference to a PsiElement across document modifications, use `SmartPointerManager.createSmartPsiElementPointer(element)` instead of holding the element directly. Direct references become invalid after edits.

## Conversion Cheat Sheet

| From | To | How |
|------|----|-----|
| VirtualFile | Document | `FileDocumentManager.getInstance().getDocument(vf)` |
| VirtualFile | PsiFile | `PsiManager.getInstance(project).findFile(vf)` |
| Document | VirtualFile | `FileDocumentManager.getInstance().getFile(doc)` |
| Document | PsiFile | `PsiDocumentManager.getInstance(project).getPsiFile(doc)` |
| PsiFile | Document | `PsiDocumentManager.getInstance(project).getDocument(psiFile)` |
| PsiFile | VirtualFile | `psiFile.virtualFile` or `psiFile.viewProvider.virtualFile` |
| Editor | Document | `editor.document` |
| Editor | VirtualFile | `editor.virtualFile` |
| Editor | PsiFile | `PsiDocumentManager.getInstance(project).getPsiFile(editor.document)` |
| Offset | Line | `document.getLineNumber(offset)` |
| Line | Offset | `document.getLineStartOffset(line)` |
| Offset | PsiElement | `psiFile.findElementAt(offset)` |
