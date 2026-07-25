# RFC-0046 Integration Handoff Addendum

## Handoff Identity

- RFC: RFC-0046
- Title: Review-gated Managed Block Removal Semantics
- Status: LOCALLY_INTEGRATED
- Date: 2026-07-25

## Git Integration

- Baseline: `28715ef60d732812ccb0fdf3a6ea14c2cef7b2dc`
- Feature Branch: `feature/rfc-0046-managed-block-removal`
- Feature Commit: `ae0d6d35c97ddf27cd9a5f1e05a7c2dc165d588b`
- Local main merge commit: `d3c50ce98442bb8e823041e19f12345cb9d5d63e`
- Merge strategy: no-ff
- origin/main before final push: `c62965cda3aef7f2d69165c545c5e1f11696f242`

The original Completion Handoff records the verified Feature Candidate. This
addendum records its subsequent local main integration and final remote delivery
preparation.

## Delivered Capability

- Explicit `UPSERT` and `REMOVE` documentation patch operations.
- Dedicated `DOCPILOT_REMOVE` response marker.
- REMOVE authorization only for valid REMOVED plan targets.
- Complete user review before any removal.
- Exact UTF-8 SHA-256 reviewed-base conflict detection.
- Fail-closed, deterministic, accepted-only in-memory transformation.
- Safe managed-block and empty owned-heading removal.

## Verification

- Focused RFC-0046 tests: PASS.
- `.\gradlew.bat clean build`: PASS.
- `.\gradlew.bat clean test`: PASS.
- Test XML: 87 files.
- Tests: 265.
- Failures: 0.
- Errors: 0.
- Skipped: 0.
- Isolated architecture-samples CLI smoke: PASS.
- `git diff --check`: PASS.

## Compatibility

- Existing two-argument UPSERT patch construction remains valid.
- DIR schema: `0.3`, unchanged.
- Specification snapshot format: `1`, unchanged.
- Provider SPI: unchanged.
- Artifact/file deletion: not introduced.
- MCP source/tests/state: unchanged.

## Known Limitations

- Review proposal and decisions remain in-process and transient.
- Exact document fingerprints intentionally conflict on any byte change.
- Atomicity covers Core's in-memory transformation, not a later file writer.
- No CLI/UI review workflow.
- Release Candidate remains pending.

## User Change Protection

`C:\WorkSpace\docpilot-core\archive-project.bat` remains untracked and was not
modified, staged, or committed.

## RFC-0047 Boundary

RFC-0047 is not selected by this handoff. Two planning alternatives are provided:

1. durable auditable review persistence and stale-apply safety;
2. v0.5 release provenance and determinism gates.

User approval is required before either becomes the RFC-0047 specification.
