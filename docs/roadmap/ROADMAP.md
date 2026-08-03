# DocPilot Roadmap

## Current milestone

### v1.1 Product Capability — RFC-0063 implemented; RFC-0064 through RFC-0074 baseline fixed

RFC-0057 established the canonical readiness baseline. RFC-0058 introduced runtime-only Documentation Profiles; RFC-0059 through RFC-0062 now provide DIR 0.4 Feature contracts, deterministic discovery, Compose destination Evidence, function-reference resolution, nested graph ownership, and navigation argument Evidence while preserving established Artifact, Review, Reconciliation, and Evolution identities.

The public v1.0 Product Validation decision remains `PRODUCT_VALIDATION_FAIL` / `NOT_APPROVED`. PV-009 remains `PENDING`. RFC-0058 does not alter the documented immutable `v1.0.0` technical baseline or declare a v1.1 Release Candidate.

The supplied source ZIP contains no `.git`; current branch, HEAD, origin divergence, tag presence, and clean-tree status must therefore be verified only in a Git worktree.

## Delivered capability baseline

- source loading, scanning, and Evidence indexing
- knowledge construction
- DIR 0.5 specification building with Stable IDs, deterministic Feature Discovery, and Contract extraction
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
- RFC-0062 Compose function-reference resolution, nested graph ownership, navigation argument
  Evidence, and signature-backed argument links

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
| RFC-0062 | `IMPLEMENTED_WITH_VALIDATION_LIMITATIONS` | Function references, nested graph ownership, and navigation argument Evidence; real-project repeat validation passed, syntax coverage absent |
| RFC-0063 | Implemented | Standalone Core Release Evidence format 2 and removal of the temporary in-repository MCP implementation |
| RFC-0064 | Implemented | Profile-aware Feature Documentation Rendering |
| RFC-0065 | Implemented | DIR 0.5 Contract Specification Foundation and Snapshot format 3 |
| RFC-0066 | `IMPLEMENTED_WITH_VALIDATION_LIMITATIONS` | Nine-role deterministic extraction; real-project Evidence covered public APIs and callbacks |

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
4. RFC-0060 — deterministic Evidence-bounded DIR 0.4 projection — implemented with validation limitations.
5. RFC-0061 — deterministic Compose Navigation Evidence — implemented with documented limitations.
6. RFC-0062 — function references, nested graph ownership, and navigation argument Evidence — implemented; real-project repeat validation passed, syntax coverage absent.
7. RFC-0063 — standalone Core Release Evidence format 2 and MCP removal — implemented.
8. RFC-0064 — Profile-aware Feature Documentation Rendering — implemented.
9. RFC-0065 — Contract Specification Foundation — implemented.
10. RFC-0066 — Deterministic Contract Extraction — implemented with real-project validation limitations.
11. RFC-0067 — Contract Documentation Rendering — implemented.
12. RFC-0068 — Official Documentation Generation CLI — implemented.
13. RFC-0069 — Documentation Bundle and Manifest — implemented.
14. RFC-0070 — Structured AI Documentation Enrichment — implemented.
15. RFC-0071 — Diagram IR and Mermaid Rendering — not implemented.
16. RFC-0072 — Documentation Claims and Traceability — implemented; its RFC-0071 prerequisite was
    explicitly skipped (Diagram IR is unrelated to Claim/Evidence/Contract binding), recorded in
    `docs/rfc/RFC-0072-Documentation-Claims-and-Traceability.md`'s "Depends on" section.
17. RFC-0073 / RFC-0074 — official Reconciliation and Evolution CLI workflows.
18. RFC-0075 — Documentation Quality Gate.
19. RFC-0076 — Product Validation Re-entry and independent PV-009 reassessment.

RFC-0077 (Kotlin/Android Profile Document Coverage Completion) and RFC-0078–0082 (Finding model,
synthesis/Advisory documents, Productization Roadmap curation, AI-Proposed ADR workflow) are a
separate, unblocked track from this fixed sequence — **all implemented** as of 2026-08-02:

- RFC-0077 — Document Coverage Completion for `kotlin-android@1` Profile — implemented.
- RFC-0078 — Evidence-Bound Finding and Severity Model — implemented (required building RFC-0072,
  Documentation Claims and Traceability, first, since it did not previously exist).
- RFC-0079 — Cross-Artifact Synthesis Request and Advisory Document Tier — implemented, core-library-only.
- RFC-0080 — Executive Summary and Known Issues Register Document Types — implemented, core-library-only.
- RFC-0081 — Productization Roadmap Document and Human Curation Step — implemented.
- RFC-0082 — AI-Proposed Architecture Decision Record Workflow — implemented; closes out the track.

See `docs/rfc/RFC-0077-...md` through `docs/rfc/RFC-0082-...md` for each RFC's doc. RFC-0071
(Diagram IR/Mermaid) and RFC-0073 (Official Reconciliation CLI Workflow) remain not implemented and
unaffected — both were explicit, approved dependency skips recorded in RFC-0072's and RFC-0082's
"Depends on" sections respectively.

RFC-0083 (CLI Wiring for Findings, Advisory Documents, and Documentation Logging) is a follow-on
to this track — **implemented** as of 2026-08-03. RFC-0078–0082 were deliberately core-library-only
with no CLI entry point; RFC-0083 adds six `docpilot generate <noun>` subcommands (`findings`,
`known-issues`, `roadmap`, `executive-summary`, `adr-propose`, `adr-adopt`) that wire those
capabilities into the CLI as standalone commands (not folded into `generate docs`'s Bundle/Manifest
pipeline), and gives `generate docs` the same `ProjectLogSession` AI-call logging that
`architecture`/`adr`/`specification` already had. See
`docs/rfc/RFC-0083-CLI-Wiring-for-Findings-Advisory-Documents-and-Documentation-Logging.md`.
Verified against a real Ollama-backed run on an isolated `architecture-samples` copy in addition to
the automated test suite (530 tests, 0 failures). Explicitly out of scope, left for a future RFC: a
Finding auto-extraction pipeline (Findings are still hand-authored JSON, only validated by the CLI)
and persistence of curation decisions/proposals outside caller-managed files.

RFC-0084 (AI-Proposed Finding Extraction and Persisted Finding/Curation Registry) closes both of
those gaps — **implemented** as of 2026-08-03, in two parts. Part A adds `generate propose-findings`,
which asks an AI model to propose candidate Findings for a batch of components (fail-closed parsed,
hallucinated subject ids rejected, prompt deliberately compact to avoid a prompt-size bug found while
smoke-testing RFC-0083) and emits them in the exact JSON shape `generate findings --input` already
validates — no new "adopt" command, the existing validator is the human-gated safety net. Part B adds
a core-library persisted Finding/curation registry (`.docpilot/findings/registry.json` +
`decisions.json`), modeled on `FileReviewBundleRepository`'s file-handling shape but with new domain
types (that repository's Markdown-patch domain types don't fit Finding-shaped data); `generate
findings` now auto-merges into the registry, and `known-issues`/`roadmap`/`executive-summary`/
`adr-propose` gain `--findings-registry` while `roadmap`/`adr-adopt` gain `--decisions-registry`, so a
curation decision made in one CLI invocation is honored by a later one without re-supplying it. See
`docs/rfc/RFC-0084-AI-Proposed-Finding-Extraction-and-Persisted-Finding-Curation-Registry.md`.
Verified with both automated tests (565 tests, 0 failures) and real-project smoke tests (Part A with
a real Ollama model, Part B's persistence loop confirmed across separate CLI invocations).

The Product Owner fixed this numbering, order, and scope boundary in
`docs/planning/RFC-0064-RFC-0074-FIRST-PRODUCT-DEVELOPMENT-ROADMAP.md`. Individual RFC design,
data contracts, Acceptance Criteria, and verification methods are approved when each RFC starts.

Profiles and Resolutions remain runtime-only. RFC-0059 introduced DIR 0.4 and Snapshot format 2 while retaining format 1/DIR 0.3 compatibility; RFC-0060 through RFC-0062 extend deterministic discovery without changing those persisted formats.

## Unnumbered hardening candidates

The following remain future candidates and do not reserve RFC-0059 or later product-sequence numbers:

- Cross-process Review Leases and Audit-safe Retention
- Signed Release Evidence and External Attestation
- persistent Evolution graph partition caching
- post-RFC-0074 operational hardening supported by new Evidence

Hardening may be scheduled when Evidence shows an immediate integrity, release, or operational blocker.

## Release validation policy

Each release must preserve versioned build, test, Git, CLI, provider, artifact, and error-handling Evidence. Unexecuted validation is recorded as `NOT_EXECUTED` or `NOT_EXECUTED_ENVIRONMENT_LIMITATION`, never PASS. Public Product Validation remains independent from technical release evidence.
