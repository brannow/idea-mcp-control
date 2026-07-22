package com.github.brannow.phpstormmcp.statusbar

import com.github.brannow.phpstormmcp.McpServerStateListener
import com.intellij.openapi.project.Project
import com.intellij.util.messages.MessageBus
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * These transitions are the contract the tool window renders from. The bug they guard against:
 * a start attempt that never binds used to leave the toolbar showing a running server (start
 * disabled, stop armed) while nothing was listening, because enablement came from
 * `McpServerService.server != null` instead of from this state.
 */
class McpServerStateTest {

    private fun newState(): McpServerState {
        val project = mockk<Project>(relaxed = true)
        every { project.getService(McpActivityLog::class.java) } returns mockk(relaxed = true)
        // syncPublisher is generic; a relaxed mock hands back Any and the call site's implicit
        // cast to the listener type fails, so stub it with a real typed mock.
        val bus = mockk<MessageBus>(relaxed = true)
        every { project.messageBus } returns bus
        every { bus.syncPublisher(McpServerStateListener.TOPIC) } returns
            mockk<McpServerStateListener>(relaxed = true)
        return McpServerState(project)
    }

    @Test
    fun `starts out stopped`() {
        val state = newState()
        assertEquals(McpServerState.Status.STOPPED, state.status)
        assertFalse(state.isRunning)
        assertNull(state.errorMessage)
    }

    @Test
    fun `starting is not running`() {
        val state = newState()
        state.starting(6969)

        assertEquals(McpServerState.Status.STARTING, state.status)
        // The whole point: an in-flight start must not read as running anywhere.
        assertFalse(state.isRunning)
        assertEquals("HTTP :6969", state.transport)
    }

    @Test
    fun `bound engine reports running on the bound port`() {
        val state = newState()
        state.starting(6969)
        state.start("HTTP :6969")

        assertEquals(McpServerState.Status.RUNNING, state.status)
        assertTrue(state.isRunning)
        assertNull(state.errorMessage)
    }

    @Test
    fun `failure is distinct from stopped and keeps the reason`() {
        val state = newState()
        state.starting(6969)
        state.failed("NoClassDefFoundError: kotlinx.coroutines.internal.CoroutineExceptionHandlerImplKt")

        assertEquals(McpServerState.Status.ERROR, state.status)
        assertFalse(state.isRunning)
        assertTrue(state.errorMessage!!.contains("NoClassDefFoundError"))
    }

    @Test
    fun `stop clears an error so the server can be started again`() {
        val state = newState()
        state.starting(6969)
        state.failed("boom")
        state.stop()

        assertEquals(McpServerState.Status.STOPPED, state.status)
        assertNull(state.errorMessage)
    }

    @Test
    fun `failure resets the client count`() {
        val state = newState()
        state.starting(6969)
        state.start("HTTP :6969")
        state.clientConnected()
        state.clientConnected()
        assertEquals(2, state.connectedClients)

        state.failed("boom")
        assertEquals(0, state.connectedClients)
    }

    @Test
    fun `client disconnect never goes negative`() {
        val state = newState()
        state.start("HTTP :6969")
        state.clientDisconnected()

        assertEquals(0, state.connectedClients)
    }
}
