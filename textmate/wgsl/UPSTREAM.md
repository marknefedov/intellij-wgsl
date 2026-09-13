# Upstream grammars

The files `syntaxes/*.tmLanguage.json` and `language-configuration.json`
are based on https://github.com/wgsl-analyzer/wgsl-analyzer at commit
`5f9045b5e6795e172653be99341321f404e5829a`, under `editors/code/`.

They are distributed under the upstream MIT OR Apache-2.0 license. Both license
texts are included in this directory. `package.json` is our minimal TextMate
bundle manifest; it intentionally contains no VS Code commands or extension code.

Compatibility patch: `comments.lineComment` uses the string `"//"` instead of
VS Code's newer object form. IntelliJ 2026.1's TextMate reader expects a string.
The JSONC comment is removed so the file also conforms to standard JSON.

Local WGSL grammar patches classify struct and alias declarations, explicit type
positions (including nested templates), parameter declarations, and field access.
Function calls use `support.function` so IntelliJ maps them to function-call
attributes separately from declarations.
They remove the uppercase-name type heuristic and place generic identifiers after
address spaces, access modes, and built-in values. WESL inherits these rules.
These are lexical classifications: constructors, references to parameters/constants,
and identifiers used as template value arguments still need semantic analysis for
fully accurate classification. The plugin maps these shader scopes to configurable
WGSL/WESL editor attributes; other scopes retain IntelliJ's TextMate highlighting.

When updating, copy these three files from a reviewed upstream commit, update
this revision, reapply the local patches, and run the grammar and editor tests. WESL includes `source.wgsl`, so
the two grammars must be updated and distributed together.
