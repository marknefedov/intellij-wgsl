package wgslplugin

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Optional real-server smoke test: pass -PwgslServerArchive=/path/to/the/pinned/platform/archive. */
class WgslLiveServerTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `pinned server starts over stdio and completes struct fields`() {
        val archivePath = System.getProperty("wgsl.server.test.archive")
        assumeTrue("Supply wgslServerArchive to exercise a downloaded release", archivePath != null)
        val executable = WgslServerInstaller.install(temporary.newFolder("cache").toPath(), WgslServerInstaller.currentAsset()) { _, destination ->
            Files.copy(Path.of(archivePath), destination, REPLACE_EXISTING)
        }
        val root = temporary.newFolder("project").toPath()
        val source = "struct Material { roughness: f32, }\nfn main() { var material: Material; material. }\n"
        val file = root.resolve("shader.wgsl")
        Files.writeString(file, source)
        val process = ProcessBuilder(executable.toString()).directory(root.toFile())
            .redirectError(temporary.newFile("server.log")).start()
        val executor = Executors.newCachedThreadPool()
        val client = object : LanguageClient {
            override fun telemetryEvent(value: Any?) {}
            override fun publishDiagnostics(params: PublishDiagnosticsParams) {}
            override fun showMessage(params: MessageParams) {}
            override fun logMessage(params: MessageParams) {}
            override fun showMessageRequest(params: ShowMessageRequestParams) = CompletableFuture.completedFuture<MessageActionItem>(null)
            override fun configuration(params: ConfigurationParams): CompletableFuture<List<Any>> =
                CompletableFuture.completedFuture(params.items.map { emptyMap<String, Any>() })
        }
        val launcher = LSPLauncher.createClientLauncher(client, process.inputStream, process.outputStream, executor) { it }
        val listening = launcher.startListening()
        val server = launcher.remoteProxy
        try {
            val initialization = InitializeParams().apply {
                processId = ProcessHandle.current().pid().toInt()
                workspaceFolders = listOf(WorkspaceFolder(root.toUri().toString(), "test"))
                capabilities = ClientCapabilities()
                initializationOptions = emptyMap<String, Any>()
            }
            val result = server.initialize(initialization).get(30, TimeUnit.SECONDS)
            assertNotNull(result.capabilities.completionProvider)
            server.initialized(InitializedParams())
            server.textDocumentService.didOpen(DidOpenTextDocumentParams(TextDocumentItem(file.toUri().toString(), "wgsl", 1, source)))
            val character = source.lines()[1].indexOf("material.") + "material.".length
            val parameters = CompletionParams(TextDocumentIdentifier(file.toUri().toString()), Position(1, character))
            var labels: List<String>
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            // Initial workspace loading can briefly produce an empty completion response.
            do {
                val completion = server.textDocumentService.completion(parameters).get(10, TimeUnit.SECONDS)
                labels = (if (completion.isLeft) completion.left else completion.right.items).map { it.label }
                if (labels.any { it.contains("roughness") }) break
                Thread.sleep(100)
            } while (System.nanoTime() < deadline)
            assertTrue("Expected roughness completion, got $labels", labels.any { it.contains("roughness") })
            server.shutdown().get(10, TimeUnit.SECONDS)
            server.exit()
        } finally {
            listening.cancel(true)
            process.destroyForcibly()
            process.waitFor(5, TimeUnit.SECONDS)
            executor.shutdownNow()
        }
    }
}
