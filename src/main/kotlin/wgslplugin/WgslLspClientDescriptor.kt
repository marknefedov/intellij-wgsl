package wgslplugin

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.ProjectWideLspClientDescriptor
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspFormattingSupport
import com.intellij.util.io.HttpRequests
import org.eclipse.lsp4j.ConfigurationItem
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

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
        val settings = WgslSettings.getInstance(project).state
        val executable = if (settings.managed) {
            try {
                WgslServerInstaller.install(
                    Path.of(PathManager.getSystemPath(), "wgsl-analyzer"),
                    WgslServerInstaller.currentAsset(), ::download,
                ).toString()
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

    override fun createInitializationOptions(): Any = WgslConfiguration.parse(WgslSettings.getInstance(project).state.configuration)
    override fun getWorkspaceConfiguration(item: ConfigurationItem): Any? =
        WgslConfiguration.section(WgslConfiguration.parse(WgslSettings.getInstance(project).state.configuration), item.section)

    companion object {
        fun isShaderFile(file: VirtualFile): Boolean = !file.isDirectory && file.isInLocalFileSystem &&
            (file.extension.equals("wgsl", ignoreCase = true) || file.extension.equals("wesl", ignoreCase = true))

        private fun download(url: String, destination: Path) {
            ProgressManager.getInstance().progressIndicator?.text = "Downloading wgsl-analyzer ${WgslServerInstaller.VERSION}"
            HttpRequests.request(url).connectTimeout(15_000).readTimeout(30_000).connect { request ->
                request.inputStream.use { input ->
                    Files.newOutputStream(destination).use { output ->
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            ProgressManager.checkCanceled()
                            val count = input.read(buffer)
                            if (count == -1) break
                            total += count
                            if (total > 32L * 1024 * 1024) throw IOException("Server download exceeds size limit")
                            output.write(buffer, 0, count)
                        }
                    }
                }
            }
        }
    }
}
