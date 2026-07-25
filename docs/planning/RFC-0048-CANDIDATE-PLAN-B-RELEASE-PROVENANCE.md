# RFC-0048 Candidate Plan B: v0.5 Release Provenance

## Status

PROPOSED - NOT APPROVED AS RFC-0048

## Proposed title

v0.5 Release Provenance and Determinism Gate

## Type

QUALITY_RELEASE

## Problem

Build, test, CLI smoke, compatibility, and Git evidence are distributed across
handoffs. There is no single versioned machine-verifiable release record binding
the evidence to one Core commit.

## Product outcome

A release decision can consume one deterministic manifest proving source identity,
compatibility versions, test totals, smoke artifacts, and protected-scope checks.

## Goals

1. Define Release Evidence Manifest format version 1.
2. Bind evidence to exact Core and embedded MCP identities.
3. Record DIR, specification snapshot, and Review Bundle format versions.
4. Record build/test aggregate and required CLI smoke artifacts.
5. Verify artifact SHA-256 values.
6. Fail on missing, stale, inconsistent, or corrupted evidence.
7. Produce deterministic machine and Markdown reports.

## Non-goals

- Automatic push, tag, GitHub Release, or package publication.
- Runtime Core behavior changes.
- CI vendor migration.
- CLI review workflow.
- MCP product expansion.
- Online provider certification unless separately approved.

## Proposed manifest

```text
formatVersion
coreCommit
mcpMode
mcpCommit
dirSchemaVersion
specificationSnapshotFormat
reviewBundleFormat
buildResult
testAggregate
cliSmokeArtifacts[]
compatibilityChecks[]
protectedScopeChecks[]
payloadSha256
```

Timestamps are informational and excluded from deterministic identity.

## Architecture

```text
official verification commands
        ->
evidence collectors
        ->
canonical manifest
        ->
release gate validator
        ->
PASS / FAIL report
```

This is build/release tooling. Core runtime must not depend on it.

## Expected change areas

- release verification tooling/scripts
- versioned evidence schema and codec
- fixture-driven validator tests
- release-readiness documentation
- minimal build configuration only when explicitly required

## Verification

- exact commit acceptance and stale commit rejection;
- missing artifact and checksum mismatch rejection;
- deterministic manifest bytes;
- test failure/error/skipped policy;
- Review Bundle v1 compatibility recording;
- embedded MCP identity;
- Windows path normalization;
- offline reproducibility;
- full regression.

## Risks

- Platform-specific build evidence.
- Cached tests may be misrepresented without a strict policy.
- Timestamps can break determinism.
- Evidence proves execution, not semantic correctness.

## Complexity

MEDIUM

## Recommendation

STRONGLY_RECOMMENDED if v0.5 release readiness is more urgent than exposing the
new review capability to end users.

## Decisions required

1. Approve Plan B as RFC-0048.
2. Approve Release Evidence Manifest v1.
3. Define skipped/cached test policy.
4. Decide whether provider smoke is mandatory.
5. Confirm publication and tagging remain manual.
