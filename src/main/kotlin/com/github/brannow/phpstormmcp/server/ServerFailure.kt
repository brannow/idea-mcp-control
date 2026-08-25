package com.github.brannow.phpstormmcp.server

import java.net.BindException

/**
 * How many links of a `cause` chain we are willing to follow.
 *
 * Not paranoia about depth — the guard that matters is the identity set below. A cause chain can
 * be self-referential (`e.initCause(e)` is legal, and some wrappers rebuild a chain that loops),
 * and every caller here runs on the EDT. An unguarded `while (cause != null)` would freeze the IDE
 * outright. The cap is the cheap second line of defence for a chain that is merely absurdly long.
 */
private const val MAX_CAUSE_DEPTH = 16

/** How many wrapper class names [describeFailure] is willing to name before it truncates. */
private const val MAX_WRAPPERS_NAMED = 3

/**
 * The `cause` chain of [throwable], outermost first, with cycles and runaway depth cut off.
 * Always contains at least [throwable] itself.
 */
internal fun causeChain(throwable: Throwable): List<Throwable> {
    val chain = ArrayList<Throwable>(4)
    val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
    var current: Throwable? = throwable
    while (current != null && chain.size < MAX_CAUSE_DEPTH && seen.add(current)) {
        chain.add(current)
        current = current.cause
    }
    return chain
}

/**
 * One line naming what actually went wrong, for the activity log and the error notification.
 *
 * **Why this is not just `throwable.javaClass.simpleName + message`** (which is what it used to
 * be): the throwable that escapes a failed ktor start is usually a wrapper that says nothing.
 * ktor's CIO engine runs as a lazily started coroutine (`CIOApplicationEngine.initServerJob()`,
 * a `LazyStandaloneCoroutine`). When a child of it — the accept job doing the actual `bind()` —
 * dies, the engine coroutine is cancelled while suspended in `serverSocket.await()`, and
 * kotlinx-coroutines hands that suspension point `parent.getCancellationException()`
 * (`CancellableContinuationImpl.getContinuationCancellationCause`). `JobSupport` builds that as
 * `rootCause.toCancellationException("$classSimpleName is cancelling")` — i.e.
 * `JobCancellationException("LazyStandaloneCoroutine is cancelling", cause = <the real error>)`.
 *
 * So the top-level exception carries a message about coroutine bookkeeping and the real failure
 * sits one link down. A field report of exactly that string, with nothing else to go on, is what
 * this function exists to prevent: report the innermost cause, and name the wrappers only so the
 * reader knows the full trace in idea.log has more.
 */
internal fun describeFailure(throwable: Throwable): String {
    val chain = causeChain(throwable)
    val root = chain.last()
    val rootText = describeSingle(root)
    if (chain.size == 1) return rootText

    // The wrappers, outermost first, excluding the root we already named.
    val wrappers = chain.dropLast(1).map { it.javaClass.simpleName }
    val named = wrappers.take(MAX_WRAPPERS_NAMED).joinToString(" <- ")
    val omitted = wrappers.size - MAX_WRAPPERS_NAMED
    val suffix = if (omitted > 0) "$named, +$omitted more" else named
    return "$rootText (via $suffix)"
}

private fun describeSingle(throwable: Throwable): String {
    val message = throwable.message?.takeIf { it.isNotBlank() }
    return if (message != null) "${throwable.javaClass.simpleName}: $message"
    else throwable.javaClass.simpleName
}

/**
 * 0.7.0 reported every start failure as "port already in use", which sent the one real report we
 * got down a dead end — the port was fine, a bundled-library mismatch was killing the engine. Only
 * claim a port conflict when a [BindException] is actually somewhere in the chain.
 */
internal fun isPortConflict(throwable: Throwable): Boolean =
    causeChain(throwable).any { it is BindException }
