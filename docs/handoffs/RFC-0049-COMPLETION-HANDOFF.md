# RFC-0049 Completion Handoff

## RFC identity

- RFC: RFC-0049
- Title: v0.5 Release Provenance and Determinism Gate
- Baseline: `2d73b0b3e57be9fdb04ff1c02ccb29ea7dd60aa2`
- Feature branch: `feature/rfc-0049-release-provenance-spec`
- Status: implementation completed and locally verified

## Implementation summary

RFC-0049 adds the independent `docpilot-release` module. Core runtime, Provider
SPI, CLI runtime, and embedded MCP do not depend on this module.

Implemented capabilities:

- Release Evidence Manifest format 1;
- strict, deterministic JSON encoding and decoding;
- SHA-256 canonical payload integrity;
- fail-closed `DOCPILOT_V0_5` policy evaluation;
- stable ordered gate failures;
- deterministic Markdown release report;
- hardened JUnit XML aggregation and freshness evidence;
- clean Git candidate and operation-state inspection;
- artifact path, size, and SHA-256 collection and verification;
- documentation synchronization checks;
- atomic no-overwrite evidence publication;
- collection-time before/after repository identity check;
- offline manifest, source, artifact, policy, and documentation verification;
- stable release verifier exit categories.

## Build evidence

- `gradlew.bat clean build`: PASS
- `gradlew.bat clean test --rerun-tasks`: PASS
- Gradle cache was explicitly bypassed for final test execution.
- Kotlin daemon access was unavailable in the sandbox; Gradle automatically used
  its fallback compiler and completed successfully.

## Test evidence

Latest full-suite aggregate before final documentation-only synchronization:

```text
XML files: 95
Tests: 287
Failures: 0
Errors: 0
Skipped: 0
```

RFC-0049 adds 14 tests across 6 test classes.

Focused regression:

- RFC-0046 `ManagedBlockRemovalReviewTest`: PASS
- RFC-0047 `ReviewBundlePersistenceTest`: PASS
- RFC-0048 `ReviewCommandWorkflowTest`: PASS
- RFC-0049 focused suite: PASS

## Compatibility

- DIR schema remains `0.3`.
- Specification Snapshot format remains `1`.
- Review Bundle format remains `1`.
- CLI JSON output format remains `1`.
- RFC-0048 review exit codes remain unchanged.
- Provider SPI remains unchanged.
- MCP source and tests are unchanged.
- Core runtime has no dependency on `docpilot-release`.

## Changed areas

```text
settings.gradle.kts
docpilot-release/**
docs/rfc/RFC-0049-v0.5-Release-Provenance-and-Determinism-Gate.md
docs/planning/RFC-0049-*
docs/handoffs/RFC-0049-COMPLETION-HANDOFF.md
docs/roadmap/ROADMAP.md
```

## Known limitations

- Final Release Evidence Manifest cannot be collected until RFC-0049 has an exact
  clean commit.
- Current CLI surface exposes offline `verify`; collection is exposed as the
  public `ReleaseCollectionCoordinator` so project automation can supply explicit
  command and artifact plans without an additional competing plan-file format.
- Live provider smoke is not run and is optional by policy.
- Artifact signing, reviewer authentication, SBOM/SLSA, remote storage, and
  reproducible archive certification remain out of scope.
- Sandbox Kotlin daemon marker writes fail; fallback compilation succeeds.

## Completion readiness

RFC-0049 code, tests, Canonical RFC, Planning, Roadmap, and this Handoff are
internally synchronized. Feature commit, final clean-commit collection, main
integration, push, tag, and release are not performed by this handoff.

## Git integration status

- Feature commit: NOT CREATED
- Main merge: NOT PERFORMED
- Push: NOT PERFORMED
- Tag: NOT PERFORMED
- Release: NOT PERFORMED

## Follow-up

After final RFC-0049 integration and v0.5 release evidence, Review Bundle Lifecycle
and Apply Receipt remains the recommended RFC-0050 candidate.
