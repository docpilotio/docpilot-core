# RFC-0049 Integration Handoff Addendum

## Repository

- Feature worktree:
  `C:\WorkSpace\docpilot-rfc-0045-discovery\rfc0049-spec-worktree`
- Feature branch: `feature/rfc-0049-release-provenance-spec`
- Baseline: `2d73b0b3e57be9fdb04ff1c02ccb29ea7dd60aa2`
- Implementation commit: `3dd0fdb1d4202d1800d6681f3647776124037b43`
- Main integration: pending at document creation
- Push: pending at document creation

## RFC

- ID: RFC-0049
- Title: v0.5 Release Provenance and Determinism Gate
- Implementation: completed
- Local verification: completed
- Release candidate publication: not performed

## Implementation

RFC-0049 adds the independent `docpilot-release` module and preserves one-way
dependency direction. It implements:

- Release Evidence Manifest format 1;
- canonical deterministic JSON and strict decoding;
- SHA-256 payload integrity;
- binary fail-closed `DOCPILOT_V0_5` policy;
- deterministic Markdown release reports;
- strict, hardened JUnit XML aggregation;
- clean Git candidate and embedded MCP identity evidence;
- artifact path, size, producer, and SHA-256 evidence;
- documentation and protected-scope checks;
- atomic no-overwrite evidence publication;
- public collection coordination and offline verification.

Core runtime, provider modules, the official CLI, and embedded MCP do not depend
on the release module.

## Verification

- RFC-0049 focused tests: PASS
- RFC-0046 managed-block removal regression: PASS
- RFC-0047 Review Bundle persistence regression: PASS
- RFC-0048 CLI Review workflow regression: PASS
- `clean build --rerun-tasks`: PASS
- `clean test --rerun-tasks`: PASS
- Test XML files: 95
- Tests: 287
- Failures: 0
- Errors: 0
- Skipped: 0
- `git diff --check`: PASS
- Reverse Core/CLI/Provider dependency on `docpilot-release`: none
- MCP source/test changes: none

## Compatibility

- DIR schema: `0.3`, unchanged
- Specification Snapshot format: `1`, unchanged
- Review Bundle format: `1`, unchanged
- CLI JSON format: `1`, unchanged
- CLI review exit codes: unchanged
- MCP package: embedded and unchanged

## Generated artifacts

Sandbox Kotlin daemon marker writes were denied and Gradle used its successful
fallback compilation strategy. Generated `.kotlin/errors/**` files are excluded
through `.gitignore` and are not part of release evidence or a Git commit.

## Limitations

- Final v0.5 Release Evidence Manifest must be collected from the exact clean
  integrated commit; local pre-integration results cannot be copied into it.
- Live provider smoke was not run and remains optional.
- Signing, SBOM/SLSA, external attestation, and remote evidence storage remain
  deferred.
- Tag and release publication are not performed.

## Git integration decision

The user approved:

1. RFC-0049 main integration;
2. `origin/main` push;
3. Handoff persistence;
4. preparation of two follow-up RFC candidates.

The final merge and remote commit identities are reported after integration.
