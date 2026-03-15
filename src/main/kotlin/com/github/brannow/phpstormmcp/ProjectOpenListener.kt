package com.github.brannow.phpstormmcp

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class ProjectOpenListener : ProjectActivity {

    override suspend fun execute(project: Project) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("PhpStorm MCP")
            .createNotification(
                "PhpStorm MCP",
                "Hello World! Plugin loaded for: ${project.name}",
                NotificationType.INFORMATION
            )
            .notify(project)
    }
}
