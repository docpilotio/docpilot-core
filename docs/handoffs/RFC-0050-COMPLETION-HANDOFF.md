# RFC-0050 Completion Handoff

## RFC identity

- ID: RFC-0050
- Title: Review Bundle Lifecycle and Apply Receipt
- Feature branch: `feature/rfc-0050-review-lifecycle-spec`
- Baseline commit: `d674463c078125b3d113823a90a49c26cb77b139`
- Status: implementation and local verification complete; Git integration pending

## Implementation summary

RFC-0050 adds Core-owned, versioned Lifecycle Metadata, Apply Receipt, and Apply
Transaction Journal contracts while preserving Review Bundle format 1.

The filesystem repository publishes immutable control generations through an
atomic `CURRENT` pointer. An APPLIED generation contains its Receipt before the
pointer is switched, so readers cannot observe APPLIED without the matching
Receipt.

The Core apply workflow owns bundle/lifecycle concurrency checks, complete-review
validation, reviewed-base validation, document compare-and-swap replacement,
journal progression, receipt construction, atomic control publication,
idempotent exact retry, post-apply change blocking, and deterministic recovery.

## Changed production areas

- Core lifecycle, receipt, journal models and codecs.
- Immutable file lifecycle repository.
- Documentation resource port and local file adapter.
- Apply transaction/recovery workflow.
- Supersession, archive, and offline audit verification.
- Persistent review preparation and decision lifecycle synchronization.
- Thin CLI apply delegation to Core.

## Contract compatibility

- Review Bundle: format 1 unchanged.
- Lifecycle Metadata: new independent format 1.
- Apply Receipt: new independent format 1.
- Apply Transaction Journal: new independent format 1.
- DIR schema: 0.3 unchanged.
- Specification Snapshot: format 1 unchanged.
- CLI JSON envelope: format 1 unchanged.
- MCP source and project state: unchanged.

## Verification

- Targeted lifecycle codec, atomic generation, apply, idempotency, stale-document,
  crash recovery, persisted bundle, and CLI workflow tests: PASS.
- Clean build: PASS.
- Full tests: 96 XML / 291 tests / 0 failures / 0 errors / 0 skipped.
- Diff check: PASS.

## Recovery behavior

- Input hash with PREPARED journal: rolls back to ACTIVE and permits explicit
  retry.
- Result hash with incomplete control commit: rolls forward to APPLIED with the
  staged Receipt.
- Any third document hash or contradictory journal state: transitions to
  RECOVERY_REQUIRED and fails closed.

## Known limitations

- Cross-process leases are not yet provided; generation compare-and-swap is the
  durable conflict boundary.
- Generation retention is intentionally unbounded and no automatic deletion is
  performed.
- The CLI does not yet expose dedicated lifecycle recovery, supersede, archive,
  or offline verification subcommands.
- Transaction history is retained; automatic compaction is deferred.

## Completion readiness

- Core implementation: READY.
- Regression verification: READY.
- Canonical documentation: READY.
- Feature commit: PENDING.
- Main integration: NOT PERFORMED.
- Push: NOT PERFORMED.
- Release: NOT PERFORMED.

## Canonical sources

- `docs/rfc/RFC-0050-Review-Bundle-Lifecycle-and-Apply-Receipt.md`
- `docs/planning/RFC-0050-MAIN-PLANNING-UPDATE.md`
- `docs/roadmap/ROADMAP.md`
- `docs/handoffs/RFC-0050-COMPLETION-HANDOFF.md`

## Next RFC candidate material

- Plan A: `docs/planning/RFC-0051-CANDIDATE-PLAN-A-OFFICIAL-LIFECYCLE-OPERATIONS-CLI.md`
- Plan B: `docs/planning/RFC-0051-CANDIDATE-PLAN-B-CROSS-PROCESS-REVIEW-LEASES-AND-RETENTION.md`
- Decision packet: `docs/planning/RFC-0051-TWO-PLAN-SYNC-PACKET.md`

Plan A is recommended, but RFC-0051 is not formally selected by this handoff.
