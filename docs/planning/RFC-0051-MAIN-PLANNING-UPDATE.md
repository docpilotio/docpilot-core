# RFC-0051 Main Planning Update

## Project dashboard

| Item | State |
| --- | --- |
| Milestone path | v1.0 auditability and safe operation |
| Current RFC | RFC-0051 |
| Selected candidate | Plan A |
| Title | Official Review Lifecycle Operations and Recovery CLI |
| Specification | APPROVED |
| Implementation | COMPLETED |
| Verification | 97 XML / 301 tests / 0 failures |
| Main integration | NOT_STARTED |

## Baseline

- Main HEAD: `60704a254f7d90a0ea9c00e9490d06bf6e917b26`
- RFC-0050 implementation: COMPLETE
- RFC-0050 main integration: COMPLETE
- Verified tests: 96 XML / 291 tests / 0 failures
- Review Bundle: format 1
- Lifecycle Metadata: format 1
- Apply Receipt: format 1
- Apply Transaction Journal: format 1

## Approved outcome

Expose RFC-0050 lifecycle status, offline verification, recovery, supersession,
and archive through stable official CLI commands while preserving Core ownership
of every lifecycle rule.

Mutation commands are dry-run by default and require explicit `--confirm`.

## Required Core preparation

RFC-0050 direct mutation services must be wrapped by a Core application boundary
that provides:

- read-only status;
- read-only offline verification;
- deterministic operation Plan;
- Plan SHA;
- confirm-time full revalidation;
- structured semantic results for CLI mapping.

CLI must not switch on lifecycle states to select behavior.

## Implementation evidence

- `ReviewLifecycleOperations` is the Core application boundary for status,
  verification, operation planning, and confirmed execution.
- deterministic Plan format 1 and Plan SHA bind action, project, proposal,
  Bundle SHA, generation, state, result state, transaction, Receipt, replacement,
  recovery disposition, and relevant document hashes;
- Core classifies recovery as roll-forward, rollback, already-applied, or blocked;
- Core re-plans and checks optional approved Plan SHA before every confirmation;
- official CLI implements all five lifecycle commands;
- mutation commands default to side-effect-free dry-run;
- `--dry-run` and `--confirm` are mutually exclusive;
- `--plan-sha256` requires `--confirm`;
- no CLI production source references `ReviewLifecycleState`, lifecycle codecs,
  control JSON filenames, or repository `transition`;
- focused Core and CLI tests pass;
- clean build: PASS;
- full regression: 97 XML / 301 tests / 0 failures / 0 errors / 0 skipped;
- `git diff --check`: PASS.

## Implementation stages

1. Core status and verification request/result contracts.
2. Core operation Plan model and deterministic Plan SHA.
3. Recovery planning without mutation.
4. Confirmed recovery with Plan/generation revalidation.
5. Supersede and archive planning plus confirmed execution.
6. Official lifecycle CLI grammar and common selection.
7. Deterministic text/JSON renderers and stable exit codes.
8. Core boundary and no-rule-duplication tests.
9. CLI restart, dry-run, confirm, conflict, and tamper tests.
10. Isolated filesystem smoke.
11. Full build/test and Completion Handoff.

## Release readiness impact

RFC-0051 improves operational accessibility and automation safety. It does not
change v0.5 Release Evidence or create a release by itself.

## Deferred

- cross-process leases;
- retention and deletion;
- UI/TUI;
- MCP lifecycle operations;
- remote review synchronization;
- signatures and reviewer identity;
- automatic or implicit confirmation.

## Known limitations

- Plan format 1 is an in-process/CLI concurrency token and is not persisted.
- Cross-process leases remain deferred; immutable generation checks are the
  durable conflict boundary.
- Lifecycle status reports transaction evidence only for an active transaction.
- No separate interactive prompt is provided; explicit `--confirm` is the only
  approval mechanism.

## Canonical sources

- `docs/rfc/RFC-0051-Official-Review-Lifecycle-Operations-and-Recovery-CLI.md`
- `docs/planning/RFC-0051-CANDIDATE-PLAN-A-OFFICIAL-LIFECYCLE-OPERATIONS-CLI.md`
- `docs/planning/RFC-0051-CANDIDATE-PLAN-B-CROSS-PROCESS-REVIEW-LEASES-AND-RETENTION.md`
- `docs/planning/RFC-0051-TWO-PLAN-SYNC-PACKET.md`
- `docs/roadmap/ROADMAP.md`
