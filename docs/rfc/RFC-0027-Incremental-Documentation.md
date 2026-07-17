# RFC-0027 — Incremental Documentation

Status: Proposed  
Version: 0.1  
Depends on: RFC-0025  
Related: RFC-0026

## Summary

Introduce deterministic project-change detection so DocPilot can identify which
source files changed between two project snapshots.

RFC-0027 establishes the foundation for incremental documentation:

- immutable source-file fingerprints,
- immutable project snapshots,
- deterministic snapshot creation,
- deterministic change-set calculation,
- added, modified, removed, and unchanged file classification,
- and tests for stable ordering and change detection.

This first RFC-0027 package does not yet regenerate documents. It creates the
change-detection layer required by later incremental knowledge-graph and
document-update steps.

## Motivation

Current DocPilot generation analyzes the current project state and generates a
complete document. Repeating full analysis for every small source change is
wasteful and makes it difficult to explain why a document changed.

Incremental documentation requires a stable answer to:

```text
What changed in the project since the previous documented snapshot?
```

The answer must be deterministic and independent of Git, IDE state, file
timestamps, or AI providers.

## Target Flow

```text
Project source inventory
    ↓
ProjectSnapshotBuilder
    ↓
Current ProjectSnapshot
    +
Previous ProjectSnapshot
    ↓
ProjectChangeDetector
    ↓
ProjectChangeSet
    ↓
Incremental knowledge rebuild
    ↓
Affected document section selection
    ↓
Document update
```

## Scope of This Package

This package introduces:

- `SourceFileFingerprint`
- `ProjectSnapshot`
- `ProjectSnapshotBuilder`
- `DefaultProjectSnapshotBuilder`
- `ProjectFileChangeType`
- `ProjectFileChange`
- `ProjectChangeSet`
- `ProjectChangeDetector`
- `DefaultProjectChangeDetector`
- deterministic SHA-256 content hashing
- validation and behavior tests

## Design Principles

1. Content-based, not timestamp-based
2. Deterministic ordering
3. Platform-independent relative paths
4. Immutable snapshot objects
5. Explicit change categories
6. No Git dependency
7. No AI dependency
8. No filesystem watching in this RFC
9. No document mutation in this RFC

## Data Model

### SourceFileFingerprint

```kotlin
data class SourceFileFingerprint(
    val relativePath: String,
    val contentSha256: String,
    val sizeBytes: Long,
)
```

Paths use `/` separators.

### ProjectSnapshot

```kotlin
data class ProjectSnapshot(
    val files: List<SourceFileFingerprint>,
)
```

Files are stored in ascending relative-path order.

### ProjectChangeSet

```kotlin
data class ProjectChangeSet(
    val changes: List<ProjectFileChange>,
)
```

The change set exposes derived lists:

- `added`
- `modified`
- `removed`
- `unchanged`
- `hasChanges`

## Change Rules

| Previous | Current | Classification |
|---|---|---|
| absent | present | ADDED |
| present, different hash | present | MODIFIED |
| present | absent | REMOVED |
| present, same hash | present | UNCHANGED |

File size is retained as useful metadata but content hash determines whether a
file changed.

## Snapshot Input

`ProjectSnapshotBuilder` accepts a project root and a collection of relative
paths. This keeps file discovery separate from fingerprinting and allows the
existing source scanner to remain responsible for inventory policy.

```kotlin
fun build(
    projectRoot: Path,
    relativePaths: Collection<String>,
): ProjectSnapshot
```

## Error Handling

Snapshot creation fails when:

- the project root is not a directory,
- a requested file is outside the project root,
- a requested path does not resolve to a regular file,
- or duplicate normalized relative paths are supplied.

## Compatibility

This package only adds new types and does not alter existing generation,
knowledge, provider, or CLI APIs.

## Non-Goals

RFC-0027 phase 1 does not implement:

- Git diff parsing,
- filesystem watchers,
- snapshot persistence,
- incremental knowledge-graph mutation,
- architecture-section impact analysis,
- document patching,
- document merge conflict handling,
- or background monitoring.

## Acceptance Criteria

1. Same content produces the same SHA-256 fingerprint.
2. Snapshot files are sorted by normalized relative path.
3. Backslashes are normalized to `/`.
4. Added, modified, removed, and unchanged files are classified correctly.
5. Change output order is deterministic.
6. Existing tests continue to pass.
7. `./gradlew clean test` reports `BUILD SUCCESSFUL`.

## Follow-up RFC-0027 Phases

### Phase 2 — Snapshot Persistence

Persist and load project snapshots using a versioned JSON format under:

```text
.docpilot/snapshots/
```

### Phase 3 — Incremental Knowledge Rebuild

Re-index only changed files and invalidate graph elements whose evidence refers
to removed or modified files.

### Phase 4 — Document Impact Analysis

Map changed evidence and graph nodes to document sections.

### Phase 5 — Incremental Document Update

Regenerate only affected sections and deterministically merge them with
unchanged document sections.
