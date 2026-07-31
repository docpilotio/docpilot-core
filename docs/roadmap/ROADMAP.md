# DocPilot Roadmap

## Current milestone

### v1.1 Product Capability — RFC-0058 implemented

RFC-0057 established the canonical readiness baseline. RFC-0058 now implements runtime-only Documentation Profiles and deterministic Document Contracts while preserving the existing Artifact, Snapshot, Review, Reconciliation, and Evolution formats.

The public v1.0 Product Validation decision remains `PRODUCT_VALIDATION_FAIL` / `NOT_APPROVED`. PV-009 remains `PENDING`. RFC-0058 does not alter the documented immutable `v1.0.0` technical baseline or declare a v1.1 Release Candidate.

The supplied source ZIP contains no `.git`; current branch, HEAD, origin divergence, tag presence, and clean-tree status must therefore be verified only in a Git worktree.

## Delivered capability baseline

- source loading, scanning, and Evidence indexing
- knowledge construction
- DIR 0.4 specification building with Stable IDs and deterministic Feature Discovery
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
- RFC-0058 `kotlin-android@1` Profile, Document/Section contracts, Evidence and capability policy, completeness findings, ownership safety, semantic hashes, and additive RFC-0052 compatibility binding
- RFC-0059 Feature, Entry Point, Scenario, and Scenario Step production contracts
- RFC-0060 Evidence-bounded Android Entry Point, participant, and direct-call Scenario discovery
- RFC-0061 deterministic Compose route, registration, destination, Entry Point, Feature,
  and Scenario Evidence

## RFC status baseline

| RFC | State | Canonical interpretation |
|---|---|---|
| RFC-0001 through RFC-0053 | Implemented sequence | Historical RFC and planning records retained |
| RFC-0054 | Proposed, not approved or completed | Candidate documents and validator source do not establish RFC completion |
| RFC-0055 | Implemented | Existing Documentation Reconciliation |
| RFC-0056 | `IMPLEMENTATION_COMPLETED_WITH_VERIFICATION_LIMITATION` | v1.1-only; full Gradle and architecture-samples Evolution E2E pending |
| RFC-0057 | Implemented | Canonical Baseline and Documentation Expansion Readiness |
| RFC-0058 | `IMPLEMENTED_WITH_ENVIRONMENT_VERIFICATION_LIMITATION` | Documentation Profiles and Document Contracts |
| RFC-0059 | Implemented | Feature, Entry Point, and Scenario Specification Foundation |
| RFC-0060 | `IMPLEMENTED_WITH_VALIDATION_LIMITATIONS` | Deterministic Feature Discovery; Compose routes remain unsupported |
| RFC-0061 | `IMPLEMENTED_WITH_DOCUMENTED_LIMITATIONS` | Deterministic Compose Navigation Evidence and destination discovery |

## Historical release milestones

### v0.5 MVP / POC

RFC-0001 through RFC-0049 delivered the technical MVP/POC baseline, including Source-to-Specification flow, incremental documentation, deterministic review, relationship semantics, durable review persistence, official review CLI operations, and Release Evidence. Historical documents report local/full validation at their completion points. Current ZIP inspection does not independently re-establish Git identity for those results.

### v1.0 technical baseline

RFC-0050 through RFC-0055 extended review lifecycle, selective artifact planning, semantic relationships, and existing-document reconciliation. Historical planning reports the `v1.0.0` technical tag. Post-tag Product Validation failed, so the public/product v1.0 release is not approved.

### v1.1 Product Capability

RFC-0056 adds Documentation Evolution and Change Intelligence. RFC-0058 adds runtime-only Profile policy and deterministic Resolution. Recorded RFC-0058 verification includes changed-source subset compilation, 18 targeted Profile tests, and 4 existing Renderer regression methods. Canonical full Gradle execution and official architecture-samples Evolution/Profile fixtures remain pending.

## Next product sequence

1. RFC-0057 — Canonical Baseline and Documentation Expansion Readiness — implemented.
2. RFC-0058 — Documentation Profiles and Document Contracts — implemented with environment verification limitation.
3. RFC-0059 — Feature, Entry Point, and Scenario Specification Foundation — implemented.
4. RFC-0060 — deterministic Evidence-bounded DIR 0.4 projection — proposed next.
4. Later RFCs — Contract extraction, Profile-aware rendering, Diagram IR and renderers, traceability, and structured AI enrichment after deterministic production models exist.

RFC-0058 keeps Profiles and Resolutions runtime-only. It does not introduce DIR 0.4, change Snapshot format 1, or add Feature/Scenario production entities.

## Unnumbered hardening candidates

The following remain future candidates and do not reserve RFC-0059 or later product-sequence numbers:

- Cross-process Review Leases and Audit-safe Retention
- Signed Release Evidence and External Attestation
- persistent Evolution graph partition caching
- official Reconciliation and Evolution product workflows
- independent Product Validation re-entry work

Hardening may be scheduled when Evidence shows an immediate integrity, release, or operational blocker.

## Release validation policy

Each release must preserve versioned build, test, Git, CLI, provider, artifact, and error-handling Evidence. Unexecuted validation is recorded as `NOT_EXECUTED` or `NOT_EXECUTED_ENVIRONMENT_LIMITATION`, never PASS. Public Product Validation remains independent from technical release evidence.
