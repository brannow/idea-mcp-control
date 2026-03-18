package com.github.brannow.phpstormmcp.startup

import com.github.brannow.phpstormmcp.server.McpServerService
import com.github.brannow.phpstormmcp.settings.McpSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class McpAutoStartActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val settings = McpSettings.getInstance(project)
        if (settings.autoStart) {
            ApplicationManager.getApplication().invokeLater {
                McpServerService.getInstance(project).start()
            }
        }
    }
}
