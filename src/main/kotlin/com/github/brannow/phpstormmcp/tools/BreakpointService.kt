package com.github.brannow.phpstormmcp.tools

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointManager
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import com.jetbrains.php.PhpIndex
import java.util.concurrent.CompletableFuture

// -- Common base for all breakpoint info types --

sealed interface AnyBreakpointInfo {
    val id: String
    val enabled: Boolean
    val condition: String?
    val logExpression: String?
    val suspend: Boolean
}

data class BreakpointInfo(
    override val id: String,
    val file: String,
    val line: Int,
    override val enabled: Boolean,
    override val condition: String? = null,
    override val logExpression: String? = null,
    override val suspend: Boolean = true,
    val method: Boolean = false,
    val vendor: Boolean = false
) : AnyBreakpointInfo

data class ExceptionBreakpointInfo(
    override val id: String,
    val exceptionClass: String,
    override val enabled: Boolean,
    override val condition: String? = null,
    override val logExpression: String? = null,
    override val suspend: Boolean = true
) : AnyBreakpointInfo

data class PhpClassInfo(val shortName: String, val fqcn: String)

data class AddBreakpointResult(
    val breakpoint: BreakpointInfo,
    val existingBreakpoints: List<BreakpointInfo> = emptyList(),
    /** Original line the agent asked for, when the breakpoint was slid off a method-definition line. */
    val slidFrom: Int? = null
)

data class RemoveBreakpointsResult(
    val removed: List<AnyBreakpointInfo>,
    val notFound: List<String> = emptyList()
)

class AmbiguousBreakpointException(
    val breakpoints: List<BreakpointInfo>
) : IllegalArgumentException()

class BreakpointNotFoundException(
    val query: String
) : IllegalArgumentException("Breakpoint not found: $query")

class MultipleClassesFoundException(
    val query: String,
    val classes: List<PhpClassInfo>
) : IllegalArgumentException()

@Service(Service.Level.PROJECT)
class BreakpointService(private val project: Project) {

    internal interface Platform {
        fun getBreakpointManager(): XBreakpointManager
        fun findLineBreakpointType(): XLineBreakpointType<*>?
        fun resolveFile(path: String): VirtualFile?
        fun getLineCount(file: VirtualFile): Int
        fun isLibrary(file: VirtualFile): Boolean
        fun isMethodBreakpointLine(file: VirtualFile, line: Int): Boolean
        fun findMethodBreakpointType(): XLineBreakpointType<*>?
        fun createMethodBreakpointProperties(file: VirtualFile, line: Int): XBreakpointProperties<*>?
        fun canPutLineBreakpoint(file: VirtualFile, line: Int): Boolean
        fun findFirstExecutableLine(file: VirtualFile, fromLine: Int): Int?
        fun getLineText(file: VirtualFile, line: Int): String?
        fun <T> readAction(action: () -> T): T
        fun <T> runOnEdt(action: () -> T): T

        // Exception breakpoint support
        fun findExceptionBreakpointType(): XBreakpointType<*, *>?
        fun searchPhpClasses(input: String): List<PhpClassInfo>
        fun isExceptionClass(fqcn: String): Boolean
        fun getExceptionClassName(breakpoint: XBreakpoint<*>): String?
        fun addExceptionBreakpoint(fqcn: String): XBreakpoint<*>
    }

    internal var platform: Platform = object : Platform {
        override fun getBreakpointManager(): XBreakpointManager =
            XDebuggerManager.getInstance(project).breakpointManager

        override fun findLineBreakpointType(): XLineBreakpointType<*>? {
            // Exclude the method type — "php-method" also contains "php", and if it sorts first
            // we'd hand back the method type for ordinary line adds.
            return XDebuggerUtil.getInstance().lineBreakpointTypes.firstOrNull {
                it.id.contains("php", ignoreCase = true) && !it.id.contains("method", ignoreCase = true)
            }
        }

        override fun findMethodBreakpointType(): XLineBreakpointType<*>? {
            return XDebuggerUtil.getInstance().lineBreakpointTypes.firstOrNull {
                it.id.contains("php", ignoreCase = true) && it.id.contains("method", ignoreCase = true)
            }
        }

        override fun resolveFile(path: String): VirtualFile? {
            LocalFileSystem.getInstance().findFileByPath(path)?.let { return it }
            val basePath = project.basePath ?: return null
            val resolved = "$basePath/$path"
            return LocalFileSystem.getInstance().findFileByPath(resolved)
        }

        override fun getLineCount(file: VirtualFile): Int {
            val document = FileDocumentManager.getInstance().getDocument(file)
            return document?.lineCount ?: 0
        }

        override fun isLibrary(file: VirtualFile): Boolean {
            return ProjectFileIndex.getInstance(project).isInLibrary(file)
        }

        override fun isMethodBreakpointLine(file: VirtualFile, line: Int): Boolean {
            val methodType = findMethodBreakpointType() ?: return false
            return methodType.canPutAt(file, line, project)
        }

        /**
         * Build populated method-breakpoint properties for a method/function declaration line,
         * mirroring PhpStorm's gutter "Method" variant. The platform's
         * `createBreakpointProperties(file, line)` returns null for the PHP method type, and an
         * empty `PhpMethodBreakpointProperties` yields a nameless breakpoint that the PHP
         * registration handler silently drops (see `method-breakpoints.md`). The variant's
         * `createProperties()` is the only public API that resolves the class FQN + method name
         * from PSI, so we go through it. Must run inside a read action (PSI access). Returns null
         * when no method variant applies (e.g. trait/interface/anonymous-class methods).
         */
        @Suppress("UNCHECKED_CAST")
        override fun createMethodBreakpointProperties(file: VirtualFile, line: Int): XBreakpointProperties<*>? {
            val methodType = findMethodBreakpointType() as? XLineBreakpointType<XBreakpointProperties<*>>
                ?: return null
            val position = XDebuggerUtil.getInstance().createPosition(file, line) ?: return null
            val variant = methodType.computeVariants(project, position).firstOrNull() ?: return null
            return variant.createProperties()
        }

        /** Whether a normal line breakpoint is legal here — the same check PhpStorm's gutter uses. */
        override fun canPutLineBreakpoint(file: VirtualFile, line: Int): Boolean {
            val lineType = findLineBreakpointType() ?: return false
            return lineType.canPutAt(file, line, project)
        }

        /**
         * From a method/function definition line, find the first body line where a normal line
         * breakpoint sticks AND that carries a real statement — skipping the opening `{` (which
         * `canPutAt` accepts but is useless to break on) and blank lines. Returns a 0-based line.
         */
        override fun findFirstExecutableLine(file: VirtualFile, fromLine: Int): Int? {
            val lineType = findLineBreakpointType() ?: return null
            val methodType = findMethodBreakpointType()
            val doc = FileDocumentManager.getInstance().getDocument(file) ?: return null
            for (l in (fromLine + 1) until doc.lineCount) {
                val isMethodLine = methodType?.canPutAt(file, l, project) ?: false
                if (isMethodLine || !lineType.canPutAt(file, l, project)) continue
                val text = doc.getText(
                    com.intellij.openapi.util.TextRange(doc.getLineStartOffset(l), doc.getLineEndOffset(l))
                ).trim()
                if (text.isEmpty() || text == "{" || text == "}") continue
                return l
            }
            return null
        }

        override fun getLineText(file: VirtualFile, line: Int): String? {
            val doc = FileDocumentManager.getInstance().getDocument(file) ?: return null
            if (line < 0 || line >= doc.lineCount) return null
            val start = doc.getLineStartOffset(line)
            val end = doc.getLineEndOffset(line)
            return doc.getText(com.intellij.openapi.util.TextRange(start, end))
        }

        override fun <T> readAction(action: () -> T): T {
            return ReadAction.compute<T, Throwable> { action() }
        }

        override fun <T> runOnEdt(action: () -> T): T {
            val future = CompletableFuture<T>()
            ApplicationManager.getApplication().invokeLater {
                future.complete(action())
            }
            return future.get()
        }

        // -- Exception breakpoint support --

        override fun findExceptionBreakpointType(): XBreakpointType<*, *>? {
            return XBreakpointType.EXTENSION_POINT_NAME.extensionList
                .firstOrNull {
                    it.id.contains("php", ignoreCase = true) &&
                        it.id.contains("exception", ignoreCase = true)
                }
        }

        override fun searchPhpClasses(input: String): List<PhpClassInfo> {
            val phpIndex = PhpIndex.getInstance(project)

            if (input.contains("\\")) {
                val fqcn = if (input.startsWith("\\")) input else "\\$input"
                return phpIndex.getClassesByFQN(fqcn)
                    .filter { !it.isInterface }
                    .map { PhpClassInfo(it.name, normalizeFqcn(it.fqn ?: it.name)) }
                    .distinctBy { it.fqcn }
            }

            // Exact short name match
            val exact = phpIndex.getClassesByName(input)
                .filter { !it.isInterface }
                .map { PhpClassInfo(it.name, normalizeFqcn(it.fqn ?: it.name)) }
                .distinctBy { it.fqcn }
            if (exact.isNotEmpty()) return exact

            // Prefix fallback — search all class names and filter by prefix
            return phpIndex.getAllClassNames(null)
                .filter { it.startsWith(input, ignoreCase = true) }
                .take(50)
                .flatMap { name ->
                    phpIndex.getClassesByName(name)
                        .filter { !it.isInterface }
                        .map { PhpClassInfo(it.name, normalizeFqcn(it.fqn ?: it.name)) }
                }
                .distinctBy { it.fqcn }
        }

        override fun isExceptionClass(fqcn: String): Boolean {
            val phpIndex = PhpIndex.getInstance(project)
            val lookup = if (fqcn.startsWith("\\")) fqcn else "\\$fqcn"
            val classes = phpIndex.getClassesByFQN(lookup)
            return classes.any { phpClass ->
                var current: com.jetbrains.php.lang.psi.elements.PhpClass? = phpClass
                val visited = mutableSetOf<String>()
                while (current != null) {
                    val currentFqn = normalizeFqcn(current.fqn ?: break)
                    if (currentFqn == "Exception" || currentFqn == "Error") return@any true
                    if (!visited.add(currentFqn)) break
                    current = current.superClass
                }
                false
            }
        }

        override fun getExceptionClassName(breakpoint: XBreakpoint<*>): String? {
            // Strategy 1: reflection on properties (most reliable)
            val properties = breakpoint.properties
            if (properties != null) {
                val clazz = properties::class.java
                for (fieldName in EXCEPTION_FQCN_FIELDS) {
                    try {
                        val field = clazz.getDeclaredField(fieldName)
                        field.isAccessible = true
                        val value = field.get(properties)
                        if (value is String && value.isNotEmpty()) return value
                    } catch (_: Exception) {}
                }
            }

            // Strategy 2: display text from the breakpoint type
            try {
                @Suppress("UNCHECKED_CAST")
                val type = breakpoint.type as XBreakpointType<XBreakpoint<*>, *>
                val displayText = type.getDisplayText(breakpoint)
                if (!displayText.isNullOrBlank()) return displayText
            } catch (_: Exception) {}

            return null
        }

        override fun addExceptionBreakpoint(fqcn: String): XBreakpoint<*> {
            val type = findExceptionBreakpointType()
                ?: throw IllegalStateException("PHP exception breakpoint type not available")

            val properties = createExceptionProperties(type, fqcn)

            // Cast through XBreakpointProperties — NOT Nothing/Void which causes ClassCastException
            @Suppress("UNCHECKED_CAST")
            val typedType = type as XBreakpointType<XBreakpoint<XBreakpointProperties<*>>, XBreakpointProperties<*>>

            @Suppress("UNCHECKED_CAST")
            return getBreakpointManager().addBreakpoint(
                typedType,
                properties as? XBreakpointProperties<*>
            )
        }

        private fun createExceptionProperties(
            type: XBreakpointType<*, *>,
            fqcn: String
        ): XBreakpointProperties<*>? {
            @Suppress("UNCHECKED_CAST")
            val typedType = type as? XBreakpointType<*, XBreakpointProperties<*>> ?: return null
            val properties = typedType.createProperties() ?: return null

            val clazz = properties::class.java
            // Try known field names first
            for (fieldName in EXCEPTION_FQCN_FIELDS) {
                try {
                    val field = clazz.getDeclaredField(fieldName)
                    field.isAccessible = true
                    if (field.type == String::class.java) {
                        field.set(properties, fqcn)
                        return properties
                    }
                } catch (_: Exception) {}
            }

            // Fallback: find any empty String field
            for (field in clazz.declaredFields) {
                if (field.type == String::class.java) {
                    try {
                        field.isAccessible = true
                        val current = field.get(properties) as? String
                        if (current.isNullOrEmpty()) {
                            field.set(properties, fqcn)
                            return properties
                        }
                    } catch (_: Exception) {}
                }
            }

            return properties
        }
    }

    // ================================================================
    // Line breakpoints
    // ================================================================

    fun listBreakpoints(fileFilter: String? = null): List<BreakpointInfo> = platform.readAction {
        val manager = platform.getBreakpointManager()
        manager.allBreakpoints
            .filterIsInstance<XLineBreakpoint<*>>()
            .filter { !manager.isDefaultBreakpoint(it) }
            .mapNotNull { bp -> toBreakpointInfo(bp) }
            .filter { fileFilter == null || it.file.contains(fileFilter) }
    }

    fun addBreakpoint(
        file: String,
        line: Int,
        condition: String? = null,
        logExpression: String? = null,
        suspend: Boolean = true
    ): AddBreakpointResult {
        val resolved = platform.readAction {
            val lineType = platform.findLineBreakpointType()
                ?: throw IllegalStateException("PHP line breakpoint type not available. Is the PHP plugin active?")

            val virtualFile = platform.resolveFile(file)
                ?: throw IllegalArgumentException("File not found: $file")

            if (line < 1) {
                throw IllegalArgumentException("Line must be >= 1, got $line")
            }

            val lineCount = platform.getLineCount(virtualFile)
            if (lineCount > 0 && line > lineCount) {
                throw IllegalArgumentException("Line $line is beyond end of file ($file has $lineCount lines)")
            }

            // Decide the target line + breakpoint type, mirroring PhpStorm's gutter via canPutAt:
            //  - method/function definition line → create a real PHP method breakpoint
            //    (`php-line-method`) on the declaration line, with properties built from the
            //    gutter "Method" variant so the breakpoint is named (Class::method) and actually
            //    registers with Xdebug. See internal/docs/03-debugger-api/method-breakpoints.md.
            //    Fallback: if the variant can't produce properties (trait/interface/anon class),
            //    slide down to the body's first real statement and set a line breakpoint there.
            //  - otherwise → a line breakpoint, but only if canPutAt says it's a legal location.
            //    Skipping that check is what created ghost breakpoints on const/blank/comment lines.
            val zeroLine = line - 1
            var effectiveLine = line
            var slidFrom: Int? = null
            var resolvedType: XLineBreakpointType<*> = lineType
            var resolvedProps: XBreakpointProperties<*>? = null
            when {
                platform.isMethodBreakpointLine(virtualFile, zeroLine) -> {
                    val methodType = platform.findMethodBreakpointType()
                    val props = methodType?.let { platform.createMethodBreakpointProperties(virtualFile, zeroLine) }
                    if (methodType != null && props != null) {
                        // Real method breakpoint on the declaration line — no slide.
                        resolvedType = methodType
                        resolvedProps = props
                    } else {
                        val slid = platform.findFirstExecutableLine(virtualFile, zeroLine)
                            ?: throw IllegalArgumentException(
                                "Line $line in $file is a method/function definition with no executable " +
                                    "statement to break on. Add a method breakpoint via PhpStorm's gutter instead."
                            )
                        effectiveLine = slid + 1
                        slidFrom = line
                    }
                }
                platform.canPutLineBreakpoint(virtualFile, zeroLine) -> { /* use line as-is */ }
                else -> throw IllegalArgumentException(notPlaceableMessage(file, line, virtualFile, lineCount))
            }

            ResolvedAdd(resolvedType, resolvedProps, virtualFile, effectiveLine, slidFrom, findBreakpointsAtLine(virtualFile, effectiveLine - 1))
        }

        val fileUrl = resolved.file.url
        val zeroBasedLine = resolved.effectiveLine - 1

        val info = platform.runOnEdt {
            @Suppress("UNCHECKED_CAST")
            val bp = platform.getBreakpointManager().addLineBreakpoint(
                resolved.type as XLineBreakpointType<XBreakpointProperties<*>>,
                fileUrl,
                zeroBasedLine,
                resolved.properties
            )

            if (condition != null) {
                bp.setCondition(condition)
            }
            if (logExpression != null) {
                bp.setLogExpression(logExpression)
            }
            if (!suspend) {
                bp.suspendPolicy = SuspendPolicy.NONE
            }

            toBreakpointInfo(bp)!!
        }

        return AddBreakpointResult(
            breakpoint = info,
            existingBreakpoints = resolved.existing,
            slidFrom = resolved.slidFrom
        )
    }

    /**
     * Build a "not a breakpoint location" error and point the agent at the nearest line below
     * where a breakpoint *can* go, so it doesn't have to guess — the same `canPutAt` scan the gutter
     * uses. Kept terse: PhpStorm only rejects genuinely non-executable lines (blank, comment, const
     * declaration), so the message states the fact rather than enumerating a category that's often wrong.
     */
    private fun notPlaceableMessage(file: String, line: Int, virtualFile: VirtualFile, lineCount: Int): String {
        val suggestion = ((line until lineCount).firstOrNull { l ->
            platform.isMethodBreakpointLine(virtualFile, l) || platform.canPutLineBreakpoint(virtualFile, l)
        })?.let { it + 1 } // 0-based loop index → 1-based line
        val tail = if (suggestion != null) " Try line $suggestion." else ""
        return "$file:$line is not a breakpoint location — no executable code there.$tail"
    }

    /** Internal carrier for the resolved add target (after a possible method-line slide). */
    private data class ResolvedAdd(
        val type: XLineBreakpointType<*>,
        val properties: XBreakpointProperties<*>?,
        val file: VirtualFile,
        val effectiveLine: Int,
        val slidFrom: Int?,
        val existing: List<BreakpointInfo>
    )

    /**
     * Read a single source line (trimmed) for a breakpoint's location — used to show a
     * "proof of placement" excerpt next to newly added breakpoints. Null when unavailable.
     */
    fun getLineExcerpt(file: String, line: Int): String? = platform.readAction {
        val vf = platform.resolveFile(file) ?: return@readAction null
        platform.getLineText(vf, line - 1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    // ================================================================
    // Exception breakpoints
    // ================================================================

    fun listExceptionBreakpoints(): List<ExceptionBreakpointInfo> = platform.readAction {
        val manager = platform.getBreakpointManager()
        val exceptionType = platform.findExceptionBreakpointType() ?: return@readAction emptyList()

        manager.allBreakpoints
            .filter { it.type == exceptionType && !manager.isDefaultBreakpoint(it) }
            .mapNotNull { toExceptionBreakpointInfo(it) }
    }

    fun addExceptionBreakpoint(
        className: String,
        condition: String? = null,
        logExpression: String? = null,
        suspend: Boolean = true
    ): ExceptionBreakpointInfo {
        val fqcn = platform.readAction { resolveExceptionClass(className) }

        val info = platform.runOnEdt {
            val bp = platform.addExceptionBreakpoint(fqcn)

            if (condition != null) {
                bp.setCondition(condition)
            }
            if (logExpression != null) {
                bp.setLogExpression(logExpression)
            }
            if (!suspend) {
                bp.suspendPolicy = SuspendPolicy.NONE
            }

            toExceptionBreakpointInfo(bp)
                ?: throw IllegalStateException("Failed to read back exception breakpoint")
        }

        return info
    }

    private fun resolveExceptionClass(input: String): String {
        val existing = listExceptionBreakpoints()
        val existingByFqcn = existing.associateBy { it.exceptionClass }
        val allMatches = platform.searchPhpClasses(input)
        val exceptionMatches = allMatches.filter { platform.isExceptionClass(it.fqcn) }
        val available = exceptionMatches.filter { it.fqcn !in existingByFqcn }

        if (input.contains("\\")) {
            val normalized = normalizeFqcn(input)
            if (allMatches.isEmpty()) {
                throw IllegalArgumentException("Class not found: $normalized")
            }
            if (exceptionMatches.isEmpty()) {
                throw IllegalArgumentException("${allMatches.first().fqcn} is not an exception class")
            }
            if (available.isEmpty()) {
                val bp = existingByFqcn[exceptionMatches.first().fqcn]!!
                throw IllegalArgumentException("Exception breakpoint already exists: ${formatExceptionBreakpoint(bp)}")
            }
            return available.first().fqcn
        }

        // Short name / prefix search
        return when {
            exceptionMatches.isEmpty() && allMatches.isNotEmpty() ->
                throw IllegalArgumentException("'$input' matched ${allMatches.size} class(es) but none are exception classes")
            exceptionMatches.isEmpty() ->
                throw IllegalArgumentException("No PHP exception class found matching '$input'")
            available.isEmpty() -> {
                val covered = exceptionMatches.mapNotNull { existingByFqcn[it.fqcn] }
                val list = covered.joinToString("\n") { formatExceptionBreakpoint(it) }
                throw IllegalArgumentException("Already covered:\n$list")
            }
            available.size == 1 ->
                available.first().fqcn
            else ->
                throw MultipleClassesFoundException(input, available)
        }
    }

    // ================================================================
    // Update / Remove — work with both line and exception breakpoints
    // ================================================================

    fun updateBreakpoint(
        id: String,
        enabled: Boolean? = null,
        condition: String? = null,
        logExpression: String? = null,
        suspend: Boolean? = null
    ): AnyBreakpointInfo {
        val bp = platform.readAction { findAnyBreakpointById(id) }
            ?: throw BreakpointNotFoundException(id)

        return platform.runOnEdt {
            if (enabled != null) {
                bp.isEnabled = enabled
            }
            if (condition != null) {
                bp.setCondition(condition.ifEmpty { null })
            }
            if (logExpression != null) {
                bp.setLogExpression(logExpression.ifEmpty { null })
            }
            if (suspend != null) {
                bp.suspendPolicy = if (suspend) SuspendPolicy.ALL else SuspendPolicy.NONE
            }

            toAnyBreakpointInfo(bp)!!
        }
    }

    fun removeBreakpoints(ids: List<String>? = null): RemoveBreakpointsResult {
        val manager = platform.getBreakpointManager()

        if (ids.isNullOrEmpty()) {
            val (allBps, infos) = platform.readAction {
                val lineBps = manager.allBreakpoints
                    .filterIsInstance<XLineBreakpoint<*>>()
                    .filter { !manager.isDefaultBreakpoint(it) }

                val exceptionType = platform.findExceptionBreakpointType()
                val exceptionBps = if (exceptionType != null) {
                    manager.allBreakpoints
                        .filter { it.type == exceptionType && !manager.isDefaultBreakpoint(it) }
                } else emptyList()

                val lineInfos: List<AnyBreakpointInfo> = lineBps.mapNotNull { toBreakpointInfo(it) }
                val exceptionInfos: List<AnyBreakpointInfo> = exceptionBps.mapNotNull { toExceptionBreakpointInfo(it) }

                val allBps: List<XBreakpoint<*>> = lineBps + exceptionBps
                allBps to (lineInfos + exceptionInfos)
            }

            if (allBps.isNotEmpty()) {
                platform.runOnEdt { allBps.forEach { manager.removeBreakpoint(it) } }
            }

            return RemoveBreakpointsResult(removed = infos)
        }

        // Resolve each ID — can be numeric, file:line, file-only (purge), or FQCN
        val (toRemove, toRemoveInfos, notFound) = platform.readAction {
            val toRemove = mutableListOf<XBreakpoint<*>>()
            val toRemoveInfos = mutableListOf<AnyBreakpointInfo>()
            val notFound = mutableListOf<String>()

            for (id in ids) {
                val resolved = resolveForRemoval(id)
                if (resolved.isEmpty()) {
                    notFound.add(id)
                } else {
                    for (bp in resolved) {
                        if (bp !in toRemove) {
                            toRemove.add(bp)
                            toAnyBreakpointInfo(bp)?.let { toRemoveInfos.add(it) }
                        }
                    }
                }
            }
            Triple(toRemove, toRemoveInfos, notFound)
        }

        if (toRemove.isNotEmpty()) {
            platform.runOnEdt { toRemove.forEach { manager.removeBreakpoint(it) } }
        }

        return RemoveBreakpointsResult(removed = toRemoveInfos, notFound = notFound)
    }

    // ================================================================
    // Resolution helpers
    // ================================================================

    /**
     * Resolves an ID to breakpoints for removal. Supports:
     * - Numeric timestamp ID → any breakpoint (line or exception)
     * - FQCN (contains \) → exception breakpoint
     * - file:line → single line breakpoint (throws AmbiguousBreakpointException if multiple)
     * - file path (no line) → all line breakpoints in that file (purge)
     */
    private fun resolveForRemoval(id: String): List<XBreakpoint<*>> {
        val cleanId = sanitizeId(id)

        // Try as numeric timestamp — searches both line and exception breakpoints
        val timestamp = cleanId.toLongOrNull()
        if (timestamp != null) {
            val bp = platform.getBreakpointManager().allBreakpoints
                .firstOrNull { !platform.getBreakpointManager().isDefaultBreakpoint(it) && it.timeStamp == timestamp }
            return listOfNotNull(bp)
        }

        // Try as FQCN for exception breakpoints
        if (cleanId.contains("\\")) {
            val normalized = normalizeFqcn(cleanId)
            val exceptionType = platform.findExceptionBreakpointType()
            if (exceptionType != null) {
                val manager = platform.getBreakpointManager()
                val match = manager.allBreakpoints
                    .filter { it.type == exceptionType && !manager.isDefaultBreakpoint(it) }
                    .firstOrNull { normalizeFqcn(platform.getExceptionClassName(it) ?: "") == normalized }
                if (match != null) return listOf(match)
            }
            return emptyList()
        }

        // Try as file:line
        val colonIndex = cleanId.lastIndexOf(':')
        if (colonIndex > 0) {
            val filePart = cleanId.substring(0, colonIndex)
            val linePart = cleanId.substring(colonIndex + 1).toIntOrNull()
            if (linePart != null) {
                val virtualFile = platform.resolveFile(filePart) ?: return emptyList()
                val zeroBasedLine = linePart - 1
                val matches = platform.getBreakpointManager().allBreakpoints
                    .filterIsInstance<XLineBreakpoint<*>>()
                    .filter { it.sourcePosition?.file?.path == virtualFile.path && it.line == zeroBasedLine }

                if (matches.size > 1) {
                    throw AmbiguousBreakpointException(matches.mapNotNull { toBreakpointInfo(it) })
                }
                return matches
            }
        }

        // Try as exact file path — purge all line breakpoints in this file
        val virtualFile = platform.resolveFile(cleanId)
        if (virtualFile != null) {
            val manager = platform.getBreakpointManager()
            return manager.allBreakpoints
                .filterIsInstance<XLineBreakpoint<*>>()
                .filter { !manager.isDefaultBreakpoint(it) }
                .filter { it.sourcePosition?.file?.path == virtualFile.path }
        }

        // Fallback: substring match on file paths (same as breakpoint_list filter)
        val manager = platform.getBreakpointManager()
        return manager.allBreakpoints
            .filterIsInstance<XLineBreakpoint<*>>()
            .filter { !manager.isDefaultBreakpoint(it) }
            .filter { bp ->
                val path = bp.sourcePosition?.file?.path?.let { toProjectRelativePath(it) }
                path != null && path.contains(cleanId)
            }
    }

    private fun findBreakpointsAtLine(virtualFile: VirtualFile, zeroBasedLine: Int): List<BreakpointInfo> {
        val manager = platform.getBreakpointManager()
        return manager.allBreakpoints
            .filterIsInstance<XLineBreakpoint<*>>()
            .filter { !manager.isDefaultBreakpoint(it) }
            .filter { it.sourcePosition?.file?.path == virtualFile.path && it.line == zeroBasedLine }
            .mapNotNull { toBreakpointInfo(it) }
    }

    private fun sanitizeId(id: String): String = id.trimStart('#').trim()

    /**
     * Finds any breakpoint (line or exception) by ID.
     * - Numeric timestamp → searches all breakpoints
     * - file:line → searches only line breakpoints
     */
    private fun findAnyBreakpointById(id: String): XBreakpoint<*>? {
        val cleanId = sanitizeId(id)
        val timestamp = cleanId.toLongOrNull()
        if (timestamp != null) {
            return platform.getBreakpointManager().allBreakpoints
                .firstOrNull { !platform.getBreakpointManager().isDefaultBreakpoint(it) && it.timeStamp == timestamp }
        }

        // file:line → only line breakpoints
        val colonIndex = cleanId.lastIndexOf(':')
        if (colonIndex > 0) {
            val filePart = cleanId.substring(0, colonIndex)
            val linePart = cleanId.substring(colonIndex + 1).toIntOrNull() ?: return null
            val virtualFile = platform.resolveFile(filePart) ?: return null
            val zeroBasedLine = linePart - 1

            val matches = platform.getBreakpointManager().allBreakpoints
                .filterIsInstance<XLineBreakpoint<*>>()
                .filter { it.sourcePosition?.file?.path == virtualFile.path && it.line == zeroBasedLine }

            return when {
                matches.isEmpty() -> null
                matches.size == 1 -> matches.first()
                else -> throw AmbiguousBreakpointException(matches.mapNotNull { toBreakpointInfo(it) })
            }
        }

        return null
    }

    fun fileExists(path: String): Boolean = platform.readAction { platform.resolveFile(path) != null }

    // ================================================================
    // Info conversion
    // ================================================================

    private fun toProjectRelativePath(absolutePath: String): String {
        val basePath = project.basePath ?: return absolutePath
        return if (absolutePath.startsWith(basePath)) {
            absolutePath.removePrefix(basePath).removePrefix("/")
        } else {
            absolutePath
        }
    }

    private fun toBreakpointInfo(bp: XLineBreakpoint<*>): BreakpointInfo? {
        val pos = bp.sourcePosition ?: return null
        val relativePath = toProjectRelativePath(pos.file.path)
        return BreakpointInfo(
            id = bp.timeStamp.toString(),
            file = relativePath,
            line = bp.line + 1,
            enabled = bp.isEnabled,
            condition = bp.conditionExpression?.expression,
            logExpression = bp.logExpressionObject?.expression,
            suspend = bp.suspendPolicy != SuspendPolicy.NONE,
            method = bp.type.id.contains("method", ignoreCase = true),
            vendor = platform.isLibrary(pos.file)
        )
    }

    private fun toExceptionBreakpointInfo(bp: XBreakpoint<*>): ExceptionBreakpointInfo? {
        val className = platform.getExceptionClassName(bp) ?: return null
        return ExceptionBreakpointInfo(
            id = bp.timeStamp.toString(),
            exceptionClass = normalizeFqcn(className),
            enabled = bp.isEnabled,
            condition = bp.conditionExpression?.expression,
            logExpression = bp.logExpressionObject?.expression,
            suspend = bp.suspendPolicy != SuspendPolicy.NONE
        )
    }

    private fun toAnyBreakpointInfo(bp: XBreakpoint<*>): AnyBreakpointInfo? {
        if (bp is XLineBreakpoint<*>) return toBreakpointInfo(bp)
        return toExceptionBreakpointInfo(bp)
    }

    companion object {
        fun getInstance(project: Project): BreakpointService {
            return project.getService(BreakpointService::class.java)
        }

        /**
         * PhpStorm stores FQCNs without leading backslash (e.g. "Exception"),
         * but PhpIndex.getClassesByFQN() expects it. Normalize to without-slash
         * for storage and comparison, add it back only for PhpIndex lookups.
         */
        internal fun normalizeFqcn(fqcn: String): String = fqcn.trimStart('\\')

        // Field names commonly used by JetBrains exception breakpoint properties
        private val EXCEPTION_FQCN_FIELDS = listOf(
            "myQualifiedName", "myException", "myExceptionName",
            "exception", "qualifiedName", "exceptionName"
        )
    }
}
