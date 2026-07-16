# RFC-0007 — Kotlin Symbol Extractor

Status: Proposed  
Version: 0.1  
Target: Sprint 3

## Summary

Introduce a lightweight Kotlin symbol extractor that consumes tokens
produced by `KotlinLexer` and builds DocPilot-owned source models.

## Scope

Version 0.1 extracts:

- package names,
- imports,
- import aliases,
- wildcard imports,
- classes,
- interfaces,
- objects,
- functions,
- properties,
- type aliases,
- enum classes,
- annotation classes,
- explicit visibility,
- basic source locations.

## Non-Goals

This RFC does not:

- validate the complete Kotlin grammar,
- resolve symbols or types,
- extract nested declarations by brace scope,
- build dependency or call graphs,
- or infer Android component roles.

## Acceptance Criteria

- supported declarations are recognized,
- package and imports are extracted,
- explicit visibility is preserved,
- source locations are 1-based,
- existing tests continue to pass,
- and `./gradlew clean test` reports `BUILD SUCCESSFUL`.
