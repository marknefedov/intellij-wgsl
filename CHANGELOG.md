<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# wgsl plugin Changelog

## Unreleased

- Store managed wgsl-analyzer binaries in the plugin-specific `intellij-wgsl/wgsl-analyzer` IDE cache directory using `PathManager.getSystemDir()`.

- Add configurable WGSL/WESL type, function, field, parameter, and variable colors with light and dark defaults, using the existing TextMate lexer.

- Improve WGSL/WESL TextMate scopes for structs, aliases, explicit types, parameters, and fields, including lowercase type names and nested templates.

- Modernize settings with Kotlin UI DSL, localized labels, and tracked state with immutable snapshots; upgrade the Kotlin compiler to 2.4.20.

- Rebuild the plugin in Kotlin using JetBrains LSP and upstream WGSL/WESL TextMate grammars.
- Download and verify pinned wgsl-analyzer binaries on first use, with a custom executable option.
- Add project server settings and restart integration with the Language Services widget.
- Require an LSP-capable IntelliJ-based IDE 2026.2.2 or newer; migrate to Gradle 9.7.1 and Java 21.
- Remove the native parser, JFlex lexer, semantic analysis, and legacy custom URL imports.

## 0.0.37 - 2025-08-12

- update base platform version
- fix for #84 - fixed highlighting of `const` with uppercase only name

## 0.0.36 - 2025-05-01

- fix for #82 - added missing built-in functions
- fixed display of built-in function documentation

## 0.0.35 - 2024-11-16

- support array literals

## 0.0.34 - 2024-10-26

- Allow nested array types

## 0.0.33 - 2024-10-25

- added support for builtin type aliases - #78
- updated to the latest IntelliJ Platform

## 0.0.32 - 2024-08-01

- Remove the maximum supported version to allow future versions — @andreypfau

## 0.0.31 - 2024-04-11

- update for 2024.1

## 0.0.30

- support for multi-line preprocessor declarations - #71

## 0.0.29

- update for 2023.2.3

## 0.0.28

### Added

- code fixes from @Uriopass
- added support for `bindind_array` - #67

## 0.0.27

### Added

- better code completion from @rosingrind - closes #53
- code fixes from @rosingrind
- updated for IDEs based on 2023.2

## 0.0.26

### Added

- improved refactoring support from @rosingrind

## 0.0.25

### Added

- fix for nested array references - #55
- updated grammar kit dependencies

## 0.0.24

### Added

- updated the maximum version of IntelliJ IDEA to 2023.1
- added preprocessor stuff to the top level in the parser

## 0.0.23

### Added

- updated the maximum version of IntelliJ IDEA to 2022.3
- updated to JDK 17
- added examples

## 0.0.22

### Added

- fix for const expressions - #44

## 0.0.21

### Added

- fix for i32 and u32 code completions - #47
- fixed highlighting of `texture_2d`

## 0.0.20

### Added

- fix for f16 and f32 code completions - #43
- added support for const and override expressions - #44
- added a warning for old global constant syntax — disable with
  - `\\+ old-global-constant-decl` on the first line of the file
- fixed-size arrays - #45

## 0.0.19

### Added

- fix for trailing commas in function and constant constructor arguments - ticket #41

## 0.0.18

### Added

- fix for nested types - #37
- allow older syntax deprecation warnings to be turned off - #38
    - adding a comment as the first line in the file like the following will allow deprecation warnings to be disabled
      ```
      \\+ old-attribute-syntax old-struct-syntax old-attributes
      ```
      - old-attribute-syntax disables the warning on the old bracket style attributes
      - `old-struct-syntax` disables the warning for semicolons separating `struct` members
      - `old-attributes` disables the warning for old deprecated attributes such as `stage`

## 0.0.17

### Added

- increase the maximum build number for the IntelliJ Platform to 222 - #36

## 0.0.16

### Added

- added support for compound assignment operators - see issue #34

## 0.0.15

### Added

- updating grammar to handle the latest spec changes
  - while
  - staticAssert
  - Allow vec and mat constructors without an explicit type; see #32

## 0.0.14

### Added

- reserved words list update from vibernation
- IDE comment action support
- preprocessor changes from Arc-blroth

## 0.0.13

### Added

- Added missing attribute docs
- Added simple support for Bevy WGSL preprocessor declarations

## 0.0.12

### Added

- All currently defined attributes are supported
- Added warnings for the deprecated stage attribute
- Added hover documentation for all defined attributes
- Added warnings for deprecated attribute syntax

## 0.0.11

### Added

- Updated to support comma instead of semicolon in struct definitions
- Updated syntax highlighting example to use more modern syntax

## 0.0.10

### Added

- Fixed a null pointer exception in the annotator

## 0.0.9

### Added

- Changes from HolgerGottChristensen
  - Initial code folding implementation
  - A bracket matcher that matches common bracket types
  - Updated annotator to annotate reserved keywords
  - Added a keyword completion contributor that allows for completing all built-in keywords.
  - Added a built-in completion handler that completes all built-in functions.

## 0.0.8

### Added

- Changes from HolgerGottChristensen
  - Updated the color for built-in types to be Keyword rather than class name
  - Color struct fields
  - Color attributes
  - Color built-in functions
  - Add an error when a fragment-only function is used outside a fragment stage
  - Color function declarations

## 0.0.7

### Added

- support for proposed new `@attribute` syntax
- hover documentation for attributes

## 0.6.0

### Added

- updated max build number

## 0.0.5

### Added

- another attempt at fixing the hover docs in a production build

## 0.0.4

### Added

- fixed hover doc code. Weird that it works in dev mode but not in the real thing

## 0.0.3

### Added

- added hover documentation for functions and some help for the builtin functions
- added doc comment support for functions
- implemented rename refactor for variables and references
- better `++` and `--` in the parser

## 0.0.2

### Added

- added increment `++` and  decrement `--` syntax support

## 0.0.1

### Added

- Color settings page
- Syntax highlighting using the output from the parser
- Lexer and parser for WGSL
- Initial scaffold created from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)
