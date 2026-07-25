# RFC-0047 Main Planning Update

## Project Dashboard

```text
Current Phase
Phase 1 - MVP / POC

Implemented RFC Candidate
RFC-0047 - Auditable Review Persistence and Resumable Conflict-safe Apply

Integration State
Feature implementation and verification complete
Feature Commit and main merge pending
```

## Implementation Summary

- Define Core-owned Review Bundle format version 1.
- Encode proposal, decisions, project/specification identities as canonical JSON.
- Derive deterministic decision-independent proposal IDs.
- Protect payloads with SHA-256 integrity.
- Persist bundles under `.docpilot/reviews` through a Core repository port.
- Validate temporary files before atomic replacement.
- Prevent lost updates with expected payload checksums.
- Persist partial decisions and resume from a new workflow/repository instance.
- Block stale bundle and stale documentation apply independently.
- Preserve RFC-0046 UPSERT/REMOVE and complete-review invariants.

## Verification

- Focused codec/repository/restart/resume tests: PASS.
- `.\gradlew.bat clean build`: PASS.
- `.\gradlew.bat clean test`: PASS.
- Aggregate: 88 XML files, 270 tests, 0 failures, 0 errors, 0 skipped.
- Isolated architecture-samples CLI smoke: PASS.
- `git diff --check`: PASS at document preparation.

## Non-goals Preserved

- No CLI/UI.
- No MCP dependency or source/state change.
- No remote review service.
- No authenticated signatures.
- No file/artifact deletion.

## Known Limitations

- Bundle integrity proves content consistency, not reviewer identity.
- Reviewer comments and Markdown are stored unencrypted.
- Expected-integrity replacement is not distributed locking.
- Exact documentation fingerprints intentionally reject benign byte changes.
- Apply returns an in-memory result; final writer transactionality remains external.
- Automatic format migration and durable apply receipts are deferred.

## Git Integration Status

- Feature Worktree: `C:\WorkSpace\docpilot-rfc-0047`
- Feature Branch: `feature/rfc-0047-review-bundle-persistence`
- Baseline: `084f1c2ac8a7efbed5a2c3837d9e76848a274149`
- Feature Commit: PENDING
- Main merge: NOT PERFORMED
- Push: NOT PERFORMED
