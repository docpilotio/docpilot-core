# RFC-0028 — Snapshot Persistence

Status: Proposed  
Version: 0.1  
Depends on: RFC-0027

## Summary

Persist deterministic project snapshots under `.docpilot/snapshots/` so that
DocPilot can compare the current project state with the last documented state.

This implementation adds a versioned JSON format, atomic file replacement,
snapshot metadata, repository abstractions, and tests without introducing a
third-party JSON dependency.

## Storage Layout

```text
.docpilot/
└── snapshots/
    ├── latest.json
    └── previous.json
```

When a new snapshot is saved:

1. the existing `latest.json` is moved to `previous.json`;
2. the new snapshot is written to a temporary file;
3. the temporary file is atomically moved to `latest.json` when supported.

## Format

```json
{
  "schemaVersion": 1,
  "createdAt": "2026-07-17T00:00:00Z",
  "files": [
    {
      "relativePath": "src/main/Sample.kt",
      "contentSha256": "...",
      "sizeBytes": 42
    }
  ]
}
```

`createdAt` is metadata and is not used to determine source changes.

## Components

- `StoredProjectSnapshot`
- `SnapshotFormat`
- `SnapshotCodec`
- `JsonSnapshotCodec`
- `SnapshotRepository`
- `FileSnapshotRepository`

## Design Rules

1. The JSON schema is explicitly versioned.
2. File entries retain deterministic relative-path ordering.
3. Writes use UTF-8.
4. A new snapshot does not overwrite the previous snapshot without rotation.
5. Snapshot change detection remains content based.
6. Persistence is independent of Git and AI providers.
7. Unsupported schema versions fail clearly.
8. Malformed files do not silently produce empty snapshots.

## Compatibility

RFC-0027 `ProjectSnapshot` remains unchanged. Persistence wraps it with metadata
rather than adding timestamps to the deterministic snapshot model.

## Acceptance Criteria

1. A snapshot round-trips through JSON without changing file fingerprints.
2. `latest.json` is created on first save.
3. A second save rotates the old latest snapshot to `previous.json`.
4. Missing snapshot files return `null`.
5. Unsupported schema versions fail.
6. JSON escaping supports paths containing quotes and backslashes.
7. Existing tests continue to pass.
8. `./gradlew clean test` reports `BUILD SUCCESSFUL`.
