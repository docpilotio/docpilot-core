# RFC-0055 Main Planning Update

## Dashboard

| Item | State |
| --- | --- |
| Track | Product Capability |
| RFC | RFC-0055 |
| Title | Existing Documentation Reconciliation |
| v1.0 role | FINAL PRODUCT CAPABILITY RFC |
| Direction | APPROVED |
| Detailed specification | APPROVED |
| Implementation | COMPLETED |
| Verification | COMPLETED — 102 XML / 323 tests / 0 failures |
| Main integration | COMPLETED at `4e05018` |
| Release tag | `v1.0.0` pushed |

## Baseline

- Main: `3c1223d96496ab0ad029ad116c7592b50e491249`
- RFC-0053: implemented, verified, and integrated
- RFC-0054 Quality Validation: proposed, not approved
- DIR schema: `0.3`
- Snapshot format: `1`
- Verified suite: 100 XML / 312 tests / 0 failures

## Approved direction

Define Core-owned Evidence-based ownership and safe three-way reconciliation for
generated artifacts, existing user documents, and shared managed blocks.

## Architecture decisions

- Preview/dry-run is the default.
- Reconciliation uses reviewed BASE, CURRENT, and generated CANDIDATE.
- ownership requires manifest/marker/Receipt/hash Evidence.
- unknown ownership fails closed.
- user regions are byte-preserved.
- incremental scope is RFC-0052 impacted plus drifted artifacts.
- AI creates proposals only.
- Core emits a versioned Explanation Report for every material decision.
- all merge, ownership, conflict, and apply rules remain in Core.
- apply reuses RFC-0050 transaction/recovery semantics.
- whole-file deletion is excluded.

## Proposed implementation stages

1. Ownership and Evidence contracts.
2. Ownership Manifest format 1 and verifier.
3. managed-block parser and ownership-region model.
4. deterministic three-way reconciliation.
5. conflict model and structured diff.
6. Preview Plan format 1 and Plan SHA.
7. RFC-0052 incremental/drift selection.
8. AI proposal validation boundary.
9. complete reconciliation decisions.
10. atomic apply, result, idempotency, and recovery.
11. offline verification.
12. Decision Explanation Report and Evidence graph.
13. isolated existing-document fixture and Handoff.

## Completion gate

- no write during Preview;
- no unknown-owned overwrite;
- deterministic ownership and conflicts;
- byte-preserved user regions;
- exact incremental target selection;
- AI cannot choose operation or ownership;
- stale Plan/current/manifest blocking;
- atomic/recoverable apply;
- idempotent reapply;
- offline verification;
- stable-rule and Evidence-based explanation for every material decision;
- CLI/MCP unchanged.

## Current implementation evidence

- Core ownership, Manifest, managed-block parsing, Preview Plan, deterministic
  three-way reconciliation, conflicts, decisions, stale-current checks,
  idempotent store contract, and Explanation Report are implemented.
- Unknown ownership fails closed and unmanaged document regions remain
  byte-preserved.
- Plan, Manifest, and Explanation integrity tampering is rejected.
- Clean build and clean test pass: 102 XML / 323 tests / 0 failures / 0 errors /
  0 skipped.
- Format-1 Plan, Manifest, Result, and Explanation codecs verify offline.
- File-backed persistence uses a PREPARE journal and deterministic roll-forward
  recovery after document, manifest, or result write interruption.
- RFC-0052 KEEP/drift selection and RFC-0046 complete REMOVE approval are
  enforced by Core tests.
- Isolated user-document fixtures verify persistence, restart, recovery,
  idempotency, and unmanaged-byte preservation.

The implementation and Git integration gates are closed. Final main verification
passed before `origin/main` and the annotated `v1.0.0` tag were pushed.

## Known design constraints

- RFC-0055 does not delete complete files.
- Missing reviewed base requires adoption decision.
- Content similarity is advisory only.
- Quality Report integration remains optional until RFC-0054 is approved.
- Cross-process lease/retention remains v1.1 hardening.

## v1.0 completion

RFC-0055 is the final v1.0 Product Capability RFC and completes the primary
sequence from deterministic generation to safe coexistence with existing user
documentation.

## Post-v1.0 Product Capability

Add Documentation Evolution Intelligence after v1.0. Core should
produce structured causal facts for changed, added, and removed entities and
relationships with impact scope and Evidence. It consumes the RFC-0055
Explanation Report and Evidence graph. AI may render prose only.

Proposed number: RFC-0056, subject to separate approval.

## Canonical sources

- `docs/rfc/RFC-0055-Existing-Documentation-Reconciliation.md`
- `docs/planning/RFC-0055-MAIN-PLANNING-UPDATE.md`
- `docs/roadmap/ROADMAP.md`
