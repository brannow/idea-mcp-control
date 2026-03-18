package com.github.brannow.phpstormmcp.toolwindow

import com.github.brannow.phpstormmcp.statusbar.McpIcons
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class McpToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val content = ContentFactory.getInstance().createContent(null, "", false)
        val panel = McpToolWindowPanel(project, toolWindow, content)
        content.component = panel
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }

    override fun init(toolWindow: ToolWindow) {
        toolWindow.stripeTitle = "MCP Hub"
        toolWindow.setIcon(McpIcons.StatusInactive)
    }
}
