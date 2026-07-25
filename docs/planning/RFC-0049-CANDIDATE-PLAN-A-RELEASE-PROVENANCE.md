# RFC-0049 Candidate Plan A: v0.5 Release Provenance and Determinism Gate

## Status

PROPOSED - NOT APPROVED AS RFC-0049

## Type

QUALITY_RELEASE

## Problem

RFC-0048 completes the durable review workflow from Core through CLI. Build, test,
CLI smoke, compatibility, and Git evidence remain distributed across handoffs and
console logs. There is no single machine-verifiable release record bound to one
commit.

## Product outcome

A release decision consumes one deterministic manifest proving exact source
identity, compatibility formats, test totals, required smoke artifacts, and
protected-scope checks.

## Goals

1. Define Release Evidence Manifest format version 1.
2. Bind evidence to exact Core and embedded MCP identities.
3. Record DIR, snapshot, Review Bundle, and CLI JSON format versions.
4. Record build/test results and stable CLI exit-code contract verification.
5. Record required CLI smoke artifacts and SHA-256 values.
6. Fail on missing, stale, inconsistent, or corrupted evidence.
7. Produce deterministic JSON and Markdown gate reports.
8. Operate offline after dependencies are available.

## Non-goals

- Automatic Git push, tag, release, or publication.
- Runtime Core feature changes.
- CI vendor migration.
- Online provider certification by default.
- MCP product expansion.
- Review Bundle lifecycle changes.

## Manifest candidates

```text
formatVersion
coreCommit
mcpMode
mcpCommit
dirSchemaVersion
specificationSnapshotFormat
reviewBundleFormat
cliOutputFormat
stableExitCodeContract
buildResult
testAggregate
cliSmokeArtifacts[]
compatibilityChecks[]
protectedScopeChecks[]
payloadSha256
```

## Architecture

Release verification tooling depends on public build/runtime contracts. Core
runtime does not depend on release tooling.

## Verification

- exact/stale commit handling;
- deterministic manifest bytes;
- missing artifact and checksum failures;
- failure/error/skipped policy;
- cached-test evidence policy;
- review CLI text/JSON/exit-code smoke;
- Windows path normalization;
- embedded MCP identity;
- offline repeated execution.

## Risk

MEDIUM

## Recommendation

STRONGLY_RECOMMENDED. The product workflow is now broad enough that closing release
evidence is the most valuable bounded next step.

## Decisions required

1. Approve Plan A as RFC-0049.
2. Approve Release Evidence Manifest v1.
3. Define cached/skipped test policy.
4. Decide whether live provider smoke is mandatory.
5. Confirm publication remains manual.
