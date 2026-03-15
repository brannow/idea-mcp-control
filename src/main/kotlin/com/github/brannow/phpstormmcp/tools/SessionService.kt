package com.github.brannow.phpstormmcp.tools

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import java.util.concurrent.CompletableFuture

data class SessionInfo(
    val id: String,
    val name: String,
    val status: String,
    val currentFile: String? = null,
    val currentLine: Int? = null,
    val active: Boolean = false
)

@Service(Service.Level.PROJECT)
class SessionService(private val project: Project) {

    private fun getDebuggerManager() = XDebuggerManager.getInstance(project)

    private fun sessionId(session: XDebugSession): String =
        System.identityHashCode(session).toString()

    private fun sessionStatus(session: XDebugSession): String = when {
        session.isStopped -> "stopped"
        session.isSuspended -> "paused"
        else -> "running"
    }

    private fun toProjectRelativePath(absolutePath: String): String {
        val basePath = project.basePath ?: return absolutePath
        return if (absolutePath.startsWith(basePath)) {
            absolutePath.removePrefix(basePath).removePrefix("/")
        } else {
            absolutePath
        }
    }

    private fun toSessionInfo(session: XDebugSession, currentSession: XDebugSession?): SessionInfo {
        val position = session.currentPosition
        return SessionInfo(
            id = sessionId(session),
            name = session.sessionName,
            status = sessionStatus(session),
            currentFile = position?.file?.path?.let { toProjectRelativePath(it) },
            currentLine = position?.line?.let { it + 1 },
            active = session === currentSession
        )
    }

    fun listSessions(): List<SessionInfo> {
        val manager = getDebuggerManager()
        val currentSession = manager.currentSession
        return manager.debugSessions
            .filter { !it.isStopped }
            .map { toSessionInfo(it, currentSession) }
    }

    fun stopSession(sessionId: String): SessionInfo {
        val manager = getDebuggerManager()
        val session = findSessionById(sessionId)
            ?: throw IllegalArgumentException("Session not found: #$sessionId")

        val info = toSessionInfo(session, manager.currentSession)
        val future = CompletableFuture<Unit>()

        ApplicationManager.getApplication().invokeLater {
            session.stop()
            future.complete(Unit)
        }

        future.get()
        return info.copy(status = "stopped")
    }

    fun stopAllSessions(): List<SessionInfo> {
        val manager = getDebuggerManager()
        val currentSession = manager.currentSession
        val sessions = manager.debugSessions.filter { !it.isStopped }
        if (sessions.isEmpty()) return emptyList()

        val infos = sessions.map { toSessionInfo(it, currentSession) }
        val future = CompletableFuture<Unit>()

        ApplicationManager.getApplication().invokeLater {
            sessions.forEach { it.stop() }
            future.complete(Unit)
        }

        future.get()
        return infos.map { it.copy(status = "stopped") }
    }

    fun stopSmart(sessionId: String?): SessionInfo {
        if (sessionId != null) {
            return stopSession(sessionId)
        }

        val sessions = getDebuggerManager().debugSessions.filter { !it.isStopped }
        return when {
            sessions.isEmpty() -> throw IllegalStateException("No active debug sessions")
            sessions.size == 1 -> stopSession(sessionId(sessions.first()))
            else -> {
                val manager = getDebuggerManager()
                val currentSession = manager.currentSession
                val infos = sessions.map { toSessionInfo(it, currentSession) }
                throw AmbiguousSessionException(infos)
            }
        }
    }

    private fun findSessionById(id: String): XDebugSession? {
        val cleanId = id.trimStart('#').trim()
        return getDebuggerManager().debugSessions.firstOrNull {
            sessionId(it) == cleanId
        }
    }

    companion object {
        fun getInstance(project: Project): SessionService {
            return project.getService(SessionService::class.java)
        }
    }
}

class AmbiguousSessionException(
    val sessions: List<SessionInfo>
) : IllegalArgumentException()
