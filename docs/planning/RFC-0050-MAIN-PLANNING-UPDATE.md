# RFC-0050 Main Planning Update

## Project Dashboard

| Item | State |
| --- | --- |
| Milestone path | v1.0 auditability and long-term operation |
| Current RFC | RFC-0050 |
| Title | Review Bundle Lifecycle and Apply Receipt |
| Specification | APPROVED |
| Implementation | COMPLETED |
| Verification | 291 tests / 0 failures |
| Main integration | NOT_STARTED |

## Current phase

RFC-0049 is implemented, verified, integrated into `main`, and synchronized to
`origin/main`. RFC-0050 Plan A is selected and its detailed Canonical
specification is approved.

RFC-0050 implementation and local verification are complete on the feature
worktree. Git integration, push, tag, and release remain pending.

## Approved product outcome

Core will own an auditable review lifecycle from Bundle creation through one exact
applied result:

```text
ACTIVE -> APPLYING -> APPLIED
ACTIVE -> SUPERSEDED
ACTIVE | APPLIED | SUPERSEDED -> ARCHIVED
APPLYING -> RECOVERY_REQUIRED when exact recovery is ambiguous
```

Successful apply produces one immutable Apply Receipt. Exact retry returns the
same Receipt without rewriting documentation.

## Contract versions

| Contract | Version | Decision |
| --- | ---: | --- |
| Review Bundle | 1 | unchanged |
| Lifecycle Metadata | 1 | new independent contract |
| Apply Receipt | 1 | new independent contract |
| Apply Transaction Journal | 1 | new independent recovery contract |

DIR `0.3`, Specification Snapshot `1`, CLI JSON `1`, and Release Evidence
Manifest `1` remain unchanged.

## Transaction architecture

Receipt and APPLIED lifecycle are written into one immutable control generation.
An atomic `CURRENT` pointer switch makes them visible together.

Documentation replacement cannot share a portable hardware transaction with the
control directory. Core therefore owns a write-ahead Journal and exact-hash
recovery protocol:

```text
PREPARED
  -> DOCUMENT_REPLACED
  -> CONTROL_COMMITTED
  -> COMPLETED
```

Readers and mutations recover incomplete transactions before exposing aggregate
state. Ambiguous document bytes fail closed as `RECOVERY_REQUIRED`.

## Architecture boundaries

Core owns:

- lifecycle transitions;
- Receipt construction and identity;
- transaction and recovery state;
- idempotency;
- offline verification;
- aggregate concurrency.

CLI/MCP own only:

- arguments and path/resource adapters;
- invocation;
- structured result presentation or transport.

Core must not depend on CLI, MCP, provider implementations, or release tooling.

## Implementation stages

1. Lifecycle, Receipt, and Journal models plus strict canonical codecs.
2. Offline cross-contract verifier.
3. Immutable control-generation repository and atomic pointer.
4. Review Aggregate repository and explicit legacy Bundle adoption.
5. DocumentationResource port and local compare-and-swap adapter.
6. Apply transaction coordinator and deterministic recovery.
7. Decision-update aggregate transaction.
8. Supersession and archive transitions.
9. Thin CLI status/apply mapping where required.
10. Crash-injection, concurrency, regression, build, and smoke verification.
11. Completion Handoff and Roadmap synchronization from actual evidence.

## Verification priorities

- Review Bundle v1 byte compatibility;
- APPLIED never visible without Receipt;
- failure injection after every durable transaction step;
- input/result/neither document recovery matrix;
- idempotent repeated apply;
- post-apply user change protection;
- stale Bundle and lifecycle generation conflicts;
- no deletion during archive;
- offline verification without providers or network;
- no MCP source or state change.

## Implementation evidence

- Core models and deterministic codecs for Lifecycle Metadata v1, Apply Receipt
  v1, and Apply Transaction Journal v1.
- Immutable generation repository with atomic `CURRENT` pointer publication.
- Core-owned `DocumentationResource`, apply coordinator, exact retry idempotency,
  post-apply conflict blocking, and input/result/neither recovery.
- Core-owned supersede, archive, and offline verification services.
- Persistent review preparation and decision mutation keep lifecycle observation
  synchronized.
- Official CLI apply delegates document mutation and Receipt generation to Core.
- Targeted Review lifecycle, persistence, and CLI workflow tests pass.
- Clean build: PASS.
- Full test aggregate: 96 XML / 291 tests / 0 failures / 0 errors / 0 skipped.
- `git diff --check`: PASS.

## Known risks

- falsely claiming cross-filesystem strict atomicity;
- pointer fallback exposing torn control state;
- recovery after document-first commit;
- stale proposal-scoped leases;
- decision update and lifecycle payload mismatch;
- unbounded retained generations.

The current file repository uses JVM-local compare-and-swap checks and atomic
same-directory replacement. Cross-process locking and automatic retention remain
future hardening work.

## Deferred

- signatures and authenticated identity;
- external release attestation;
- automatic retention/deletion;
- remote review synchronization;
- UI/TUI;
- MCP-owned state;
- Review Bundle v2.

## Canonical sources

- `docs/rfc/RFC-0050-Review-Bundle-Lifecycle-and-Apply-Receipt.md`
- `docs/planning/RFC-0050-CANDIDATE-PLAN-A-REVIEW-BUNDLE-LIFECYCLE-AND-APPLY-RECEIPT.md`
- `docs/planning/RFC-0050-CANDIDATE-PLAN-B-SIGNED-RELEASE-EVIDENCE-AND-ATTESTATION.md`
- `docs/planning/RFC-0050-TWO-PLAN-SYNC-PACKET.md`
- `docs/roadmap/ROADMAP.md`
