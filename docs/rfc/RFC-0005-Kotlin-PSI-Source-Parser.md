# RFC-0005 — Kotlin PSI Source Parser

Status: Proposed  
Version: 0.1  
Target: Sprint 3

## Summary

Introduce a Kotlin source parser backed by Kotlin compiler PSI.

The parser converts one Kotlin source file into DocPilot-owned syntax models without performing type resolution.

## Extracted Information

- package declaration
- imports and aliases
- classes
- interfaces
- objects
- enum classes
- annotation classes
- functions
- properties
- type aliases
- annotations
- parameters
- declared property and return types
- nested declarations

## Dependency

```text
org.jetbrains.kotlin:kotlin-compiler-embeddable:2.4.0
```

Kotlin PSI remains an internal implementation detail. PSI types must not appear in DocPilot public APIs.

## Non-Goals

Version 0.1 does not resolve symbols, infer types, build call graphs, infer Android semantics, or identify architecture patterns.

## Acceptance Criteria

- Parser tests cover declarations, imports, annotations, parameters, types, and nesting.
- Existing tests continue to pass.
- `./gradlew clean test` reports `BUILD SUCCESSFUL`.
