# Upstream grammars

The files `syntaxes/*.tmLanguage.json` are unmodified copies, and
`language-configuration.json` is adapted from https://github.com/wgsl-analyzer/wgsl-analyzer at commit
`5f9045b5e6795e172653be99341321f404e5829a`, under `editors/code/`.

They are distributed under upstream's MIT OR Apache-2.0 license. Both license
texts are included in this directory. `package.json` is our minimal TextMate
bundle manifest; it intentionally contains no VS Code commands or extension code.

Compatibility patch: `comments.lineComment` uses the string `"//"` instead of
VS Code's newer object form. IntelliJ 2026.1's TextMate reader expects a string.

When updating, copy these three files from a reviewed upstream commit, update
this revision, reapply the compatibility patch, and run the editor tests. WESL includes `source.wgsl`, so
the two grammars must be updated and distributed together.
