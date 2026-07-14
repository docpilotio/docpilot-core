# RFC-0004 — In-Repository CLI MVP

Status: Proposed  
Version: 0.1  
Target: Sprint 2

## Summary

Add a temporary executable CLI entry point inside `docpilot-core`.

The CLI connects the existing Core pipeline and produces a Markdown project summary from a local project directory.

## Command

```text
docpilot analyze <project-path> [output-path]
```

Default output:

```text
<project-path>/docs/project-summary.md
```

## Processing Flow

```text
CLI arguments
    ↓
LocalProjectLoader
    ↓
LocalSourceScanner
    ↓
DefaultProjectSummaryBuilder
    ↓
ProjectSummaryMarkdownRenderer
    ↓
Markdown file
```

## Responsibilities

The CLI must:

1. validate the command and arguments,
2. load the local project,
3. scan project files,
4. build a deterministic summary,
5. render Markdown,
6. create the output directory when necessary,
7. write the output file,
8. report the generated path.

## Non-Responsibilities

Version 0.1 does not:

- clone GitHub repositories,
- parse Kotlin semantics,
- infer architecture,
- update existing specifications,
- or provide an interactive shell.

## Temporary Location

The CLI lives in `docpilot-core` only for the MVP.

It may later move to a dedicated `docpilot-cli` repository after the Core API stabilizes.

## Acceptance Criteria

- `./gradlew test` succeeds.
- `./gradlew run --args="analyze <path>"` generates Markdown.
- Existing Core tests continue to pass.
- Invalid commands return a clear usage message.
