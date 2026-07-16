# RFC-0010 — CLI Source Index Integration

Status: Proposed  
Version: 0.1  
Target: Sprint 3

## Summary

Extend the existing `analyze` CLI command so that one execution generates
both the project summary and the Kotlin source index.

## Command

```text
docpilot analyze <project-path>
```

## Generated Artifacts

```text
<project-path>/docs/project-summary.md
<project-path>/docs/source-index.md
```

## Processing Flow

```text
Project path
    ↓
LocalProjectLoader
    ↓
LocalSourceScanner
    ├── DefaultProjectSummaryBuilder
    │       ↓
    │   project-summary.md
    │
    └── DefaultProjectSourceIndexer
            ↓
        SourceIndexMarkdownRenderer
            ↓
        source-index.md
```

## Scope

RFC-0010:

- preserves the existing `analyze` command,
- generates two deterministic Markdown artifacts,
- creates the output directory when needed,
- reports generated artifact paths,
- and exposes a testable `runCli` function.

## Non-Goals

This RFC does not:

- call an AI model,
- clone Git repositories,
- update existing documents incrementally,
- resolve Kotlin types,
- or infer architecture roles.

## Acceptance Criteria

- one CLI execution creates both Markdown files,
- existing tests continue to pass,
- the command returns a non-zero code for invalid input,
- and `./gradlew clean test` reports `BUILD SUCCESSFUL`.
