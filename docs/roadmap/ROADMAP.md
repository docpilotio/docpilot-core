# DocPilot Roadmap

## Current milestone

### v1.1 Product Capability — RFC-0057 active

RFC-0056 Documentation Evolution and Change Intelligence is implemented for the v1.1 track. RFC-0057 establishes a canonical source, documentation, version, verification, and migration-readiness baseline before Documentation Profiles, Feature Specifications, Scenarios, Contracts, and Diagram IR are introduced.

The public v1.0 Product Validation decision remains `PRODUCT_VALIDATION_FAIL` / `NOT_APPROVED`. PV-009 remains `PENDING`. RFC-0057 does not alter the documented immutable `v1.0.0` technical baseline and does not backport RFC-0056 to v1.0.

The supplied source ZIP contains no `.git`; current branch, HEAD, origin divergence, tag presence, and clean-tree status must therefore be verified only in a Git worktree.

## Delivered capability baseline

- source loading, scanning, and Evidence indexing
- knowledge construction
- DIR 0.3 specification building with Stable IDs
- deterministic Markdown and multi-artifact selective rendering
- specification Snapshot format 1 persistence
- Stable-ID diff and incremental planning
- incremental CLI execution and AI target-scoped patch generation
- deterministic documentation review and complete-review-before-merge
- deterministic INTERNAL, EXTERNAL, and UNRESOLVED relationship semantics
- relationship-aware impact, Projection Report integrity, and bounded projection
- managed-block removal review and reviewed-base conflict safety
- durable Review Bundle format 1, lifecycle, Receipt, Journal, recovery, and thin CLI operations
- Release Evidence Manifest and offline technical release gate
- RFC-0055 existing-document ownership and reconciliation
- Documentation Quality Validator used by the product-validation workflow
- RFC-0056 deterministic Evolution Report, change extraction, Artifact impact, causal graph, coverage, strict codec, offline verifier, and narrative-only AI boundary

## RFC status baseline

| RFC | State | Canonical interpretation |
|---|---|---|
| RFC-0001 through RFC-0053 | Implemented sequence | Historical RFC and planning records retained |
| RFC-0054 | Proposed, not approved or completed | Candidate documents and validator source do not establish RFC completion |
| RFC-0055 | Implemented | Existing Documentation Reconciliation |
| RFC-0056 | `IMPLEMENTATION_COMPLETED_WITH_VERIFICATION_LIMITATION` | v1.1-only; full Gradle and architecture-samples Evolution E2E pending |
| RFC-0057 | Active | Canonical Baseline and Documentation Expansion Readiness |
| RFC-0058 | Next planned | Documentation Profiles and Document Contracts |

## Historical release milestones

### v0.5 MVP / POC

RFC-0001 through RFC-0049 delivered the technical MVP/POC baseline, including Source-to-Specification flow, incremental documentation, deterministic review, relationship semantics, durable review persistence, official review CLI operations, and Release Evidence. Historical documents report local/full validation at their completion points. Current ZIP inspection does not independently re-establish Git identity for those results.

### v1.0 technical baseline

RFC-0050 through RFC-0055 extended review lifecycle, selective artifact planning, semantic relationships, and existing-document reconciliation. Historical planning reports the `v1.0.0` technical tag. Post-tag Product Validation failed, so the public/product v1.0 release is not approved.

### v1.1 Product Capability

RFC-0056 adds Documentation Evolution and Change Intelligence. Recorded focused verification includes selective source compilation, 10 transformed RFC-0056 test methods, 8 RFC-0052/RFC-0053 bridge scenarios, isolated Evolution smoke, Reconciliation smoke, and semantic-hash compatibility fixtures. Canonical full Gradle execution and an official `architecture-samples` before/after Evolution fixture remain pending.

## Next product sequence

1. RFC-0057 — Canonical Baseline and Documentation Expansion Readiness.
2. RFC-0058 — Documentation Profiles and Document Contracts.
3. RFC-0059 — Feature, Entry Point, and Scenario Specification foundation.
4. Later RFCs — Interaction/Contract extraction, Diagram IR and renderers, traceability, and structured AI enrichment after deterministic contracts exist.

RFC-0057 defines migration readiness only. It does not introduce DIR 0.4, change Snapshot format 1, or add profile/feature/scenario production models.

## Unnumbered hardening candidates

The following remain future candidates and do not reserve RFC-0057 or RFC-0058 numbers:

- Cross-process Review Leases and Audit-safe Retention
- Signed Release Evidence and External Attestation
- persistent Evolution graph partition caching
- official Reconciliation and Evolution product workflows
- independent Product Validation re-entry work

Hardening may be scheduled when Evidence shows an immediate integrity, release, or operational blocker.

## Release validation policy

Each release must preserve versioned build, test, Git, CLI, provider, artifact, and error-handling Evidence. Unexecuted validation is recorded as `NOT_EXECUTED` or `NOT_EXECUTED_ENVIRONMENT_LIMITATION`, never PASS. Public Product Validation remains independent from technical release evidence.
