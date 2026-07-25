# RFC-0046 Main Planning Update

## Project Dashboard

```text
Current Phase
Phase 1 - MVP / POC

Implemented RFC Candidate
RFC-0046 - Review-gated Managed Block Removal Semantics

Integration State
Feature Worktree implementation and verification complete
Feature Commit and main merge pending

Release
v0.5 MVP / POC - Release Candidate pending
```

## Implementation Summary

- Add explicit `UPSERT` and `REMOVE` documentation patch operations.
- Decode a dedicated `DOCPILOT_REMOVE` marker.
- Authorize REMOVE only for a previous, now-removed specification target with an existing block.
- Add `DocumentationChangeKind.REMOVE` and explicit operation evidence to review entries.
- Bind each proposal to the exact UTF-8 document using lowercase SHA-256.
- Reject apply when the reviewed documentation base changed.
- Require a complete decision set before any accepted operation is transformed.
- Apply accepted mixed operations as one fail-closed in-memory result.
- Preserve handwritten content, unrelated blocks, DIR schema, snapshots, and Provider SPI.

## Verification

- RFC-0046 focused test class: PASS.
- Existing documentation reviewer, report renderer, and AI generator regression: PASS.
- `.\gradlew.bat clean build`: PASS.
- `.\gradlew.bat clean test`: PASS.
- Aggregate: 87 XML files, 265 tests, 0 failures, 0 errors, 0 skipped.
- Isolated architecture-samples CLI smoke: PASS; seven expected artifacts generated.
- `git diff --check`: PASS at documentation preparation.

## Release Readiness

| Gate | Status |
| --- | --- |
| Core Build | PASS |
| Core Tests | PASS |
| CLI Smoke | PASS |
| Explicit Managed Block REMOVE | PASS |
| Complete Review | PASS |
| Reviewed-base Conflict Check | PASS |
| Fail-closed Atomic Transformation | PASS |
| DIR/Snapshot Compatibility | PASS |
| Feature Git Integration | PENDING |
| Release Candidate | PENDING |

## Non-goals Preserved

- No documentation artifact/file deletion.
- No durable proposal or decision persistence.
- No CLI/UI workflow.
- No MCP source, tests, or state changes.
- No new relationship extraction.

## Known Limitations

- Reviewed-base safety is in-process only.
- Exact byte-level fingerprinting intentionally conflicts on line-ending or whitespace changes.
- Atomicity covers the pure in-memory transformation, not a later artifact writer.
- AI heading cleanup is limited to an exact empty DocPilot-owned section.
- Remote main and release integration remain pending.

## Git Integration Status

- Feature Worktree: `C:\WorkSpace\docpilot-rfc-0046`
- Feature Branch: `feature/rfc-0046-managed-block-removal`
- Baseline: `28715ef60d732812ccb0fdf3a6ea14c2cef7b2dc`
- Feature Commit: PENDING
- Main merge: NOT PERFORMED
- Push: NOT PERFORMED
