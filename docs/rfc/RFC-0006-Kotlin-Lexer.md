# RFC-0006 — Kotlin Lexer

Status: Proposed  
Version: 0.1  
Target: Sprint 3

## Summary

Introduce a lightweight Kotlin lexer owned by DocPilot.

It converts Kotlin source text into deterministic tokens without Kotlin compiler internals, PSI, or external parser libraries.

## Scope

Recognized token categories:

- selected Kotlin keywords,
- identifiers,
- string literals,
- character literals,
- number literals,
- symbols,
- end-of-file.

Whitespace and comments are skipped.

## Non-Goals

This RFC does not build an AST, extract symbols, resolve types, validate Kotlin grammar, or interpret Android semantics.

## Acceptance Criteria

- token positions are 1-based,
- comments are ignored,
- literals remain single tokens,
- existing tests pass,
- and `./gradlew clean test` reports `BUILD SUCCESSFUL`.
