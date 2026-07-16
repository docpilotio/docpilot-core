# RFC-0009 — Source Index Rendering

Status: Proposed  
Version: 0.1  
Target: Sprint 3

## Summary

Add a Markdown renderer for `SourceIndex`.

This renderer converts the deterministic source index into a
human-reviewable document without using an AI model.

## Processing Flow

```text
SourceIndex
    ↓
SourceIndexMarkdownRenderer
    ↓
docs/source-index.md
```

## Scope

Version 0.1 renders:

- total indexed Kotlin files,
- total extracted symbols,
- indexing failure count,
- each source file,
- package name,
- imports,
- top-level symbols,
- symbol kind,
- visibility,
- and source line when available.

## Non-Goals

This RFC does not:

- infer symbol roles,
- resolve dependencies,
- render nested symbol trees,
- generate architecture explanations,
- or call an AI model.

## Acceptance Criteria

- output ordering follows `SourceIndex.files`,
- empty sections are explicit,
- failures are rendered separately,
- existing tests continue to pass,
- and `./gradlew clean test` reports `BUILD SUCCESSFUL`.
