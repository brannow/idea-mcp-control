package com.github.brannow.phpstormmcp.server

import io.ktor.server.application.serverConfig
import io.ktor.server.cio.CIO
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.ServerSocket

/**
 * Drives the real ktor CIO engine into a start failure and checks what we would show the user.
 *
 * This exists because `verifyPlugin` cannot: it checks bytecode against the platform API and never
 * loads a class or runs the bundled ktor/coroutines stack — it reported 0.7.0 as compatible with a
 * build the server could not start on. The exception *shape* on a failed start is decided entirely
 * by the bundled ktor and kotlinx-coroutines versions, so it is a runtime fact, and the only place
 * it can be pinned without launching an IDE is here.
 *
 * If a future ktor bump changes how the failure is wrapped, this test is what notices.
 *
 * Observed on ktor 3.5.0 / kotlinx-coroutines 1.11, occupying the port and starting the engine:
 *
 *     JobCancellationException("LazyStandaloneCoroutine is cancelling")
 *       -> JobCancellationException("LazyStandaloneCoroutine is cancelling")
 *         -> BindException("Address already in use")
 *
 * That top line, verbatim, is what a field report of this bug contained — which is the whole
 * lesson: the exotic-looking coroutine message is what an ordinary busy port looks like here.
 */
class CioStartFailureTest {

    @Test
    fun `a failed bind is reported as a port conflict with a usable message`() {
        // Hold a port so the engine cannot have it. CIO leaves reuseAddress off, so this really
        // does fail the bind rather than quietly sharing the socket.
        ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { occupied ->
            val failure = startAndCaptureFailure(occupied.localPort)

            assertNotNull(failure, "binding an occupied port should not have succeeded")
            failure!!

            // The contract the user sees. `describeFailure` has to dig the BindException out from
            // under however many coroutine wrappers ktor happens to put on it this version.
            assertTrue(
                isPortConflict(failure),
                "expected a port conflict, chain was: ${causeChain(failure).map { it.javaClass.name }}"
            )
            val described = describeFailure(failure)
            assertTrue(
                described.startsWith("BindException"),
                "the message must lead with the real cause, got: $described"
            )
            // The whole point of the change: never lead with coroutine bookkeeping.
            assertFalse(
                described.startsWith("JobCancellationException"),
                "the wrapper must not be what we show, got: $described"
            )
        }
    }

    /** The start sequence from [McpServerService.start], reduced to the part that can fail. */
    private fun startAndCaptureFailure(port: Int): Throwable? = runBlocking {
        val handler = CoroutineExceptionHandler { _, _ -> /* swallow: asserted via the return */ }
        val config = serverConfig {
            parentCoroutineContext = handler
            module { }
        }
        val server = embeddedServer(CIO, config) {
            connector {
                host = "127.0.0.1"
                this.port = port
            }
        }
        try {
            server.start(wait = false)
            withTimeoutOrNull(10_000) { server.engine.resolvedConnectors() }
                ?: IllegalStateException("engine never reported a bound port")
            null
        } catch (throwable: Throwable) {
            throwable
        } finally {
            runCatching { server.stop(0, 0) }
        }
    }
}
