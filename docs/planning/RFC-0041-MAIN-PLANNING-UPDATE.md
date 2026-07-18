# RFC-0041 Main Planning Update

## Project Status

```text
Current Phase
Phase 2 — Post-MVP Evolution

Current Release
v0.5 MVP

Completed RFC
RFC-0001 ~ RFC-0041

Current RFC
RFC-0042 — AI Incremental Generation
```

## RFC-0041 Summary

RFC-0041 integrates the existing ProjectSpecification incremental execution and snapshot persistence contracts into the distributable CLI. A new `generate specification` route performs project analysis, builds the current DIR 0.3 specification, loads and validates the previous specification snapshot, selects no-change/incremental/full-regeneration execution, renders and writes documentation, and saves the snapshot only after successful execution.

The CLI prints execution mode, snapshot validation state, fallback reason, and user-facing errors without printing stack traces. Existing architecture and ADR commands remain unchanged. There is no breaking public API change.

## Architecture Update

```text
CLI Generate Specification
        │
        ▼
ProjectKnowledgeLoader
        │
        ▼
DefaultSpecificationBuilder
        │
        ▼
SpecificationSnapshotExecutionCoordinator
        │
        ▼
IncrementalDocumentationEngine
        │
        ▼
DefaultIncrementalDocumentationExecutor
        │
        ├── ProjectSpecificationMarkdownRenderer
        └── FileDocumentationArtifactWriter
        │
        ▼
Specification Snapshot Save
```

## Implementation

### New classes

- `SpecificationGenerateWorkflow`
- `DefaultSpecificationGenerateWorkflow`
- `ProjectAnalysis`
- `GenerateCommandSpecificationTest`

### Modified classes

- `GenerateCommand`
- `ProjectKnowledgeLoader`
- CLI usage in `Main.kt`

### Deleted classes

- None

### Public API

Core public APIs are unchanged. The CLI gains the additive `generate specification` command.

## Test Result

```text
Compile
NOT RUN — uploaded source ZIP omitted gradle/wrapper/gradle-wrapper.jar

CLI Tests
NOT RUN — same wrapper limitation

Snapshot Tests
NOT RUN — same wrapper limitation

Regression Tests
NOT RUN — same wrapper limitation

Full Gradle Tests
NOT RUN — same wrapper limitation
```

## ADR Candidates

- Whether distributable CLI workflows should be assembled by a dedicated composition root rather than command-local adapters.
- Whether CLI exit codes should become a shared typed contract across all commands.

## Technical Debt

- Add an artifact discovery port if future renderers produce multiple or configurable output paths.
- Move the specification output path into a public renderer contract instead of mirroring the current deterministic path in the CLI adapter.
- Add end-to-end CLI tests once the complete Gradle wrapper is available.

## Next RFC

RFC-0042 can use `SpecificationSnapshotExecutionResult` and the current/previous ProjectSpecification pair as the AI incremental generation entry point. The CLI workflow now establishes the snapshot lifecycle and deterministic execution-mode decision before AI-specific planning is introduced.
