package wgslplugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.*

class WgslSettingsConfigurable(private val project: Project) : SearchableConfigurable {
    private var ui: SettingsPanel? = null

    override fun getId() = "wgsl.languageServer"
    override fun getDisplayName() = "WGSL / WESL"
    override fun createComponent(): JComponent = SettingsPanel(project).also { ui = it; reset() }

    override fun isModified(): Boolean = ui?.let { it.options() != WgslSettings.getInstance(project).state } ?: false

    override fun apply() {
        val options = ui?.options() ?: return
        try {
            WgslConfiguration.parse(options.configuration)
        } catch (exception: RuntimeException) {
            throw ConfigurationException("Invalid server configuration: ${exception.message}")
        }
        if (!options.managed && options.executable.isBlank()) {
            throw ConfigurationException("Choose a custom executable or select the managed version.")
        }
        WgslSettings.getInstance(project).loadState(options)
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                LspClientManager.getInstance(project).stopAndRestartClientsIfNeeded(WgslLspIntegrationProvider::class.java)
            }
        }
    }

    override fun reset() { ui?.reset(WgslSettings.getInstance(project).state) }
    override fun disposeUIResources() { ui?.executable?.dispose(); ui = null }

    private class SettingsPanel(project: Project) : JPanel(BorderLayout(0, 12)) {
        private val enabled = JBCheckBox("Enable wgsl-analyzer")
        private val mode = JComboBox(arrayOf("Managed version (${WgslServerInstaller.VERSION})", "Custom executable"))
        val executable = TextFieldWithBrowseButton()
        private val configuration = JTextArea(14, 70)

        init {
            executable.addBrowseFolderListener(project, FileChooserDescriptor(true, false, false, false, false, false)
                .withTitle("Select wgsl-analyzer Executable"))
            val fields = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(enabled)
                add(JLabel("Server:"))
                add(mode)
                add(JLabel("Custom executable path or command on PATH:"))
                add(executable)
                add(JLabel("The managed version is downloaded from GitHub on first use and cached by the IDE."))
                add(JLabel("Changing settings restarts the language server. Highlighting works without it."))
            }
            add(fields, BorderLayout.NORTH)
            configuration.font = Font(Font.MONOSPACED, Font.PLAIN, configuration.font.size)
            add(JPanel(BorderLayout(0, 4)).apply {
                add(JLabel("wgsl-analyzer configuration (JSON object, without an outer wgsl-analyzer key):"), BorderLayout.NORTH)
                add(JBScrollPane(configuration), BorderLayout.CENTER)
            }, BorderLayout.CENTER)
            mode.addActionListener { updateEnabled() }
            enabled.addActionListener { updateEnabled() }
        }

        fun options() = WgslSettings.Options(enabled.isSelected, mode.selectedIndex == 0, executable.text.trim(), configuration.text.trim())

        fun reset(options: WgslSettings.Options) {
            enabled.isSelected = options.enabled
            mode.selectedIndex = if (options.managed) 0 else 1
            executable.text = options.executable
            configuration.text = options.configuration
            updateEnabled()
        }

        private fun updateEnabled() {
            mode.isEnabled = enabled.isSelected
            executable.isEnabled = enabled.isSelected && mode.selectedIndex == 1
            configuration.isEnabled = enabled.isSelected
        }
    }
}
