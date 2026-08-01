# RFC-0064 through RFC-0074 First Product Development Roadmap

Status: `FIRST_BASELINE_FIXED`

Decision owner: Product Owner

Baseline: RFC-0063 completed

Development track: v1.1 Product Capability

This document fixes the first post-RFC-0063 product-development sequence. It approves the RFC
numbers, ordering, boundaries, and shared preservation policy below. It does not pre-approve the
detailed design, data contracts, Acceptance Criteria, or verification claims of an individual RFC;
those require Product Owner approval when that RFC starts.

## Fixed sequence

| RFC | Title | Intended product result |
|---|---|---|
| RFC-0064 | Profile-aware Feature Documentation Rendering | Render DIR 0.4 Feature, Entry Point, Scenario, and ordered Scenario Step documents |
| RFC-0065 | Contract Specification Foundation | Define the canonical Contract model, Stable IDs, Evidence policy, validation, and evolution extension points |
| RFC-0066 | Deterministic Contract Extraction | Extract Evidence-bounded Kotlin and Android Contracts deterministically |
| RFC-0067 | Contract Documentation Rendering | Render API, data, event, and individual Contract documentation |
| RFC-0068 | Diagram IR and Mermaid Rendering | Introduce deterministic Diagram IR with Mermaid as its first renderer |
| RFC-0069 | Documentation Claims and Traceability | Trace Source, Evidence, Specification, Claim, Document Section, and Review Decision |
| RFC-0070 | Official Reconciliation CLI Workflow | Expose preview, decision, apply, Receipt, JSON, and exit-code behavior through the product CLI |
| RFC-0071 | Official Evolution CLI Workflow | Expose change, impact, causal graph, coverage, persistence, and offline verification through the product CLI |
| RFC-0072 | Structured AI Documentation Enrichment | Permit Evidence-bounded narrative and scoped patch proposals without canonical authority |
| RFC-0073 | Documentation Quality Gate | Define and enforce the official deterministic documentation quality contract |
| RFC-0074 | Product Validation Re-entry | Re-run the public-project product flow and independently reassess PV-009 and public approval |

The default dependency order is:

```text
RFC-0064
→ RFC-0065
→ RFC-0066
→ RFC-0067
→ RFC-0068
→ RFC-0069
→ RFC-0070 / RFC-0071
→ RFC-0072
→ RFC-0073
→ RFC-0074
```

RFC-0070 and RFC-0071 may proceed in parallel only after their shared Core contracts are stable.
Production hardening of the existing `docpilot-provider-openai` may proceed as a parallel provider
track, but it may not redefine DIR, Snapshot, Feature, Contract, Diagram, Traceability, or the fixed
RFC numbering. No duplicate provider such as `provider-chatgpt` is introduced.

## Capability boundaries

RFC-0064 consumes DIR 0.4 and renders a deterministic Feature catalog and per-Feature documents.
It does not rescan source or invent Feature and Scenario entities. RFC-0065 through RFC-0067 define,
extract, and render Evidence-backed Contracts. Ambiguous or unprovable identity remains explicit as
unresolved; first-candidate selection and business-meaning inference from names are prohibited.

RFC-0068 separates `ProjectSpecification`, `DiagramSpecification`, Diagram IR, and renderer syntax.
Nodes and edges use Stable IDs, and renderers do not infer relationships. RFC-0069 adds stable Claim,
Evidence, entity, section, and review bindings, including orphan, stale, and broken traceability
detection.

RFC-0070 makes Reconciliation a preview-first product CLI workflow that preserves original content
on failure. RFC-0071 makes Evolution generation and verification an official CLI workflow while
preserving Evolution Report format 1. RFC-0072 limits AI authority to narrative and designated patch
proposals; AI cannot create canonical entities or Evidence, mutate Stable IDs or endpoints, change
Coverage or Artifact Plans, approve review, or transition lifecycle state.

RFC-0073 establishes the official quality outcomes `PASS`, `PARTIAL`, `BLOCKED`, and `FAIL`. Existing
validator code alone does not complete that RFC, and RFC-0054 remains a historical proposal. RFC-0074
is the only item in this sequence authorized to reassess the current public Product Validation state:

```text
Public v1.0 Product Validation: PRODUCT_VALIDATION_FAIL / NOT_APPROVED
PV-009: PENDING
```

Those states remain unchanged until independent RFC-0074 Evidence supports a new decision. A build,
test pass, or Git tag alone is insufficient.

## Preserved contracts

Unless an RFC explicitly versions a contract and defines migration, compatibility, and rollback, the
sequence preserves:

- DIR 0.2 manual compatibility, DIR 0.3, and DIR 0.4
- Snapshot format 1/DIR 0.3 and Snapshot format 2/DIR 0.4
- Review Bundle format 1
- Relationship Projection Report format 1
- Evolution Report format 1
- Release Evidence Manifest format 2
- RFC-0052 Artifact identities and Documentation Profile identities
- Stable ID, explicit migration, and no-silent-fallback policies
- Evidence Before Assumption, AI vendor independence, and the Human approval boundary

All RFCs remain Human First, Specification First, deterministic, Stable-Identity based,
preview-before-apply, fail-closed, and historically auditable. Historical RFC, Planning, Validation,
and Handoff records are not retroactively rewritten.

## Common verification floor

Each RFC must record targeted and related-module tests, full Gradle regression, `git diff --check`,
deterministic ordering, Stable ID regression, applicable codec round trips, malformed and unsupported
version rejection, and compatibility checks for existing Snapshot, Artifact, Review, Evolution, and
Release contracts. Real `architecture-samples` verification uses an isolated copy when required.
Unexecuted checks are never recorded as PASS.

## Explicitly deferred scope

The fixed sequence excludes Signed Release Evidence, External Release Attestation, cross-process
Review Leases, audit-safe Retention, persistent Evolution graph partition caching, replacement MCP or
orchestration runtimes, cloud worker queues, automated PR/merge/tag/release, provider failover,
multi-provider voting, runtime behavior inference, and AI-generated canonical Feature or Contract
entities. Evidence may justify them as RFC-0075 or later candidates.
