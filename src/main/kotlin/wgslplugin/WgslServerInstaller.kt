package wgslplugin

import java.io.IOException
import java.io.InputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest
import java.util.HexFormat
import java.util.Locale
import java.util.Properties
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

/** Installs only pinned, verified executables. Archive entry paths never become output paths. */
object WgslServerInstaller {
    const val VERSION = "2026-04-26"
    private const val MAX_BINARY_BYTES = 128L * 1024 * 1024

    data class Asset(val target: String, val name: String, val archiveSha256: String, val binarySha256: String) {
        val executableName: String get() = if (name.endsWith(".zip")) "wgsl-analyzer.exe" else "wgsl-analyzer"
        val url: String get() = "https://github.com/wgsl-analyzer/wgsl-analyzer/releases/download/$VERSION/$name"
    }

    fun currentAsset(): Asset = assetFor(System.getProperty("os.name"), System.getProperty("os.arch"))

    fun assetFor(os: String, architecture: String): Asset {
        val platform = os.lowercase(Locale.ROOT)
        val arch = architecture.lowercase(Locale.ROOT)
        val arm = arch == "aarch64" || arch == "arm64"
        val x64 = arch == "amd64" || arch == "x86_64"
        val windows = platform.startsWith("windows")
        val target = when {
            windows && (arm || x64) -> "${if (arm) "aarch64" else "x86_64"}-pc-windows-msvc"
            platform == "linux" && (arm || x64) -> if (arm) "aarch64-unknown-linux-gnu" else "x86_64-unknown-linux-musl"
            (platform.startsWith("mac") || platform == "darwin") && arm -> "aarch64-apple-darwin"
            else -> throw IOException("No managed wgsl-analyzer $VERSION binary for $os / $architecture. " +
                "Choose a custom executable in Settings | Languages & Frameworks | WGSL / WESL")
        }
        val manifest = Properties().apply {
            val stream = WgslServerInstaller::class.java.getResourceAsStream("/wgsl-server.properties")
                ?: throw IOException("Missing pinned server manifest")
            stream.use { load(it) }
        }
        return Asset(
            target, "wgsl-analyzer-$target.${if (windows) "zip" else "gz"}",
            manifest.getProperty("$target.archiveSha256") ?: throw IOException("Missing archive checksum for $target"),
            manifest.getProperty("$target.binarySha256") ?: throw IOException("Missing executable checksum for $target"),
        )
    }

    // JVM and file locks serialize installation across projects and IDE instances sharing a cache.
    @Synchronized
    fun install(cache: Path, asset: Asset, download: (String, Path) -> Unit): Path {
        val directory = cache.resolve(VERSION).resolve(asset.target)
        Files.createDirectories(directory)
        FileChannel.open(directory.resolve("install.lock"), CREATE, WRITE).use { channel ->
            channel.lock().use {
                val executable = directory.resolve(asset.executableName)
                if (Files.isRegularFile(executable) && sha256(executable) == asset.binarySha256) {
                    makeExecutable(executable)
                    return executable
                }
                val archive = Files.createTempFile(directory, "download-", ".tmp")
                val unpacked = Files.createTempFile(directory, "executable-", ".tmp")
                try {
                    download(asset.url, archive)
                    if (sha256(archive) != asset.archiveSha256) throw IOException("wgsl-analyzer download failed SHA-256 verification")
                    extract(archive, unpacked, asset.name.endsWith(".zip"))
                    if (sha256(unpacked) != asset.binarySha256) throw IOException("wgsl-analyzer executable failed SHA-256 verification")
                    makeExecutable(unpacked)
                    Files.move(unpacked, executable, ATOMIC_MOVE, REPLACE_EXISTING)
                    return executable
                } finally {
                    Files.deleteIfExists(archive)
                    Files.deleteIfExists(unpacked)
                }
            }
        }
    }

    private fun makeExecutable(path: Path) {
        if (!path.toFile().setExecutable(true, true) && !Files.isExecutable(path)) {
            throw IOException("Cannot make wgsl-analyzer executable: $path")
        }
    }

    internal fun extract(archive: Path, destination: Path, zip: Boolean) {
        if (!zip) {
            GZIPInputStream(Files.newInputStream(archive)).use { copyBounded(it, destination) }
            return
        }
        ZipInputStream(Files.newInputStream(archive)).use { input ->
            var found = false
            while (true) {
                val entry = input.nextEntry ?: break
                if (!entry.isDirectory && entry.name == "wgsl-analyzer.exe") {
                    if (found) throw IOException("Duplicate executable in archive")
                    copyBounded(input, destination)
                    found = true
                } else if (entry.name != "wgsl_analyzer.pdb") {
                    throw IOException("Unexpected entry in wgsl-analyzer archive: ${entry.name}")
                }
            }
            if (!found) throw IOException("No wgsl-analyzer.exe in archive")
        }
    }

    private fun copyBounded(input: InputStream, destination: Path) {
        Files.newOutputStream(destination).use { output ->
            val buffer = ByteArray(8192)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                total += count
                if (total > MAX_BINARY_BYTES) throw IOException("Server executable exceeds size limit")
                output.write(buffer, 0, count)
            }
        }
    }

    internal fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                digest.update(buffer, 0, count)
            }
        }
        return HexFormat.of().formatHex(digest.digest())
    }
}
