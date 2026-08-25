package com.github.brannow.phpstormmcp.server

import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.BindException

/**
 * The bug these guard against: a field report that read, in full,
 * "MCP server failed to start: JobCancellationException: LazyStandaloneCoroutine is cancelling".
 * That is ktor's CIO engine coroutine being cancelled by whatever really failed — the real cause
 * is one link down, and the old describe() printed only the outermost throwable.
 */
class ServerFailureTest {

    @Test
    fun `a lone throwable is described as class and message`() {
        assertEquals(
            "BindException: Address already in use",
            describeFailure(BindException("Address already in use"))
        )
    }

    @Test
    fun `a throwable with no message is described by class alone`() {
        assertEquals("BindException", describeFailure(BindException()))
        assertEquals("BindException", describeFailure(BindException("   ")))
    }

    @Test
    fun `the root cause is reported, not the wrapper that hid it`() {
        val real = NoClassDefFoundError("io/ktor/network/sockets/SocketKt")
        val wrapper = CancellationException("LazyStandaloneCoroutine is cancelling", real)

        assertEquals(
            "NoClassDefFoundError: io/ktor/network/sockets/SocketKt (via CancellationException)",
            describeFailure(wrapper)
        )
    }

    @Test
    fun `every wrapper is named up to the cap`() {
        val root = IllegalStateException("boom")
        val chained = (1..5).fold<Int, Throwable>(root) { inner, _ -> RuntimeException("wrap", inner) }

        // 5 wrappers, 3 named, the rest counted so the line never implies it showed everything.
        assertEquals(
            "IllegalStateException: boom (via RuntimeException <- RuntimeException <- RuntimeException, +2 more)",
            describeFailure(chained)
        )
    }

    /** `initCause` refuses self-causation, so a cycle has to be built by overriding `cause`. */
    private class SelfCausedException : RuntimeException("loops") {
        override val cause: Throwable get() = this
    }

    @Test
    fun `a self-referential cause chain terminates`() {
        // The assertion is secondary; the point is that this returns at all rather than spinning
        // forever. Both describeFailure and isPortConflict run on the EDT.
        assertEquals("SelfCausedException: loops", describeFailure(SelfCausedException()))
        assertFalse(isPortConflict(SelfCausedException()))
    }

    @Test
    fun `a port conflict is still recognised through the wrapper`() {
        val wrapper = CancellationException(
            "LazyStandaloneCoroutine is cancelling",
            RuntimeException("engine start failed", BindException("Address already in use"))
        )
        assertTrue(isPortConflict(wrapper))
    }

    @Test
    fun `a non-bind failure is not reported as a port conflict`() {
        val wrapper = CancellationException(
            "LazyStandaloneCoroutine is cancelling",
            NoClassDefFoundError("io/ktor/network/sockets/SocketKt")
        )
        assertFalse(isPortConflict(wrapper))
    }
}
