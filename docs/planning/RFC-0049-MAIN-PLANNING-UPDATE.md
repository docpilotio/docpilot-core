# RFC-0049 Main Planning Update

## Project Dashboard

| Item | State |
| --- | --- |
| Current milestone | v0.5 MVP / POC |
| Current RFC | RFC-0049 |
| Title | v0.5 Release Provenance and Determinism Gate |
| Specification | APPROVED |
| Implementation | COMPLETED_AND_LOCALLY_VERIFIED |
| Release candidate | PENDING |

## Current phase

RFC-0048 is implemented, verified, integrated into `main`, and synchronized to
`origin/main`. RFC-0049 Plan A is selected and its detailed Canonical
specification is approved.

RFC-0049 implementation and local verification are complete. The next phase is an
exact feature commit, final clean-commit evidence collection, integration review,
and explicit main integration approval. This document does not authorize a tag,
release, or publication.

## Approved outcome

RFC-0049 will provide:

- Release Evidence Manifest format 1;
- exact clean Core commit and embedded MCP identity;
- deterministic clean build/test aggregation;
- mandatory official review CLI workflow smoke;
- public contract and protected-scope verification;
- artifact SHA-256 and stale-evidence rejection;
- deterministic JSON and Markdown release reports;
- binary, fail-closed `DOCPILOT_V0_5` release policy;
- offline verification of captured evidence.

## Architecture direction

Release evidence policy and canonical encoding remain independent from Git,
Gradle, filesystem, process, CLI, and MCP adapters.

Core runtime must not depend on:

```text
release verification tooling
Gradle execution
Git process adapters
MCP
```

## Approved policy decisions

- Cached, `UP-TO-DATE`, `NO-SOURCE`, or stale test results do not satisfy the
  clean-test gate.
- v0.5 requires zero failures, zero errors, and zero skipped tests.
- Deterministic fixture-provider CLI smoke is mandatory.
- Live provider smoke is optional.
- Manifest and Markdown output exclude timestamps and host-specific identity.
- The release gate has only PASS and FAIL.
- Git push, tag, publication, and release remain explicit manual actions.

## Implementation result

1. Added an independent `docpilot-release` application module.
2. Implemented versioned models, strict canonical JSON, SHA-256 integrity,
   deterministic Markdown, stable gate failures, and v0.5 policy.
3. Implemented Git candidate inspection, hardened JUnit aggregation, artifact
   collection, documentation synchronization, atomic evidence publication,
   collection coordination, and offline verification.
4. Added public collection orchestration and a stable `verify` command boundary.
5. Added deterministic, corruption, stale/cache, policy, atomicity, Git, and
   documentation tests.
6. Re-ran RFC-0046 removal, RFC-0047 persistence, and RFC-0048 CLI review tests.

## Completion readiness

Implementation completion requires every criterion in the Canonical RFC,
including deterministic repeated output, exact source binding, strict uncached
test evidence, offline verification, atomic publication, and full regression
success.

## Release readiness

| Gate | State |
| --- | --- |
| Core Build | PASS |
| Core Tests | PASS |
| CLI | PASS |
| Incremental | BASELINE PASS |
| Review Workflow | BASELINE PASS |
| architecture-samples Validation | BASELINE PASS; FINAL RC COLLECTION PENDING |
| Documentation Sync | PASS |
| Release Candidate | PENDING |

The final Release Evidence Manifest remains pending because exact evidence must be
collected from the clean RFC-0049 integration commit. Local verification results
must not be copied into that final manifest without collection.

## Known risks

- structured Gradle task outcome collection;
- deterministic and non-duplicating JUnit aggregation;
- repository mutation during long-running collection;
- host-specific data leaking into canonical output;
- improper module placement coupling Core runtime to tooling.

## Deferred work

Review Bundle Lifecycle and Apply Receipt is retained as the recommended RFC-0050
candidate. It is outside RFC-0049 and remains separately approvable.

## Canonical sources

- `docs/rfc/RFC-0049-v0.5-Release-Provenance-and-Determinism-Gate.md`
- `docs/planning/RFC-0049-CANDIDATE-PLAN-A-RELEASE-PROVENANCE.md`
- `docs/planning/RFC-0049-CANDIDATE-PLAN-B-REVIEW-BUNDLE-LIFECYCLE.md`
- `docs/planning/RFC-0049-TWO-PLAN-SYNC-PACKET.md`
- `docs/roadmap/ROADMAP.md`
