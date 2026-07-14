# RFC-0003 — Project Summary

Status: Proposed  
Version: 0.1  
Target: Sprint 1

## Summary

Introduce a deterministic summary-building stage that converts a `ProjectInventory`
into a concise, evidence-based `ProjectSummary`.

The first renderer will convert that summary into Markdown.

## Processing Flow

```text
ProjectRoot
    ↓
SourceScanner
    ↓
ProjectInventory
    ↓
ProjectSummaryBuilder
    ↓
ProjectSummary
    ↓
ProjectSummaryMarkdownRenderer
    ↓
Markdown
```

## Responsibilities

The summary builder must derive only facts that can be verified from the inventory:

- project name,
- Git working-tree detection,
- detected source languages,
- detected build systems,
- likely module paths,
- and categorized file counts.

## Module Detection

Version 0.1 treats the parent directory of each non-root Gradle build file as a
candidate module path.

Examples:

- `build.gradle.kts` → root project, not added as a module path
- `app/build.gradle.kts` → `app`
- `feature/tasks/build.gradle.kts` → `feature/tasks`

This is intentionally conservative. Gradle settings parsing belongs to a later RFC.

## Non-Responsibilities

Version 0.1 does not:

- parse Gradle syntax,
- infer architecture,
- identify classes or functions,
- resolve dependencies,
- or claim that a candidate module is a confirmed Gradle module.

## Markdown Output

The renderer produces a reviewable summary containing:

- project identity,
- detected languages and build systems,
- candidate module paths,
- and file counts.

## Acceptance Criteria

The RFC is implemented when:

- summary-building tests pass,
- Markdown-rendering tests pass,
- all existing tests pass,
- and `./gradlew test` reports `BUILD SUCCESSFUL`.
