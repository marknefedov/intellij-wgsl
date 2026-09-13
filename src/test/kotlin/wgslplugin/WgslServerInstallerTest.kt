package wgslplugin

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// All paths in this test are local TemporaryFolder fixtures, never remote Eel paths.
@Suppress("UseOptimizedEelFunctions")
class WgslServerInstallerTest {
    @get:Rule val temporary: TemporaryFolder = TemporaryFolder()

    private fun fixture(zip: Boolean = false): Pair<Path, WgslServerInstaller.Asset> {
        val binary = temporary.newFile().toPath().also { Files.writeString(it, "verified executable") }
        val archive = temporary.newFile().toPath()
        if (zip) {
            ZipOutputStream(Files.newOutputStream(archive)).use {
                it.putNextEntry(ZipEntry("wgsl-analyzer.exe"))
                it.write(Files.readAllBytes(binary))
                it.closeEntry()
                it.putNextEntry(ZipEntry("wgsl_analyzer.pdb"))
                it.write(byteArrayOf(1, 2, 3))
                it.closeEntry()
            }
        } else {
            GZIPOutputStream(Files.newOutputStream(archive)).use { it.write(Files.readAllBytes(binary)) }
        }
        return archive to WgslServerInstaller.Asset("test-target", "server.${if (zip) "zip" else "gz"}",
            WgslServerInstaller.sha256(archive), WgslServerInstaller.sha256(binary))
    }

    @Test fun `verified cache is reused offline and corruption triggers reinstall`() {
        val (archive, asset) = fixture()
        val cache = temporary.newFolder().toPath()
        val calls = AtomicInteger()
        val download: (String, Path) -> Unit = { _, path -> calls.incrementAndGet(); Files.copy(archive, path, REPLACE_EXISTING) }
        val executable = WgslServerInstaller.install(cache, asset, download)
        assertEquals("verified executable", Files.readString(executable))
        assertEquals(executable, WgslServerInstaller.install(cache, asset) { _, _ -> fail("Cache must work offline") })
        Files.writeString(executable, "corrupted")
        WgslServerInstaller.install(cache, asset, download)
        assertEquals(2, calls.get())
        assertEquals(asset.binarySha256, WgslServerInstaller.sha256(executable))
    }

    @Test fun `failed checksum does not install or leave partial files`() {
        val (archive, asset) = fixture()
        val cache = temporary.newFolder().toPath()
        assertThrows(IOException::class.java) {
            WgslServerInstaller.install(cache, asset.copy(archiveSha256 = "bad")) { _, path -> Files.copy(archive, path, REPLACE_EXISTING) }
        }
        Files.walk(cache).use { files ->
            assertEquals(listOf("install.lock"), files.filter { Files.isRegularFile(it) }.map { it.fileName.toString() }.toList())
        }
    }

    @Test fun `binary checksum is checked independently of archive checksum`() {
        val (archive, asset) = fixture()
        assertThrows(IOException::class.java) {
            WgslServerInstaller.install(temporary.newFolder().toPath(), asset.copy(binarySha256 = "bad")) { _, path ->
                Files.copy(archive, path, REPLACE_EXISTING)
            }
        }
    }

    @Test fun `windows archives may contain debug symbols but only executable is installed`() {
        val (archive, asset) = fixture(zip = true)
        val executable = WgslServerInstaller.install(temporary.newFolder().toPath(), asset) { _, path -> Files.copy(archive, path, REPLACE_EXISTING) }
        assertEquals("wgsl-analyzer.exe", executable.fileName.toString())
        assertEquals("verified executable", Files.readString(executable))
        assertFalse(Files.exists(executable.resolveSibling("wgsl_analyzer.pdb")))
    }

    @Test fun `archive paths cannot escape the cache`() {
        val archive = temporary.newFile().toPath()
        ZipOutputStream(Files.newOutputStream(archive)).use {
            it.putNextEntry(ZipEntry("../wgsl-analyzer.exe"))
            it.write(byteArrayOf(1))
            it.closeEntry()
        }
        assertThrows(IOException::class.java) { WgslServerInstaller.extract(archive, temporary.newFile().toPath(), true) }
    }

    @Test fun `concurrent projects download once`() {
        val (archive, asset) = fixture()
        val cache = temporary.newFolder().toPath()
        val calls = AtomicInteger()
        val executor = Executors.newFixedThreadPool(4)
        try {
            val requests = (1..4).map {
                executor.submit<Path> {
                    WgslServerInstaller.install(cache, asset) { _, path ->
                        calls.incrementAndGet()
                        Files.copy(archive, path, REPLACE_EXISTING)
                    }
                }
            }
            assertEquals(1, requests.map { it.get(10, TimeUnit.SECONDS) }.toSet().size)
            assertEquals(1, calls.get())
        } finally { executor.shutdownNow() }
    }

    @Test fun `platform selection supports aliases and rejects unsupported platforms`() {
        assertEquals("x86_64-pc-windows-msvc", WgslServerInstaller.assetFor("Windows 11", "amd64").target)
        assertEquals("aarch64-pc-windows-msvc", WgslServerInstaller.assetFor("Windows 11", "aarch64").target)
        assertEquals("x86_64-unknown-linux-musl", WgslServerInstaller.assetFor("Linux", "x86_64").target)
        assertEquals("aarch64-unknown-linux-gnu", WgslServerInstaller.assetFor("Linux", "arm64").target)
        assertEquals("aarch64-apple-darwin", WgslServerInstaller.assetFor("Mac OS X", "aarch64").target)
        assertThrows(IOException::class.java) { WgslServerInstaller.assetFor("Mac OS X", "x86_64") }
        assertThrows(IOException::class.java) { WgslServerInstaller.assetFor("Linux", "riscv64") }
    }
}
