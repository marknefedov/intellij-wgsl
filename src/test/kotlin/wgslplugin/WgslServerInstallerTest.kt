package wgslplugin

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
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

    private fun fixture(zip: Boolean = false, content: String = "verified executable"): Pair<Path, WgslServerInstaller.Asset> {
        val archive = temporary.newFile().toPath()
        if (zip) {
            ZipOutputStream(Files.newOutputStream(archive)).use {
                it.putNextEntry(ZipEntry("wgsl-analyzer.exe"))
                it.write(content.toByteArray())
                it.closeEntry()
                it.putNextEntry(ZipEntry("wgsl_analyzer.pdb"))
                it.write(byteArrayOf(1, 2, 3))
                it.closeEntry()
            }
        } else {
            GZIPOutputStream(Files.newOutputStream(archive)).use { it.write(content.toByteArray()) }
        }
        return archive to WgslServerInstaller.Asset("test-target", "server.${if (zip) "zip" else "gz"}")
    }

    private fun release(tag: String, asset: WgslServerInstaller.Asset, archive: Path) =
        WgslServerInstaller.Release(tag, mapOf(asset.name to WgslServerInstaller.sha256(archive)))

    private fun copy(archive: Path): (String, Path) -> Unit = { _, path -> Files.copy(archive, path, REPLACE_EXISTING) }

    @Test fun `verified cache is reused offline and corruption triggers reinstall`() {
        val (archive, asset) = fixture()
        val release = release("2026-09-29", asset, archive)
        val cache = temporary.newFolder().toPath()
        val calls = AtomicInteger()
        val download: (String, Path) -> Unit = { url, path ->
            assertEquals("https://github.com/marknefedov/wgsl-analyzer/releases/download/2026-09-29/server.gz", url)
            calls.incrementAndGet()
            Files.copy(archive, path, REPLACE_EXISTING)
        }
        assertNull(WgslServerInstaller.installed(cache, asset))
        val executable = WgslServerInstaller.install(cache, asset, release, null, download).executable
        assertEquals("verified executable", Files.readString(executable))
        assertEquals(WgslServerInstaller.Installation("2026-09-29", executable), WgslServerInstaller.installed(cache, asset))
        assertEquals(executable, WgslServerInstaller.install(cache, asset, release, "2026-09-29") { _, _ -> fail("Cache must work offline") }.executable)
        Files.writeString(executable, "corrupted")
        assertNull(WgslServerInstaller.installed(cache, asset))
        WgslServerInstaller.install(cache, asset, release, "2026-09-29", download)
        assertEquals(2, calls.get())
        assertEquals("verified executable", Files.readString(executable))
    }

    @Test fun `newer release replaces current and older releases are pruned`() {
        val cache = temporary.newFolder().toPath()
        val legacy = Files.createDirectories(cache.resolve("2026-09-13.1").resolve("test-target"))
        Files.writeString(legacy.resolve("wgsl-analyzer"), "pinned by an older plugin")
        var current: String? = null
        val installed = listOf("2026-09-13", "2026-09-24", "2026-09-29").map { tag ->
            val (archive, asset) = fixture(content = "server $tag")
            tag to WgslServerInstaller.install(cache, asset, release(tag, asset, archive), current, copy(archive)).executable.also { current = tag }
        }
        val asset = WgslServerInstaller.Asset("test-target", "server.gz")
        assertEquals(WgslServerInstaller.Installation("2026-09-29", installed.last().second), WgslServerInstaller.installed(cache, asset))
        assertEquals("server 2026-09-29", Files.readString(installed.last().second))
        // The previous release stays available to IDE instances that are starting it.
        assertTrue(Files.exists(installed[1].second))
        assertFalse(Files.exists(installed[0].second.parent))
        assertFalse(Files.exists(cache.resolve("2026-09-13.1")))
    }

    @Test fun `update resolved before a concurrent install does not downgrade the cache`() {
        val cache = temporary.newFolder().toPath()
        val (oldArchive, asset) = fixture(content = "server 2026-09-24")
        WgslServerInstaller.install(cache, asset, release("2026-09-24", asset, oldArchive), null, copy(oldArchive))
        // Instance A resolves 2026-09-29 while 2026-09-24 is current; instance B then installs 2026-10-01 first.
        val (staleArchive, _) = fixture(content = "server 2026-09-29")
        val stale = release("2026-09-29", asset, staleArchive)
        val (newArchive, _) = fixture(content = "server 2026-10-01")
        val newer = WgslServerInstaller.install(cache, asset, release("2026-10-01", asset, newArchive), "2026-09-24", copy(newArchive))
        val result = WgslServerInstaller.install(cache, asset, stale, "2026-09-24") { _, _ -> fail("Stale release must not be downloaded") }
        assertEquals(newer, result)
        assertEquals(newer, WgslServerInstaller.installed(cache, asset))
        assertEquals("server 2026-10-01", Files.readString(result.executable))
    }

    @Test fun `cached executable regains lost execute permission`() {
        val (archive, asset) = fixture()
        val cache = temporary.newFolder().toPath()
        val executable = WgslServerInstaller.install(cache, asset, release("2026-09-29", asset, archive), null, copy(archive)).executable
        assumeTrue("POSIX permissions", executable.toFile().setExecutable(false, false) && !Files.isExecutable(executable))
        assertEquals(executable, WgslServerInstaller.installed(cache, asset)?.executable)
        assertTrue(Files.isExecutable(executable))
    }

    @Test fun `failed checksum does not install or leave partial files`() {
        val (archive, asset) = fixture()
        val cache = temporary.newFolder().toPath()
        assertThrows(IOException::class.java) {
            WgslServerInstaller.install(cache, asset, WgslServerInstaller.Release("2026-09-29", mapOf(asset.name to "0".repeat(64))), null, copy(archive))
        }
        Files.walk(cache).use { files ->
            assertEquals(listOf("install.lock"), files.filter { Files.isRegularFile(it) }.map { it.fileName.toString() }.toList())
        }
        assertNull(WgslServerInstaller.installed(cache, asset))
    }

    @Test fun `failed update keeps the installed release`() {
        val (archive, asset) = fixture()
        val cache = temporary.newFolder().toPath()
        val executable = WgslServerInstaller.install(cache, asset, release("2026-09-24", asset, archive), null, copy(archive)).executable
        assertThrows(IOException::class.java) {
            WgslServerInstaller.install(cache, asset, WgslServerInstaller.Release("2026-09-29", mapOf(asset.name to "0".repeat(64))), "2026-09-24", copy(archive))
        }
        assertEquals(WgslServerInstaller.Installation("2026-09-24", executable), WgslServerInstaller.installed(cache, asset))
    }

    @Test fun `release without a digest for the platform cannot be installed`() {
        val (archive, asset) = fixture()
        assertThrows(IOException::class.java) {
            WgslServerInstaller.install(temporary.newFolder().toPath(), asset, WgslServerInstaller.Release("2026-09-29", emptyMap()), null) { _, _ ->
                fail("Unverifiable assets must not be downloaded")
            }
        }
        assertTrue(Files.exists(archive))
    }

    @Test fun `release metadata provides tag and asset digests`() {
        val release = WgslServerInstaller.parseRelease("""
            {"tag_name": "2026-09-29", "assets": [
              {"name": "wgsl-analyzer-aarch64-apple-darwin.gz", "digest": "sha256:93FF73F685030CCABD22179C5AD791D2C60099CEE0E1899C8E0B24B46C7EDE62"},
              {"name": "wgsl-analyzer-x86_64-unknown-linux-musl.gz", "digest": null},
              {"name": "wgsl-analyzer-x86_64-pc-windows-msvc.zip", "digest": "sha512:abc"},
              {"name": "wgsl-analyzer-no-server.vsix"}
            ]}
        """.trimIndent())
        assertEquals("2026-09-29", release.tag)
        assertEquals(mapOf("wgsl-analyzer-aarch64-apple-darwin.gz" to "93ff73f685030ccabd22179c5ad791d2c60099cee0e1899c8e0b24b46c7ede62"), release.digests)
        assertThrows(IOException::class.java) { WgslServerInstaller.parseRelease("""{"tag_name": "../escape", "assets": []}""") }
        assertThrows(IOException::class.java) { WgslServerInstaller.parseRelease("""{"message": "API rate limit exceeded"}""") }
        assertThrows(IOException::class.java) { WgslServerInstaller.parseRelease("<html>") }
    }

    @Test fun `windows archives may contain debug symbols but only executable is installed`() {
        val (archive, asset) = fixture(zip = true)
        val executable = WgslServerInstaller.install(temporary.newFolder().toPath(), asset, release("2026-09-29", asset, archive), null, copy(archive)).executable
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
        val release = release("2026-09-29", asset, archive)
        val cache = temporary.newFolder().toPath()
        val calls = AtomicInteger()
        val executor = Executors.newFixedThreadPool(4)
        try {
            val requests = (1..4).map {
                executor.submit<Path> {
                    WgslServerInstaller.install(cache, asset, release, null) { _, path ->
                        calls.incrementAndGet()
                        Files.copy(archive, path, REPLACE_EXISTING)
                    }.executable
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
