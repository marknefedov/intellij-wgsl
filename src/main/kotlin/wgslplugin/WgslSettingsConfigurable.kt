package wgslplugin

import com.google.gson.JsonParseException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import javax.swing.JComponent

class WgslSettingsConfigurable(private val project: Project) : SearchableConfigurable {
    private var ui: SettingsPanel? = null

    override fun getId() = "wgsl.languageServer"
    override fun getDisplayName() = WgslBundle.message("settings.displayName")
    override fun createComponent(): JComponent {
        disposeUIResources()
        return SettingsPanel(project).also { ui = it; it.reset(WgslSettings.getInstance(project).options) }.component
    }

    override fun isModified(): Boolean = ui?.let { it.options() != WgslSettings.getInstance(project).options } ?: false

    override fun apply() {
        val options = ui?.options() ?: return
        try {
            WgslConfiguration.parse(options.configuration)
        } catch (exception: JsonParseException) {
            throw ConfigurationException(WgslBundle.message("settings.invalidConfiguration", exception.message.orEmpty()))
        } catch (exception: IllegalArgumentException) {
            throw ConfigurationException(WgslBundle.message("settings.invalidConfiguration", exception.message.orEmpty()))
        }
        if (options.enabled && !options.managed && options.executable.isBlank()) {
            throw ConfigurationException(WgslBundle.message("settings.missingExecutable"))
        }
        val settings = WgslSettings.getInstance(project)
        if (settings.options == options) return
        settings.update(options)
        // Apply completes before clients restart; discard the callback if the project closes.
        ApplicationManager.getApplication().invokeLater({
            LspClientManager.getInstance(project).stopAndRestartClientsIfNeeded(WgslLspIntegrationProvider::class.java)
        }, project.disposed)
    }

    override fun reset() { ui?.reset(WgslSettings.getInstance(project).options) }
    override fun disposeUIResources() { ui?.executable?.dispose(); ui = null }

    private class SettingsPanel(project: Project) {
        private val enabled = JBCheckBox(WgslBundle.message("settings.enabled"))
        private val mode = ComboBox(arrayOf(
            WgslBundle.message("settings.managed", WgslServerInstaller.VERSION),
            WgslBundle.message("settings.custom"),
        ))
        val executable = TextFieldWithBrowseButton()
        private val configuration = JBTextArea(14, 70).apply { font = JBUI.Fonts.create("Monospaced", 13) }

        val component = panel {
            row { cell(enabled) }
            row(WgslBundle.message("settings.server")) { cell(mode).align(Align.FILL) }
            row(WgslBundle.message("settings.executable")) { cell(executable).align(Align.FILL) }
            row { comment(WgslBundle.message("settings.downloadHint")) }
            row { comment(WgslBundle.message("settings.restartHint")) }
            row { label(WgslBundle.message("settings.configuration")) }
            row { cell(JBScrollPane(configuration)).align(Align.FILL) }.resizableRow()
        }

        init {
            executable.addBrowseFolderListener(project, FileChooserDescriptor(true, false, false, false, false, false)
                .withTitle(WgslBundle.message("settings.chooseExecutable")))
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
