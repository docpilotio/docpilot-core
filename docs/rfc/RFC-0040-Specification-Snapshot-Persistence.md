# RFC-0040 — Specification Snapshot Persistence

## Status

Implemented

## Summary

RFC-0040 introduces a versioned, deterministic persistence contract for `ProjectSpecification` while preserving the existing RFC-0028 source-fingerprint snapshot contract.

## Decisions

- Existing `ProjectSnapshot` and `FileSnapshotRepository` remain unchanged.
- Specification snapshots use a separate contract and file: `.docpilot/snapshots/specification.json`.
- Snapshot format version and DIR schema version are separate fields.
- Current snapshot format is `1`; supported DIR schema is `0.3`.
- Project identity uses `ProjectSpecification.project.id`, avoiding absolute-path coupling.
- Integrity uses SHA-256 over the canonical specification payload. The integrity field is excluded from its own digest.
- Collections representing sets and stable-id entities are serialized deterministically.
- A temporary file is fully written and decoded before atomic replacement is attempted.
- Unsupported future snapshot versions fail explicitly and are not overwritten.
- Missing, corrupted, schema-mismatched, integrity-mismatched, or project-mismatched snapshots are not used as incremental input; the coordinator can perform full regeneration and replace them only after successful execution.
- The RFC-0039 executor API remains unchanged. A new coordinator owns snapshot lifecycle orchestration.

## Architecture

```text
Current ProjectSpecification
        │
        ▼
SpecificationSnapshotExecutionCoordinator
        │
        ├── SpecificationSnapshotRepository.load
        ├── validation / integrity / identity
        ├── IncrementalDocumentationEngine
        ├── existing RFC-0039 IncrementalDocumentationExecutor
        └── SpecificationSnapshotRepository.save (success only)
```

Persistence:

```text
ProjectSpecification
        │
        ▼
Canonical JSON payload
        │
        ├── SHA-256
        ▼
Versioned envelope
        │
        ▼
Temporary UTF-8 file
        │
        ▼
Decode + validate temporary file
        │
        ▼
Atomic replacement (fallback when unsupported)
```

## Validation outcomes

- `NotFound`
- `Valid`
- `Invalid(CORRUPTED)`
- `Invalid(UNSUPPORTED_VERSION)`
- `Invalid(SCHEMA_MISMATCH)`
- `Invalid(PROJECT_MISMATCH)`
- `Invalid(INTEGRITY_MISMATCH)`
- `Invalid(INVALID_SPECIFICATION)`

A migration extension point is represented by the explicit format version and load result boundary. No fictional pre-version-1 migration is implemented.

## Lifecycle policy

- Documentation execution failure: snapshot is not saved.
- Snapshot save failure: coordinator returns `FAILED` at `SNAPSHOT_SAVE`.
- Valid snapshot plus `NO_CHANGES`: snapshot rewrite is skipped.
- Full regeneration or incremental success: current specification is saved.
- Unsupported future version: explicit `SNAPSHOT_LOAD` failure to prevent destructive downgrade.

## Verification

- Relevant production sources compile with local `kotlinc`.
- Codec round-trip and payload-tampering smoke verification passed.
- Full Gradle tests were not run in the implementation environment because Gradle 9.3.0 distribution download was blocked.

Windows verification:

```powershell
.\gradlew.bat clean test
```
