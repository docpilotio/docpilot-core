# RFC-0012 — Plugin Architecture

Status: Proposed
Version: 0.1
Target: Sprint 5

## Summary

Introduce a small, stable plugin contract for extending DocPilot without
coupling `docpilot-core` to specific analyzers, AI providers, or output formats.

## Plugin Categories

- `ANALYSIS`: enriches project knowledge from source or project inputs.
- `OUTPUT`: transforms verified knowledge into external artifacts or services.

## Design Principles

- Core owns only the plugin contracts.
- Plugins declare identity, category, and version.
- Plugin execution uses explicit input and output models.
- Plugins must not mutate shared input state.
- Plugin results carry generated artifacts, messages, and failures.
- AI-provider-specific behavior is deferred to a later RFC.

## Scope of Commit 1

This commit introduces:

- plugin identity and metadata,
- plugin category,
- execution context,
- execution result,
- and the base `DocPilotPlugin` interface.

## Non-Goals

This commit does not:

- discover plugins dynamically,
- load external JAR files,
- execute plugins from the CLI,
- define an AI provider,
- or add a dependency injection framework.

## Acceptance Criteria

- plugin IDs and versions are validated,
- result models support success and failure,
- analysis and output categories are explicit,
- no external dependency is added,
- and `./gradlew clean test` reports `BUILD SUCCESSFUL`.
