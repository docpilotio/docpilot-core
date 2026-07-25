# RFC-0052 Main Planning Update

## Dashboard

| Item | State |
| --- | --- |
| Track | Product Capability |
| RFC | RFC-0052 |
| Title | Selective Documentation Artifact Planning and Rendering |
| Direction | APPROVED |
| Detailed specification | COMPLETE |
| Implementation | COMPLETE |
| Targeted verification | PASS |
| Full regression | PASS |
| Main integration | COMPLETE (`33503f7`) |

## Baseline

- Local main: `12128beb7c9696a57dd6787fd4e83c429aeb8db6`
- RFC-0051 main integration: COMPLETE
- Verified suite: 97 XML / 301 tests / 0 failures
- Current official renderer: one monolithic `specification.md`
- Current executor: full render before artifact action comparison

## Problem statement

Current incremental execution avoids unchanged writes but still performs full
rendering. With one official output artifact, change targeting cannot select only
the affected documents.

## Approved product direction

Introduce deterministic artifact discovery, exact stable-ID impact planning, an
official multi-artifact renderer, and selective execution.

## Implementation stages

1. Artifact ID/kind/descriptor and safe-path contracts.
2. Selective renderer SPI with full-render compatibility.
3. Official deterministic multi-artifact catalog.
4. Stable-ID action-to-artifact impact planner.
5. Explicit dependency closure and Plan SHA.
6. Existing owned inventory and unknown-ownership protection.
7. Selective executor integration.
8. Official renderer multi-artifact content/golden tests.
9. Planner/executor determinism and failure tests.
10. Isolated architecture-samples selective-update smoke.
11. Completion Handoff and Roadmap evidence update.

## Implementation evidence

- Core-owned artifact ID, kind, descriptor, and selective renderer contracts
- deterministic multi-artifact official Markdown catalog
- stable-ID direct impact and dependency-closure planning
- deterministic Plan SHA-256
- exact CREATE/UPDATE selective rendering
- KEEP render/write suppression
- orphan retention without deletion
- unknown-ownership fail-closed protection
- thin CLI inventory adapter over the Core renderer catalog
- targeted Renderer/Planner/Executor tests: PASS
- clean full regression: 98 XML / 306 tests / 0 failures

## Completion gate

- unrelated artifacts are neither rendered nor written: PASS;
- missing expected owned artifacts are created: PASS;
- dependency summaries refresh deterministically: PASS;
- unknown ownership fails closed: PASS;
- orphaned artifacts are retained and reported: PASS;
- full-render fallback is explicit: PASS;
- no Lease/Retention/Signature/MCP scope: PASS;
- clean full tests: PASS.

## Follow-up

- RFC-0053 proposal: Semantic Relationship Expansion.
- RFC-0054 proposal: Documentation Quality Validation.
- RFC-0055 proposal: Existing Documentation Reconciliation.
- RFC-0056+/v1.1: Review Lease/Retention hardening.
- RFC-0057+/v1.1: Signed Release Evidence/Attestation hardening.

Only RFC-0052 is selected. Follow-up numbering remains provisional.

## Canonical sources

- `docs/rfc/RFC-0052-Selective-Documentation-Artifact-Planning-and-Rendering.md`
- `docs/planning/RFC-0052-PRODUCT-ROADMAP-REALIGNMENT.md`
- `docs/planning/RFC-0052-MAIN-PLANNING-UPDATE.md`
- `docs/roadmap/ROADMAP.md`
