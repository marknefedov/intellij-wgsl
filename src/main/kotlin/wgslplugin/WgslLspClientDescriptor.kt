package wgslplugin

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.ProjectWideLspClientDescriptor
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspFormattingSupport
import org.eclipse.lsp4j.ConfigurationItem
import java.io.IOException

class WgslLspClientDescriptor(project: Project) : ProjectWideLspClientDescriptor(project, "wgsl-analyzer") {
    override val lspCustomization = object : LspCustomization() {
        override val formattingCustomizer = object : LspFormattingSupport() {
            override fun shouldFormatThisFileExclusivelyByServer(
                file: VirtualFile, ideCanFormatThisFileItself: Boolean, serverExplicitlyWantsToFormatThisFile: Boolean,
            ) = isShaderFile(file)
        }
    }

    override fun isSupportedFile(file: VirtualFile) = isShaderFile(file)
    override fun getLanguageId(file: VirtualFile) = if (file.extension.equals("wesl", ignoreCase = true)) "wesl" else "wgsl"

    override fun createCommandLine(): GeneralCommandLine {
        val settings = WgslSettings.getInstance(project).options
        val executable = if (settings.managed) {
            try {
                WgslManagedServer.executable().toString()
            } catch (exception: IOException) {
                throw ExecutionException(
                    "Cannot prepare wgsl-analyzer: ${exception.message}. Retry from the Language Services widget, " +
                        "or choose a custom executable in WGSL / WESL settings.", exception,
                )
            }
        } else settings.executable.trim().ifBlank { "wgsl-analyzer" }
        // wgsl-analyzer speaks LSP over stdio by default; there is no --stdio switch.
        return GeneralCommandLine(executable).withCharset(Charsets.UTF_8).withWorkDirectory(project.basePath)
    }

    override fun createInitializationOptions(): Any = WgslConfiguration.parse(WgslSettings.getInstance(project).options.configuration)
    override fun getWorkspaceConfiguration(item: ConfigurationItem): Any? =
        WgslConfiguration.section(WgslConfiguration.parse(WgslSettings.getInstance(project).options.configuration), item.section)

    companion object {
        fun isShaderFile(file: VirtualFile): Boolean = !file.isDirectory && file.isInLocalFileSystem &&
            (file.extension.equals("wgsl", ignoreCase = true) || file.extension.equals("wesl", ignoreCase = true))
    }
}
