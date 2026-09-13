# WGSL / WESL support for IntelliJ

<!-- Plugin description -->
WGSL and WESL language support powered by **wgsl-analyzer**, with upstream
TextMate grammars for syntax highlighting. Provides server-backed completion,
diagnostics, hover documentation, navigation, formatting, parameter information,
and inlay hints, according to the capabilities of the selected server.

Requires an IntelliJ-based IDE with JetBrains LSP support, version **2026.2.2 or
newer**, and the bundled **TextMate Bundles** plugin.
<!-- Plugin description end -->

## Getting started

1. Install the plugin and open a `.wgsl` or `.wesl` file.
2. On the first language-server startup, the plugin downloads the pinned
   **wgsl-analyzer 2026-04-26** release from GitHub. Both the archive and executable
   are checked against bundled SHA-256 digests before execution.
3. Subsequent starts reuse the verified executable from
   `<IDE system/cache directory>/wgsl-analyzer/2026-04-26/<target>/`.
4. Use the IDE's Language Services widget to inspect server status, restart it,
   or open its settings. Syntax highlighting also works offline without a server.

There is no automatic tracking of `latest` or `nightly`. A plugin update can
change the pinned version; older cache directories are left intact for other
plugin/IDE versions. Interrupted downloads are not installed. A corrupted cached
executable is downloaded again. IDE HTTP proxy settings apply to downloads.

## Settings

Open **Settings | Editor | Color Scheme | WGSL / WESL** to customize types,
function declarations and calls, fields/swizzles, parameter declarations, and
variables. Light and dark defaults are included. Colors use TextMate scopes and
work without the language server; parameter references and other ambiguous names
remain lexical classifications rather than resolved symbols.

Open **Settings | Languages & Frameworks | WGSL / WESL**:

- **Enable wgsl-analyzer** controls semantic features independently of highlighting.
- **Managed version** downloads and caches the pinned server automatically.
- **Custom executable** accepts an executable path (including spaces) or a command
  such as `wgsl-analyzer` available on the IDE process's PATH. No shell is used.
- **Server configuration** accepts a JSON object passed as initialization options
  and returned for `workspace/configuration` requests. Use upstream settings for
  the chosen server version, without an outer `wgsl-analyzer` key.

Applying changes restarts the project's language server. Each project has its own
settings and server process; managed binaries are shared through the IDE cache.

The managed release provides Windows x64/ARM64, Linux x64 (musl)/ARM64 (glibc), and
macOS ARM64 executables. Upstream does not publish an Intel macOS executable for
this release; select a locally built executable there. The custom executable mode can also be
used in offline environments. First-time managed installation requires access to
GitHub release downloads.

## Compatibility and migration

This replaces the original Java/JFlex/PSI implementation. The plugin ID is `WGSL_WESL`, separate from the original `WGSL` plugin. File registration and the existing icon are retained.

- Open-source IntelliJ IDEA builds and Android Studio do not provide the required
  JetBrains LSP module. The old Community 2024.2 baseline is no longer supported.
- The detailed native parser, completion, annotations, and reference/rename code
  have been removed. The availability of rename and find-usages features depends on the selected
  server; the pinned server must not be assumed to provide every LSP feature.
- Old custom URL import settings and per-file warning suppression comments are
  not migrated. Configure project/import behavior supported by wgsl-analyzer.
- WESL support follows upstream and remains experimental.
- TextMate supplies lexical highlighting, comments, and bracket behavior. There
  is no second semantic engine or generated language grammar in this plugin.

## Development

Use **JDK 21**, **Kotlin 2.4.20**, and the checked-in **Gradle 9.7.1 wrapper**:

```sh
./gradlew test buildPlugin
./gradlew runIde
./gradlew verifyPlugin
```

On Windows use `gradlew.bat`. The distributable ZIP is written to
`build/distributions/`. The build downloads the target IntelliJ SDK and its
TextMate dependency. Unit and editor integration tests do not download the
language server.

To smoke-test the real pinned server, download the release archive for the host
platform and run `./gradlew test -PwgslServerArchive=/absolute/path/to/archive`.
The test verifies and installs it into a temporary cache, starts LSP over stdio,
and requests struct-member completion. It does not use a developer's configured
server or cache.

Highlighting grammars live in `textmate/wgsl/` and are copied outside the plugin
JAR into every sandbox and distribution. See [upstream provenance](textmate/wgsl/UPSTREAM.md)
for the exact grammar revision and licenses. The pinned server manifest is
`src/main/resources/wgsl-server.properties`; updating it requires validating the
release archive hashes and recalculating extracted executable hashes for each
supported platform.

The compiler targets Kotlin 2.4 APIs and uses the IDE-bundled standard library.
Settings use Kotlin UI DSL and tracked persistent state and immutable snapshots.
