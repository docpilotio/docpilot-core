# RFC-0047 Candidate Plan B: v0.5 Release Provenance

## Candidate status

PROPOSED - NOT APPROVED AS RFC-0047

## Proposed title

v0.5 Release Provenance and Determinism Gate

## Type

QUALITY_RELEASE

## Why this follows RFC-0046

Core now covers relationship semantics, relationship-aware incremental review,
explicit reviewed removal, snapshot compatibility, and isolated CLI smoke. The
remaining release risk is that evidence is distributed across handoffs and command
output rather than one versioned, machine-verifiable release record.

## Product outcome

A release decision can be based on one deterministic manifest proving source
identity, compatibility versions, build/test totals, required smoke artifacts,
and protected-scope checks.

## Goals

1. Define a versioned release evidence manifest.
2. Bind evidence to the exact Core commit and embedded MCP commit identity.
3. Record DIR schema, snapshot format, provider modules, build/test aggregate, and CLI smoke.
4. Verify required artifact existence and SHA-256 values.
5. Fail when evidence is missing, stale, inconsistent, or non-deterministic.
6. Produce deterministic human-readable and machine-readable reports.
7. Keep release verification outside Core runtime behavior.

## Non-goals

- Automatic Git push, tag, GitHub Release, or package publication.
- CI vendor migration.
- Runtime feature changes.
- Review persistence or CLI review decisions.
- MCP product expansion.
- Network-dependent provider certification unless separately requested.

## Proposed manifest

```text
ReleaseEvidenceManifest
  formatVersion
  coreCommit
  mcpMode
  mcpCommit
  dirSchemaVersion
  specificationSnapshotFormat
  buildResult
  testAggregate
  cliSmokeArtifacts[]
  compatibilityChecks[]
  protectedScopeChecks[]
  generatedAt (informational, excluded from deterministic identity)
  payloadSha256
```

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

The gate is build/release tooling. Core runtime must not depend on it.

## Expected change areas

- release verification scripts or dedicated tooling module
- canonical release evidence schema
- deterministic manifest codec
- fixture-based verifier tests
- release-readiness documentation

Build configuration changes require explicit review and must remain minimal.

## Public API impact

None expected in Core runtime.

## Schema and snapshot impact

- New release evidence format: version `1`.
- DIR schema: unchanged.
- Specification snapshot format: unchanged.

## Verification

- correct commit and version acceptance;
- stale commit rejection;
- missing artifact rejection;
- hash mismatch rejection;
- test failure/error/skipped policy;
- deterministic manifest identity;
- embedded MCP identity recording;
- Windows path normalization;
- offline reproducibility;
- full project regression.

## Risks

- Build tooling can become platform-specific.
- Cached tests can be misrepresented unless evidence policy is explicit.
- Timestamps can break deterministic identity.
- A manifest can prove recorded execution, not semantic correctness by itself.

## Complexity

MEDIUM

## Recommended sequencing

```text
RFC-0046 integrated
  -> freeze v0.5 compatibility contracts
  -> evidence schema and canonical identity
  -> collectors and verifier
  -> local release-candidate dry run
  -> explicit release decision
```

## Decision gates

1. Approve release readiness as the RFC-0047 priority.
2. Approve release evidence format version 1.
3. Define skipped-test and cached-test policy.
4. Define whether provider runtime smoke is required.
5. Confirm publication/tagging remain manual and out of scope.

## Recommendation

STRONGLY_RECOMMENDED if the immediate goal is to close v0.5 MVP/POC release
readiness before adding another user workflow.
