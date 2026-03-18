package com.github.brannow.phpstormmcp.statusbar

import com.github.brannow.phpstormmcp.McpServerStateListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArrayList

@Service(Service.Level.PROJECT)
class McpActivityLog(private val project: Project) {

    data class Entry(
        val timestamp: String,
        val message: String
    )

    private val entries = CopyOnWriteArrayList<Entry>()
    val recentEntries: List<Entry>
        get() = entries.toList()

    fun log(message: String) {
        val timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        entries.add(Entry(timestamp, message))
        while (entries.size > MAX_ENTRIES) {
            entries.removeAt(0)
        }
        notifyLogUpdated()
    }

    fun clear() {
        entries.clear()
        notifyLogUpdated()
    }

    private fun notifyLogUpdated() {
        project.messageBus.syncPublisher(McpServerStateListener.TOPIC).logUpdated()
    }

    companion object {
        private const val MAX_ENTRIES = 500

        fun getInstance(project: Project): McpActivityLog {
            return project.getService(McpActivityLog::class.java)
        }
    }
}
