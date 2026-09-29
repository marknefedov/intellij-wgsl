package wgslplugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.ProjectManager
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.util.io.HttpRequests
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Keeps the managed server on the latest GitHub release without delaying server startup once a release is cached. */
object WgslManagedServer {
    private val LOG = logger<WgslManagedServer>()
    private val CHECK_INTERVAL = TimeUnit.HOURS.toMillis(1)
    private val lastCheck = AtomicLong(Long.MIN_VALUE / 2)
    private val cache: Path get() = PathManager.getSystemDir().resolve("intellij-wgsl").resolve("wgsl-analyzer")

    /** Called while the language server starts, off the EDT. Downloads the latest release only when nothing is cached. */
    fun executable(): Path {
        val asset = WgslServerInstaller.currentAsset()
        WgslServerInstaller.installed(cache, asset)?.let { installation ->
            checkForUpdate(asset, installation.tag)
            return installation.executable
        }
        lastCheck.set(System.currentTimeMillis())
        return WgslServerInstaller.install(cache, asset, latestRelease(), ::download)
    }

    // Server restarts, including the one after an update, and additional projects reuse a recent check.
    private fun checkForUpdate(asset: WgslServerInstaller.Asset, installedTag: String) {
        val now = System.currentTimeMillis()
        val last = lastCheck.get()
        if (now - last < CHECK_INTERVAL || !lastCheck.compareAndSet(last, now)) return
        ApplicationManager.getApplication().invokeLater {
            ProgressManager.getInstance().run(object : Task.Backgroundable(null, "Checking for wgsl-analyzer updates", true) {
                override fun run(indicator: ProgressIndicator) {
                    val release = try {
                        latestRelease()
                    } catch (exception: IOException) {
                        LOG.info("Cannot check for wgsl-analyzer updates", exception)
                        return
                    }
                    if (release.tag == installedTag) return
                    try {
                        WgslServerInstaller.install(cache, asset, release, ::download)
                    } catch (exception: IOException) {
                        LOG.warn("Cannot update wgsl-analyzer to ${release.tag}", exception)
                        return
                    }
                    LOG.info("Updated wgsl-analyzer from $installedTag to ${release.tag}")
                    restartManagedServers()
                }
            })
        }
    }

    private fun restartManagedServers() = ApplicationManager.getApplication().invokeLater {
        for (project in ProjectManager.getInstance().openProjects) {
            if (project.isDisposed) continue
            val options = WgslSettings.getInstance(project).options
            if (options.enabled && options.managed) {
                LspClientManager.getInstance(project).stopAndRestartClientsIfNeeded(WgslLspIntegrationProvider::class.java)
            }
        }
    }

    private fun latestRelease(): WgslServerInstaller.Release {
        ProgressManager.getInstance().progressIndicator?.text = "Checking for the latest wgsl-analyzer release"
        val json = HttpRequests.request(WgslServerInstaller.LATEST_RELEASE_URL)
            .accept("application/vnd.github+json")
            .tuner { it.setRequestProperty("X-GitHub-Api-Version", "2022-11-28") }
            .connectTimeout(10_000).readTimeout(15_000)
            .readString()
        return WgslServerInstaller.parseRelease(json)
    }

    private fun download(url: String, destination: Path) {
        ProgressManager.getInstance().progressIndicator?.text = "Downloading ${url.substringAfterLast("/releases/download/")}"
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
