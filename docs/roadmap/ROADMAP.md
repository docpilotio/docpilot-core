# DocPilot Roadmap

## Current milestone

### v0.5 MVP / POC - RFC-0001 through RFC-0049

Status: technical runtime gates passed on July 25, 2026. Specification incremental
execution, snapshot persistence, AI incremental generation, documentation
diff/review, deterministic Relationship Semantics, and relationship-aware
incremental diff/review, and review-gated managed-block removal are implemented.
RFC-0046 through RFC-0048 implementation, focused verification, full regression
verification, isolated smoke, canonical handoff, main integration, and remote
synchronization are complete. RFC-0049 is implemented and locally verified as the
v0.5 Release Provenance and Determinism Gate. Exact clean-commit evidence
collection and Git integration are pending.

Delivered baseline:

- source scanning
- knowledge construction
- DIR 0.3 specification building
- deterministic Markdown rendering
- Stable-ID specification incremental planning
- specification snapshot persistence and CLI workflow
- provider-independent AI incremental patch generation
- deterministic documentation diff and complete-review-before-merge
- deterministic INTERNAL, EXTERNAL, and UNRESOLVED relationship endpoint semantics
- direct `DEPENDS_ON` component dependency projection and validation
- relationship-aware incremental diff, planning, AI context, and review Evidence
- explicit review-gated managed-block removal with reviewed-base conflict safety
- durable, integrity-protected Review Bundles and restart-safe apply
- official thin-adapter CLI review workflow
- prompt-package generation
- AI Provider SPI
- verified Ollama architecture generation

## Product Capability track

RFC-0049 Plan A, v0.5 Release Provenance and Determinism Gate, is implemented and
locally verified. It provides Release Evidence Manifest format 1, exact
clean-commit and embedded MCP binding, strict test aggregation, artifact
integrity, deterministic JSON/Markdown reporting, atomic evidence storage,
offline verification, and a fail-closed binary release gate in an independent
module. Final evidence collection must run after an exact clean feature commit.

RFC-0050 Plan A, Review Bundle Lifecycle and Apply Receipt, is implemented and
locally verified for the path from v0.5 release trust to v1.0 auditability and
long-term operation. Review Bundle format 1 remains unchanged. Separate
Lifecycle Metadata, Apply Receipt, and Apply Transaction Journal format 1
contracts provide Core-owned transitions, atomic Receipt/APPLIED visibility,
recoverable documentation apply, idempotency, crash recovery, and offline
verification. The clean build and 291-test regression suite pass, and main
integration is complete at `0f6b15d`. Remote synchronization is pending.

Signed Release Evidence and External Attestation remains a later
release-security candidate.

RFC-0051 Plan A, Official Review Lifecycle Operations and Recovery CLI, is
implemented and locally verified. It exposes Core-owned status, offline
verification, recovery, supersession, and archive through thin-adapter CLI
commands. Every mutation defaults to a deterministic Core dry-run Plan and
requires explicit confirmation; automation may bind confirmation to the Plan
SHA. The clean build and 301-test regression suite pass. Main integration is
complete at `2036eb9`. Remote synchronization is pending. Cross-process Review
Leases and Audit-safe Retention remains deferred.

RFC-0052 returns to the primary Product Capability roadmap as Selective
Documentation Artifact Planning and Rendering. The current executor avoids
unchanged writes but still performs a full render, and the official specification
renderer emits one monolithic artifact. RFC-0052 introduces renderer-owned
artifact descriptors, deterministic Stable-ID impact planning, a multi-artifact
official layout, and selective rendering so only required CREATE/UPDATE
documents are generated. Implementation, the 306-test clean regression suite,
and Main integration at `33503f7` are complete. Remote synchronization is
pending.

The proposed Product Capability sequence after RFC-0052 is:

1. RFC-0053: Semantic Relationship Expansion (`EXTENDS`, `IMPLEMENTS`, `CALLS`,
   `IMPORTS`) with deterministic identity and Evidence.
2. RFC-0054: Documentation Quality Validation for coverage, Evidence
   traceability, stale claims, unresolved gaps, and relationship consistency.
3. RFC-0055: Existing Documentation Reconciliation for ownership, drift,
   adoption, managed/manual boundaries, and orphan disposition.

Only RFC-0052 is selected. RFC-0053 through RFC-0055 numbers remain provisional.

## v1.1 Hardening track

The following work is intentionally separated from the Product Capability track:

- RFC-0056+ or v1.1: Cross-process Review Leases and Audit-safe Retention.
- RFC-0057+ or v1.1: Signed Release Evidence and External Attestation.

These numbers are placeholders, not reserved RFC approvals. Hardening may move
earlier only if new evidence shows an immediate release or data-integrity
blocker.

Future work must preserve Clean Architecture, Evidence First, deterministic core
outputs, the separation between Snapshot Incremental and Specification
Incremental, and complete-review-before-merge. The primary POC target remains
`C:\WorkSpace\architecture-samples`.

## Release validation policy

Each release should retain a versioned snapshot containing build, test, CLI,
provider, and error-handling evidence. OpenAI runtime validation is not implied
unless explicitly included in the release scope.
