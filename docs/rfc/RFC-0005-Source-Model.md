# RFC-0005 — Source Model

Status: Proposed  
Version: 0.1  
Target: Sprint 3

## Summary

Introduce DocPilot-owned source models for representing source files and declared symbols.

This RFC defines the data structures only.

It does not define tokenization, parsing, symbol extraction, type resolution, or architecture inference.

---

## Motivation

The current DocPilot MVP can:

- load a local project,
- scan files,
- classify project files,
- build a project summary,
- and render Markdown.

The next milestone is to represent source-level structure in a stable model that does not depend on Kotlin compiler internals, PSI, AST libraries, or a specific parser implementation.

The Source Model will become the contract between future source extractors and higher-level knowledge builders.

---

## Processing Position

```text
ProjectInventory
    ↓
Future Source Extractor
    ↓
SourceFile
    ↓
SourceSymbol
    ↓
Future SourceIndex
    ↓
Knowledge Model
```

---

## Design Principles

The Source Model must be:

- independent from Kotlin compiler internals,
- immutable,
- deterministic,
- serializable in the future,
- suitable for tests,
- suitable for partial extraction,
- and extensible without exposing parser-specific types.

---

## Proposed Model

### SourceFile

Represents one source file discovered in a project.

```kotlin
public data class SourceFile(
    val relativePath: String,
    val language: SourceLanguage,
    val packageName: String?,
    val imports: List<SourceImport>,
    val symbols: List<SourceSymbol>,
)
```

Required properties:

- `relativePath`
- `language`
- `imports`
- `symbols`

Optional properties:

- `packageName`

All paths are relative to the analyzed project root and use `/` as the separator.

---

### SourceLanguage

Initial values:

```kotlin
public enum class SourceLanguage {
    KOTLIN,
    JAVA,
    UNKNOWN,
}
```

Version 0.1 focuses on Kotlin but keeps the model language-neutral.

---

### SourceImport

Represents one import declaration.

```kotlin
public data class SourceImport(
    val qualifiedName: String,
    val alias: String? = null,
    val wildcard: Boolean = false,
)
```

Examples:

```text
kotlin.collections.List
kotlinx.coroutines.flow.Flow as TaskFlow
kotlin.collections.*
```

---

### SourceSymbol

Represents one declared symbol.

```kotlin
public data class SourceSymbol(
    val name: String,
    val kind: SourceSymbolKind,
    val visibility: SourceVisibility,
    val location: SourceLocation,
    val annotations: List<String> = emptyList(),
    val children: List<SourceSymbol> = emptyList(),
)
```

A symbol may contain child symbols.

Examples:

- a class containing properties and functions,
- an object containing constants,
- an interface containing function declarations.

---

### SourceSymbolKind

Initial values:

```kotlin
public enum class SourceSymbolKind {
    CLASS,
    INTERFACE,
    OBJECT,
    ENUM_CLASS,
    ANNOTATION_CLASS,
    FUNCTION,
    PROPERTY,
    TYPE_ALIAS,
    UNKNOWN,
}
```

The model intentionally avoids Android-specific kinds such as Activity, Fragment, ViewModel, or Service.

Those meanings belong to a later Android knowledge-building stage.

---

### SourceVisibility

Initial values:

```kotlin
public enum class SourceVisibility {
    PUBLIC,
    INTERNAL,
    PROTECTED,
    PRIVATE,
    DEFAULT,
}
```

`DEFAULT` means no explicit visibility modifier was extracted.

It does not imply that the effective language visibility has already been resolved.

---

### SourceLocation

Represents the source evidence location of a symbol.

```kotlin
public data class SourceLocation(
    val relativePath: String,
    val lineStart: Int?,
    val columnStart: Int?,
    val lineEnd: Int?,
    val columnEnd: Int?,
)
```

Location values may be absent when an extractor cannot determine them reliably.

The relative path must match the owning `SourceFile.relativePath`.

---

## Validation Rules

1. `SourceFile.relativePath` must not be blank.
2. `SourceImport.qualifiedName` must not be blank.
3. `SourceSymbol.name` must not be blank.
4. `SourceLocation.relativePath` must not be blank.
5. Line and column numbers, when present, must be greater than zero.
6. Child symbols must remain ordered by source position when position information is available.
7. Parser-specific or compiler-specific objects must never appear in public Source Model types.

---

## Version 0.1 Scope

This RFC defines only the Source Model.

It includes:

- source file identity,
- language,
- package name,
- imports,
- symbols,
- symbol kinds,
- visibility,
- annotations,
- nesting,
- and source locations.

---

## Non-Goals

RFC-0005 does not define:

- Kotlin tokenization,
- Java tokenization,
- parsing,
- syntax diagnostics,
- symbol resolution,
- type inference,
- inheritance resolution,
- dependency graphs,
- function signatures,
- parameters,
- return types,
- Android component recognition,
- call graphs,
- or architecture inference.

These may be introduced through later RFCs.

---

## Package Structure

Recommended package:

```text
src/main/kotlin/io/docpilot/core/model/source/
```

Proposed files:

```text
SourceFile.kt
SourceImport.kt
SourceLanguage.kt
SourceLocation.kt
SourceSymbol.kt
SourceSymbolKind.kt
SourceVisibility.kt
```

Tests:

```text
src/test/kotlin/io/docpilot/core/model/source/SourceModelTest.kt
```

---

## Testing Strategy

Tests must verify:

- valid model construction,
- validation of blank names and paths,
- validation of line and column numbers,
- representation of nested symbols,
- import aliases,
- wildcard imports,
- and deterministic child ordering supplied by callers.

No parser is required for this RFC.

---

## Compatibility

This RFC adds new model types only.

It does not change the current Project Loader, Source Scanner, Project Summary Builder, Markdown Renderer, or CLI behavior.

---

## Acceptance Criteria

RFC-0005 is complete when:

1. all proposed source model types are implemented,
2. validation tests pass,
3. all existing tests continue to pass,
4. no compiler or parser dependency is added,
5. and `./gradlew clean test` reports `BUILD SUCCESSFUL`.

---

## Follow-up RFCs

Planned follow-up work:

- RFC-0006 — Kotlin Lexer
- RFC-0007 — Kotlin Symbol Extractor
- RFC-0008 — Project Source Index
- RFC-0009 — Symbol Summary Rendering
