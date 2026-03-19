package com.github.brannow.phpstormmcp.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Settings page registered under Tools in PhpStorm's Settings dialog.
 * Constructor receives Project because this is a projectConfigurable.
 */
class McpSettingsConfigurable(private val project: Project) : Configurable {

    private var portField: JBTextField? = null
    private var autoStartCheckbox: JBCheckBox? = null
    private var panel: JPanel? = null

    override fun getDisplayName(): String = "MCP Control"

    override fun createComponent(): JComponent {
        val settings = McpSettings.getInstance(project)
        portField = JBTextField(settings.port.toString(), 8)
        autoStartCheckbox = JBCheckBox(
            "Start server automatically when project opens",
            settings.autoStart
        )

        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Port:"), portField!!, 1, false)
            .addComponent(autoStartCheckbox!!, 1)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        return panel!!
    }

    override fun getPreferredFocusedComponent(): JComponent? = portField

    override fun isModified(): Boolean {
        val settings = McpSettings.getInstance(project)
        val currentPort = portField?.text?.trim()?.toIntOrNull() ?: return false
        return currentPort != settings.port || autoStartCheckbox?.isSelected != settings.autoStart
    }

    override fun apply() {
        val settings = McpSettings.getInstance(project)
        val newPort = portField?.text?.trim()?.toIntOrNull()
        if (newPort != null && newPort in 1..65535) {
            settings.port = newPort
        }
        settings.autoStart = autoStartCheckbox?.isSelected ?: false
    }

    override fun reset() {
        val settings = McpSettings.getInstance(project)
        portField?.text = settings.port.toString()
        autoStartCheckbox?.isSelected = settings.autoStart
    }

    override fun disposeUIResources() {
        portField = null
        autoStartCheckbox = null
        panel = null
    }
}
