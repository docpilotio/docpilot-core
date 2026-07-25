# RFC-0050 Candidate Plan A: Review Bundle Lifecycle and Apply Receipt

## Status

SELECTED AND SPECIFIED AS RFC-0050

## Type

PRODUCT_CAPABILITY / ARCHITECTURE_ENABLER

## Problem

RFC-0047 persists review decisions and RFC-0048 resumes and applies them through
the official CLI. After a successful apply, the durable contract does not yet
record a terminal proof of what was applied, which source Bundle produced it, or
whether a Bundle has been superseded, archived, or already consumed.

Long-running projects therefore have integrity-protected proposals but no
Core-owned lifecycle or durable Apply Receipt for audit and idempotency.

## Product outcome

A project can retain an auditable chain from proposal through decisions to one
terminal apply result:

```text
ACTIVE
  -> SUPERSEDED | APPLIED
  -> ARCHIVED
```

Successful apply creates an immutable receipt binding the exact proposal,
decision payload, reviewed base, input documentation, and result documentation.

## Goals

1. Define Core-owned Review Bundle lifecycle semantics.
2. Define Apply Receipt format version 1.
3. Bind receipts to proposal ID and exact Bundle payload SHA-256.
4. Bind reviewed, apply-input, and result documentation SHA-256 values.
5. Record accepted/rejected operations in canonical target order.
6. Make successful apply and receipt persistence atomic and fail-closed.
7. Make repeated apply idempotent when exact identity and result match.
8. Reject application of superseded, archived, corrupt, or conflicting Bundles.
9. Preserve deterministic encoding and offline verification.
10. Expose lifecycle and receipt through provider-neutral Core contracts.

## Candidate contracts

Apply Receipt:

```text
applyReceiptFormatVersion
receiptId
proposalId
bundlePayloadSha256
reviewedDocumentationSha256
applyInputDocumentationSha256
resultDocumentationSha256
acceptedTargets[]
rejectedTargets[]
appliedOperations[]
integrity
```

Lifecycle metadata:

```text
state
supersededByProposalId
applyReceiptId
archiveReason
integrity
```

No timestamp is required for canonical identity.

## Architecture value

- closes the review audit chain inside Core;
- makes apply retry and crash recovery explicit;
- prevents a Bundle from being silently reused after terminal apply;
- supplies the durable audit foundation expected for the v1.0 path;
- reuses RFC-0046 removal, RFC-0047 persistence, and RFC-0048 thin CLI boundary.

## Non-goals

- file deletion or project-wide cleanup;
- retention scheduling or automatic archive deletion;
- interactive UI/TUI or remote collaboration;
- reviewer authentication or digital signatures;
- MCP commands or MCP persistence;
- Git commit, tag, push, or release behavior;
- Release Evidence Manifest changes;
- cross-project Bundle transfer.

## Expected change areas

```text
Core review lifecycle models and evaluator
Apply Receipt model, codec, integrity, repository
PersistentDocumentationReviewWorkflow
Review Bundle repository atomic transaction boundary
Core status/query contracts
Focused Core persistence and recovery tests
CLI thin-adapter exposure only if separately included
```

## Compatibility

- existing Review Bundle format 1 remains readable;
- format changes require explicit migration or additive lifecycle storage;
- DIR schema and Specification Snapshot remain unchanged;
- Core owns lifecycle and receipt semantics;
- CLI and MCP cannot duplicate them.

## Risks

- atomicity across Bundle lifecycle and Receipt files;
- ambiguity between superseded and rejected proposals;
- retry behavior after documentation write succeeds but receipt persistence fails;
- receipt identity becoming host- or time-dependent;
- accidental retention policy expansion.

## Verification

- deterministic receipt bytes and identity;
- corrupt, missing, or mismatched receipt;
- accepted UPSERT and REMOVE receipts;
- mixed accepted/rejected decisions;
- exact repeated apply idempotency;
- stale input and changed Bundle conflict;
- superseded and archived apply rejection;
- crash-boundary recovery scenarios;
- concurrent terminal transition;
- RFC-0046 through RFC-0049 regression.

## Priority

STRONGLY_RECOMMENDED for RFC-0050.

It naturally follows RFC-0049: v0.5 gains trustworthy release evidence, then the
v1.0 path gains durable review audit and long-term operation.

## Decision

Plan A is approved and specified as RFC-0050.

Canonical specification:

```text
docs/rfc/RFC-0050-Review-Bundle-Lifecycle-and-Apply-Receipt.md
```

Approved contract direction:

- Review Bundle format 1 unchanged;
- separate Lifecycle Metadata format 1;
- separate Apply Receipt format 1;
- separate Apply Transaction Journal format 1;
- atomic Receipt/APPLIED lifecycle visibility through an immutable control
  generation pointer;
- recoverable cross-resource documentation transaction;
- Core-only lifecycle, receipt, idempotency, and recovery rules.
