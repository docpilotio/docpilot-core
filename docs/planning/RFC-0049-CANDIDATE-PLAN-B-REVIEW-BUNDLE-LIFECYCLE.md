# RFC-0049 Candidate Plan B: Review Bundle Lifecycle and Apply Receipt

## Status

DEFERRED - RECOMMENDED RFC-0050 CANDIDATE, NOT YET APPROVED

## Type

PRODUCT_CAPABILITY / ARCHITECTURE_ENABLER

## Problem

RFC-0047 persists proposals and decisions, and RFC-0048 applies them through CLI.
Bundles do not have durable apply receipts, retention state, supersession links,
or an explicit safe archival lifecycle.

## Product outcome

A developer can prove which approved result was produced, distinguish active from
superseded reviews, and archive completed bundles without confusing archival with
automatic deletion.

## Goals

1. Define an integrity-protected Apply Receipt format.
2. Bind source bundle checksum, result documentation checksum, accepted/rejected targets,
   and application outcome.
3. Define bundle lifecycle states without mutating Review Bundle v1 semantics.
4. Link superseding proposals deterministically.
5. Provide explicit archive operations and read-only history queries.
6. Preserve existing bundles and receipts across restart.

## Non-goals

- Automatic retention deletion.
- Remote synchronization.
- Authenticated signatures.
- Multi-user approval.
- Review Bundle v1 field mutation.
- MCP ownership.
- General event-sourcing platform.

## Candidate model

```text
ReviewApplyReceipt v1
  proposalId
  sourcePayloadSha256
  reviewedDocumentationSha256
  resultDocumentationSha256
  acceptedTargetIds[]
  rejectedTargetIds[]
  outcome
  integrity
```

Lifecycle metadata is a separate versioned record so valid Review Bundle v1 files
remain immutable and readable.

## Architecture

Core owns receipt/lifecycle models, codecs, integrity, and repositories. CLI may
later expose read-only history and explicit archive commands as thin adapters.

## Verification

- deterministic receipt identity and round-trip;
- receipt tamper detection;
- result hash binding;
- supersession chain validation and cycle rejection;
- active/completed/archived query;
- archive without deletion;
- restart persistence;
- RFC-0047/0048 regression.

## Risk

MEDIUM-HIGH

## Recommendation

RECOMMENDED after release provenance unless audit/history is the immediate product
priority.

## Decisions required

1. Approve Plan B as RFC-0049.
2. Decide whether apply receipts are mandatory or optional.
3. Define lifecycle states and supersession policy.
4. Confirm archive never means automatic delete.
5. Confirm reviewer identity/signatures remain deferred.
