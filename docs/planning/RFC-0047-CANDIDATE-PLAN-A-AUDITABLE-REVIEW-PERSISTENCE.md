# RFC-0047 Candidate Plan A: Auditable Review Persistence

## Candidate status

PROPOSED - NOT APPROVED AS RFC-0047

## Proposed title

Auditable Review Persistence and Resumable Conflict-safe Apply

## Type

PRODUCT_CAPABILITY / ARCHITECTURE_ENABLER

## Why this follows RFC-0046

RFC-0046 binds a proposal to an exact documentation base, but the proposal and
decisions exist only in memory. A process restart loses the review session. Plan A
turns the existing safe in-process contract into a durable, resumable workflow
without moving state ownership to MCP.

## Product outcome

A developer can prepare a review, stop the process, inspect or edit a deterministic
review bundle, and later resume apply. Core rejects tampered, mismatched, incomplete,
or stale bundles before any documentation transformation.

## Goals

1. Define a versioned, provider-neutral review bundle format.
2. Persist proposal identity, reviewed-document SHA-256, entries, Evidence, and decisions.
3. Preserve explicit UPSERT/REMOVE operations.
4. Validate format version, canonical ordering, checksums, and proposal/decision identity.
5. Resume apply only against the exact reviewed documentation base.
6. Preserve complete-review-before-merge and accepted-only atomic transformation.
7. Provide repository and codec ports in Core with local adapter implementations.

## Non-goals

- Interactive CLI/UI commands.
- Reviewer authentication, cryptographic signatures, or remote identity.
- Remote review service.
- MCP-owned state or Core-to-MCP dependency.
- File/artifact deletion.
- Concurrent multi-review merge.

## Proposed model

```text
ReviewBundleEnvelope
  formatVersion
  proposalId
  createdFromSpecificationIdentity
  reviewedDocumentationSha256
  proposal
  decisions
  payloadSha256
```

`proposalId` is deterministically derived from the canonical proposal payload,
not generated from wall-clock time or randomness.

## Architecture

```text
Core review models
        ->
ReviewBundleCodec port
        ->
canonical JSON codec
        ->
ReviewBundleRepository port
        ->
local filesystem adapter
```

The review domain must not import CLI, MCP, or provider implementations.

## Expected change areas

- `incremental/specification/review` persistence models and ports
- canonical JSON codec and integrity validation
- local repository adapter
- workflow resume orchestration
- deterministic and corruption tests

## Public API impact

Additive review bundle, codec, repository, load-result, and resume contracts.
Existing transient preparation/apply remains valid.

## Schema and snapshot impact

- New review bundle format: version `1`.
- DIR schema: unchanged.
- Specification snapshot format: unchanged.

## Verification

- deterministic byte-identical round-trip;
- unknown version rejection;
- payload checksum mismatch rejection;
- proposal ID mismatch rejection;
- shuffled input canonicalization;
- incomplete decision persistence and resume;
- stale-document apply rejection after reload;
- REMOVE operation round-trip;
- corrupt/truncated file behavior;
- full build/test and isolated CLI smoke.

## Risks

- A new persistence format creates a long-lived compatibility obligation.
- Proposal IDs can become unstable if canonicalization is underspecified.
- Persisted reviewer comments may contain sensitive information.
- Local filesystem durability is not a distributed transaction.

## Complexity

MEDIUM-HIGH

## Recommended sequencing

```text
RFC-0046
  -> Review bundle model and canonical identity
  -> Codec and integrity validation
  -> Repository port and local adapter
  -> Resume/apply orchestration
  -> Later CLI review workflow
```

## Decision gates

1. Approve persistence as RFC-0047 product scope.
2. Approve JSON review bundle format version 1.
3. Decide whether reviewer comments are persisted by default.
4. Confirm CLI commands remain a later RFC.
5. Confirm MCP remains outside the runtime boundary.

## Recommendation

RECOMMENDED if resumable human review is the next product milestone.
