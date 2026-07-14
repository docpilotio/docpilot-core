# RFC-0002 — Source Scanner

Status: Proposed  
Version: 0.1  
Target: Sprint 1

## Summary

Introduce a source-scanning boundary that inventories the contents of a validated `ProjectRoot`.

The scanner discovers files and directories without interpreting source-code semantics.

## Proposed Flow

```text
ProjectRoot
    ↓
SourceScanner
    ↓
ProjectInventory
    ↓
Future Knowledge Builder
```

## Responsibilities

The Source Scanner must:

1. recursively traverse the project directory,
2. return relative paths,
3. classify common project files,
4. ignore generated and tool-managed directories,
5. produce deterministic output ordering,
6. and remain independent from Android SDK classes.

## Initial File Categories

Version 0.1 recognizes:

- Kotlin source files,
- Java source files,
- Gradle build files,
- Gradle settings files,
- Android manifest files,
- XML resource files,
- Markdown files,
- and other files.

## Default Exclusions

The scanner ignores directory trees named:

- `.git`
- `.gradle`
- `.idea`
- `build`
- `out`
- `node_modules`

## Non-Responsibilities

The Source Scanner does not:

- parse Kotlin or Java,
- infer architecture,
- detect Android modules,
- resolve Gradle dependencies,
- build a knowledge graph,
- or generate documentation.

## API

```kotlin
public fun interface SourceScanner {
    public fun scan(project: ProjectRoot): ProjectInventory
}
```

## Determinism

All returned path lists must be sorted by their normalized relative path.

Deterministic output is required for reliable tests, reviews, and future regression comparisons.

## Testing

Tests must cover:

- classification of supported file types,
- recursive traversal,
- default exclusions,
- relative-path output,
- and deterministic ordering.

## Acceptance Criteria

The RFC is implemented when:

- all source-scanner tests pass,
- all existing tests continue to pass,
- `./gradlew test` succeeds,
- and no Android-specific dependency is added to Core.
