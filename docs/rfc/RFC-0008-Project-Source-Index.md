# RFC-0008 — Project Source Index

Status: Proposed
Version: 0.1
Target: Sprint 3

## Summary

Create a project-level Source Index by connecting ProjectInventory,
KotlinLexer, and KotlinSymbolExtractor.

## Scope

- Read Kotlin files from ProjectInventory.
- Tokenize and extract symbols.
- Return deterministic SourceFile ordering.
- Report per-file failures without stopping the full index.

## Non-Goals

No Java indexing, type resolution, dependency graph, call graph,
Android-role inference, or summary rendering.

## Acceptance Criteria

`./gradlew clean test` reports `BUILD SUCCESSFUL`.
