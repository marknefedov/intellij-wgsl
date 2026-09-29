package wgslplugin

import com.google.gson.JsonParser
import java.io.IOException
import java.io.InputStream
import java.io.UncheckedIOException
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
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

/**
 * Installs executables from GitHub releases, verified against the SHA-256 digests GitHub publishes for each asset.
 * Archive entry paths and release metadata never become output paths.
 */
object WgslServerInstaller {
    const val REPOSITORY = "marknefedov/wgsl-analyzer"
    const val LATEST_RELEASE_URL = "https://api.github.com/repos/$REPOSITORY/releases/latest"
    private const val MAX_BINARY_BYTES = 128L * 1024 * 1024
    private const val CURRENT = "current"
    private const val CHECKSUM = "executable.sha256"
    private val TAG = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
    private val SHA256 = Regex("[0-9a-f]{64}")
    // Older plugin versions pinned a release and cached it as <cache>/<tag>/<target>.
    private val LEGACY_PINNED = Regex("""\d{4}-\d{2}-\d{2}(\.\d+)?""")

    data class Asset(val target: String, val name: String) {
        val executableName: String get() = if (name.endsWith(".zip")) "wgsl-analyzer.exe" else "wgsl-analyzer"
    }

    /** A release tag and the archive digests of its assets, keyed by asset name. */
    data class Release(val tag: String, val digests: Map<String, String>) {
        init { if (!TAG.matches(tag)) throw IOException("Unexpected wgsl-analyzer release tag: $tag") }
        fun url(asset: Asset) = "https://github.com/$REPOSITORY/releases/download/$tag/${asset.name}"
        fun sha256(asset: Asset): String = digests[asset.name]
            ?: throw IOException("wgsl-analyzer release $tag has no verifiable ${asset.name} for ${asset.target}")
    }

    data class Installation(val tag: String, val executable: Path)

    fun currentAsset(): Asset = assetFor(System.getProperty("os.name"), System.getProperty("os.arch"), muslOnly())

    // The musl build needs the musl loader, which glibc distributions do not ship; the glibc build needs glibc 2.28+.
    private fun muslOnly(): Boolean =
        Files.exists(Path.of("/lib/ld-musl-x86_64.so.1")) && !Files.exists(Path.of("/lib64/ld-linux-x86-64.so.2"))

    fun assetFor(os: String, architecture: String, musl: Boolean = false): Asset {
        val platform = os.lowercase(Locale.ROOT)
        val arch = architecture.lowercase(Locale.ROOT)
        val arm = arch == "aarch64" || arch == "arm64"
        val x64 = arch == "amd64" || arch == "x86_64"
        val windows = platform.startsWith("windows")
        val target = when {
            windows && (arm || x64) -> "${if (arm) "aarch64" else "x86_64"}-pc-windows-msvc"
            platform == "linux" && (arm || x64) -> if (arm) "aarch64-unknown-linux-gnu" else "x86_64-unknown-linux-${if (musl) "musl" else "gnu"}"
            (platform.startsWith("mac") || platform == "darwin") && arm -> "aarch64-apple-darwin"
            else -> throw IOException("No managed wgsl-analyzer binary for $os / $architecture. " +
                "Choose a custom executable in Settings | Languages & Frameworks | WGSL / WESL")
        }
        return Asset(target, "wgsl-analyzer-$target.${if (windows) "zip" else "gz"}")
    }

    /** Parses a GitHub REST API release. Assets without a SHA-256 digest are omitted and cannot be installed. */
    fun parseRelease(json: String): Release {
        try {
            val release = JsonParser.parseString(json).asJsonObject
            val digests = release.getAsJsonArray("assets").map { it.asJsonObject }.mapNotNull { asset ->
                val digest = asset.get("digest")?.takeIf { it.isJsonPrimitive }?.asString?.lowercase(Locale.ROOT)
                    ?.removePrefix("sha256:")?.takeIf { SHA256.matches(it) } ?: return@mapNotNull null
                asset.get("name").asString to digest
            }.toMap()
            return Release(release.get("tag_name").asString, digests)
        } catch (exception: RuntimeException) {
            // Gson reports malformed or unexpectedly shaped JSON with several unchecked exception types.
            throw IOException("Unexpected wgsl-analyzer release metadata", exception)
        }
    }

    /**
     * The most recently installed release, if its executable is still intact and can be run. Works offline and does not
     * wait for a concurrent update: installation replaces each file atomically and keeps the previous release.
     */
    fun installed(cache: Path, asset: Asset): Installation? = try {
        // Content checks do not cover permissions, which a cache backup and restore can drop.
        verifiedInstallation(cache.resolve(asset.target), asset)?.also { makeExecutable(it.executable) }
    } catch (_: IOException) {
        null
    }

    /**
     * Installs [release] unless the cache has moved on. [replacing] is the tag the caller saw installed when it resolved
     * [release]; if another project or IDE instance has installed a different release since, that one is kept and
     * returned, so metadata resolved before a concurrent update cannot downgrade the shared cache. JVM and file locks
     * serialize installation across projects and IDE instances sharing a cache.
     */
    fun install(cache: Path, asset: Asset, release: Release, replacing: String?, download: (String, Path) -> Unit): Installation {
        val directory = cache.resolve(asset.target)
        Files.createDirectories(directory)
        return locked(directory) {
            val previous = verifiedInstallation(directory, asset)
            if (previous != null && (previous.tag == release.tag || previous.tag != replacing)) {
                makeExecutable(previous.executable)
                return@locked previous
            }
            val archiveSha256 = release.sha256(asset)
            val archive = Files.createTempFile(directory, "download-", ".tmp")
            val unpacked = Files.createTempFile(directory, "executable-", ".tmp")
            try {
                download(release.url(asset), archive)
                if (sha256(archive) != archiveSha256) throw IOException("wgsl-analyzer download failed SHA-256 verification")
                extract(archive, unpacked, asset.name.endsWith(".zip"))
                makeExecutable(unpacked)
                val version = directory.resolve(release.tag)
                Files.createDirectories(version)
                val executable = version.resolve(asset.executableName)
                writeAtomically(version.resolve(CHECKSUM), sha256(unpacked))
                Files.move(unpacked, executable, ATOMIC_MOVE, REPLACE_EXISTING)
                writeAtomically(directory.resolve(CURRENT), release.tag)
                // Keep the previous release: another IDE instance may be about to start it.
                prune(directory, setOfNotNull(release.tag, previous?.tag))
                prune(cache) { LEGACY_PINNED.matches(it) }
                Installation(release.tag, executable)
            } finally {
                Files.deleteIfExists(archive)
                Files.deleteIfExists(unpacked)
            }
        }
    }

    @Synchronized
    private fun <T> locked(directory: Path, action: () -> T): T =
        FileChannel.open(directory.resolve("install.lock"), CREATE, WRITE).use { channel -> channel.lock().use { action() } }

    private fun verifiedInstallation(directory: Path, asset: Asset): Installation? {
        val current = directory.resolve(CURRENT)
        if (!Files.isRegularFile(current)) return null
        val tag = Files.readString(current).trim().takeIf { TAG.matches(it) } ?: return null
        val executable = directory.resolve(tag).resolve(asset.executableName)
        val checksum = executable.resolveSibling(CHECKSUM)
        if (!Files.isRegularFile(executable) || !Files.isRegularFile(checksum)) return null
        if (sha256(executable) != Files.readString(checksum).trim()) return null
        return Installation(tag, executable)
    }

    private fun writeAtomically(path: Path, text: String) {
        val temporary = Files.createTempFile(path.parent, "write-", ".tmp")
        try {
            Files.writeString(temporary, text)
            Files.move(temporary, path, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    // Best effort: a running executable cannot be deleted on Windows; it is retried after the next update.
    private fun prune(directory: Path, keep: Set<String>) = prune(directory) { it !in keep }

    private fun prune(directory: Path, stale: (String) -> Boolean) {
        val paths = try {
            Files.list(directory).use { entries -> entries.filter { Files.isDirectory(it) && stale(it.fileName.toString()) }.toList() }
        } catch (_: IOException) {
            return
        }
        for (path in paths) {
            try {
                Files.walk(path).use { files -> files.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
            } catch (_: IOException) {
            } catch (_: UncheckedIOException) {
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
