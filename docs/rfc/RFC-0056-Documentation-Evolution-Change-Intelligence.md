# RFC-0056: Documentation Evolution and Change Intelligence

## Status

Proposed for post-v1.0 approval. Implementation is not authorized by this
document.

## Purpose

DocPilot can generate, incrementally select, review, and safely reconcile
documentation. RFC-0056 adds a Core-owned explanation of how and why
documentation evolved between two verified states.

The authoritative output is structured Evidence. AI may render that Evidence
as prose but may not invent causes, impacts, identities, or completeness.

## Product outcome

Given two compatible verified project/documentation states, DocPilot produces a
deterministic Evolution Report that answers:

- what Entity, Property, API, and Relationship was added, removed, or changed;
- which source Evidence caused the change;
- which documentation Artifacts were selected and why;
- what reconciliation ownership or merge decision affected the result;
- which downstream Artifacts and summaries were impacted;
- whether an explanation is complete, partial, or blocked by missing Evidence.

## Goals

1. Define a versioned Core Evolution Report contract.
2. Preserve stable Entity, Relationship, Artifact, and decision identities.
3. Build a deterministic causal graph from source change to documentation
   result.
4. Explain additions, removals, modifications, moves, and identity-preserving
   renames.
5. Consume RFC-0052 incremental plans, RFC-0053 relationship projections, and
   RFC-0055 reconciliation Evidence.
6. Support relationship-only and ownership-only documentation changes.
7. Distinguish observed fact, Core inference, user decision, and optional AI
   narrative.
8. Fail closed when required before/after Evidence is unavailable or
   incompatible.
9. Support offline integrity verification and reproducible rendering.

## Non-goals

- source-code modification;
- ownership or merge decisions;
- replacement of RFC-0055 conflict handling;
- AI-generated authoritative causes;
- predictive architecture recommendations;
- interactive UI;
- MCP-owned semantics;
- cross-process leases, retention, or signed release Evidence;
- v1.0.x backport of this Product Capability.

## Inputs

Core consumes immutable, content-addressed inputs:

```text
Before/After Specification Snapshot
Before/After Documentation Artifact Catalog
Specification and Relationship Diff
RFC-0052 Documentation Artifact Plan
RFC-0053 Relationship Projection Report
RFC-0055 Reconciliation Plan and Result
RFC-0055 Decision Explanation Report
Review Bundle / Apply Receipt references
Optional Documentation Quality Report
```

Missing optional inputs produce an explicit coverage finding; they never cause
heuristic facts to be silently invented.

## Change model

Stable change kinds:

```text
ENTITY_ADDED
ENTITY_REMOVED
ENTITY_MODIFIED
ENTITY_MOVED
PROPERTY_CHANGED
API_CHANGED
RELATIONSHIP_ADDED
RELATIONSHIP_REMOVED
RELATIONSHIP_MODIFIED
ARTIFACT_CREATED
ARTIFACT_UPDATED
ARTIFACT_RETAINED
OWNERSHIP_CHANGED
RECONCILIATION_CONFLICTED
USER_DECISION_APPLIED
```

Each change binds:

```text
changeId
subject stable ID
before/after hashes
change kind
Evidence references
causal predecessors
affected Artifact IDs
confidence class
coverage state
```

`confidence class` is not a probability. It is one of:

```text
OBSERVED
CORE_DERIVED
USER_AUTHORIZED
OPTIONAL_AI_NARRATIVE
```

## Causal graph

Core emits typed deterministic edges:

```text
Source Evidence -> causes -> Specification Change
Specification Change -> selects -> Artifact
Relationship Change -> refreshes -> Summary
Artifact Plan Entry -> produces -> Candidate
Ownership Decision -> permits/prohibits -> Reconciliation Operation
User Decision -> authorizes -> Applied Result
Applied Result -> changes -> Documentation Artifact
```

Cycles are rejected. Nodes and edges are sorted by stable identity before
hashing and rendering.

## Evolution Report

Conceptual contract:

```kotlin
data class DocumentationEvolutionReport(
    val formatVersion: Int = 1,
    val projectId: String,
    val beforeStateSha256: String,
    val afterStateSha256: String,
    val changes: List<DocumentationEvolutionChange>,
    val causalGraph: DocumentationEvolutionGraph,
    val impactedArtifacts: List<EvolutionArtifactImpact>,
    val coverage: EvolutionCoverage,
    val evidenceRefs: List<String>,
    val reportSha256: String,
)
```

The semantic hash excludes timestamps, absolute paths, locale, filesystem
enumeration order, and optional narrative prose.

## Completeness and fail-closed rules

Coverage states:

```text
COMPLETE
PARTIAL_MISSING_OPTIONAL_EVIDENCE
BLOCKED_INCOMPATIBLE_FORMAT
BLOCKED_MISSING_REQUIRED_EVIDENCE
BLOCKED_INTEGRITY_FAILURE
```

Core must not report `COMPLETE` unless every material applied Artifact change
has at least one verified causal path to source Evidence, ownership decision, or
explicit user decision.

## AI boundary

AI may:

- render a concise narrative from verified graph nodes and edges;
- group related changes for readability;
- offer alternate wording tied to the same facts.

AI may not:

- create or remove graph nodes or edges;
- change stable IDs, hashes, coverage, or impact scope;
- label a missing cause as known;
- suppress removals, conflicts, or user-retained content;
- write or apply documentation.

## Incremental behavior

Evolution analysis evaluates only:

- changed Specification and Relationship identities;
- RFC-0052 selected Artifacts and summaries;
- drifted or reconciled RFC-0055 Artifacts;
- directly connected causal Evidence.

Unchanged graph partitions are reused by content hash. Input ordering never
changes the report.

## Persistence and verification

The Report is a separate format-1 contract with:

- canonical deterministic encoding;
- payload SHA-256;
- repository-relative references;
- offline verification against all required input hashes;
- unknown-field and unsupported-version fail-closed behavior.

It does not alter DIR, Snapshot, Review Bundle, Lifecycle, Receipt,
Reconciliation Plan, or Reconciliation Result formats.

## CLI and MCP boundary

No CLI or MCP implementation is required initially. Future adapters may expose
`evolution explain` and `evolution verify`, but all diff, identity, causal,
coverage, and integrity rules remain exclusively in Core.

## Expected implementation areas

```text
src/main/kotlin/io/docpilot/core/evolution/**
src/test/kotlin/io/docpilot/core/evolution/**
docs/rfc/RFC-0056-Documentation-Evolution-Change-Intelligence.md
docs/planning/RFC-0056-MAIN-PLANNING-UPDATE.md
docs/handoffs/RFC-0056-COMPLETION-HANDOFF.md
```

## Verification

- added/removed/modified Entity and Relationship cases;
- relationship-only Artifact impact;
- ownership-only and user-decision change;
- stable rename/move identity;
- complete and partial coverage;
- missing/tampered/incompatible Evidence;
- graph cycle and dangling-reference rejection;
- shuffled input determinism;
- offline codec round trip;
- optional AI narrative cannot alter Report SHA;
- clean full build/test and isolated before/after fixture.

## Completion criteria

- every material documentation change is represented or explicitly uncovered;
- causal paths use verified Core Evidence;
- impact scope agrees with RFC-0052 selection;
- reconciliation causes agree with RFC-0055 Explanation Evidence;
- AI remains narrative-only;
- Report encoding and offline verification are deterministic;
- CLI and MCP contain no evolution semantics;
- targeted and full verification pass.

## Release strategy

RFC-0056 starts v1.1 development on main only after the verified `v1.0.0`
baseline. It is not merged into `release/v1.0.x`.
