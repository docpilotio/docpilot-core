# DocPilot Architecture

## Status

This document reflects the canonical source-tree baseline through RFC-0056 and the RFC-0057 documentation synchronization boundary.

RFC-0057 adds no runtime feature and changes no public production contract. It records the implemented architecture, separates historical evidence from currently reproducible evidence, and establishes migration readiness for future documentation expansion.

## Core specification pipeline

```text
Target Project
    ↓
Project Loader
    ↓
Source Scanner
    ↓
SourceIndex + Evidence
    ↓
Knowledge Builder
    ↓
KnowledgeBuildResult / Knowledge Graph
    ↓
Specification Builder
    ↓
ProjectSpecification (DIR 0.3)
    ↓
Specification Snapshot format 1
```

`DefaultSpecificationBuilder` emits DIR 0.3. Manually constructed `ProjectSpecification` instances retain a source-compatible legacy default of DIR 0.2. The Snapshot codec currently accepts DIR 0.3 only.

## Documentation artifact pipeline

```text
Current ProjectSpecification
+
Previous Specification / Existing Artifact Inventory
    ↓
Renderer-owned Artifact Catalog
    ↓
RFC-0052 DocumentationArtifactPlan
    ↓
CREATE / UPDATE / RETAIN selection
    ↓
Selective deterministic rendering
    ↓
Versioned documentation artifacts
```

The renderer consumes canonical specification and plan inputs. It does not reinterpret source files or invent Evidence. RFC-0052 semantic Plan hashes remain part of the integrity boundary.

## Relationship semantics and projection

```text
Observed relationship Evidence
    ↓
INTERNAL / EXTERNAL / UNRESOLVED endpoint resolution
    ↓
Stable relationship identity
    ↓
Relationship Projection Report format 1
    ↓
Bounded artifact projection and integrity verification
```

RFC-0044 established deterministic endpoint semantics. RFC-0045 connected relationship changes to incremental planning and review. RFC-0053 added Evidence-backed semantic relationships and a deterministic Projection Report.

## Review and apply pipeline

```text
IncrementalUpdatePlan / Artifact Plan
+
AI target-scoped patches or deterministic rendered content
    ↓
DocumentationReviewProposal
    ↓
Review Bundle format 1
    ↓
Complete ACCEPTED / REJECTED decisions
    ↓
Lifecycle Metadata + Apply Receipt + Apply Transaction Journal
    ↓
Conflict-safe managed-block apply / recovery
```

AI output is a proposal. Missing patches, partial decisions, malformed managed blocks, unauthorized targets, stale reviewed bases, or invalid lifecycle state prevent apply. Core owns state transitions and integrity verification.

## Existing-document reconciliation

```text
Generated Artifact Plan
+
Existing documents
+
Ownership Manifests
+
User Decisions
    ↓
RFC-0055 preview-first three-way reconciliation
    ↓
Conflict / retained-content / orphan disposition
    ↓
Reconciliation Plan and Result
```

Reconciliation preserves managed/manual boundaries and records material ownership and merge decisions. It has no official product CLI in the current baseline.

## Documentation evolution intelligence

```text
Verified before Snapshot
+
Verified after Snapshot
+
Artifact Catalogs and RFC-0052 Plan
+
Optional Relationship / Ownership / Reconciliation Evidence
    ↓
RFC-0056 DocumentationEvolutionAnalyzer
    ↓
Change records + Artifact impact
    ↓
Acyclic causal graph
    ↓
COMPLETE / PARTIAL / BLOCKED coverage
    ↓
Evolution Report format 1 + offline verifier
```

The Evolution Report explains Entity, API, Property, Relationship, move, identity-preserving rename, ownership, conflict, retained-content, and user-decision effects. AI may render alternate narrative only; it cannot change facts, graph edges, coverage, Stable IDs, or hashes.

## Release Evidence boundary

`docpilot-release` is an independent Gradle module. It owns deterministic Release Evidence Manifest generation, exact-input integrity checks, offline verification, and a fail-closed release gate. Product Validation remains a separate decision and is not implied by a technical build or tag.

## AI provider boundary

```text
Bounded prompt package / verified report
    ↓
AI Provider SPI
    ↓
Provider adapter
    ↓
AI model
    ↓
Narrative or proposed patch
```

Provider output is non-canonical until accepted through the relevant Core workflow. OpenAI and Ollama adapters do not own specification facts or lifecycle state.

## Architectural boundaries

- Scanner extracts observable source Evidence.
- Knowledge Builder constructs structured knowledge.
- Specification Builder creates canonical DIR entities.
- Snapshot persistence preserves exact specification identity.
- Artifact Planner selects deterministic documentation work.
- Renderer is presentation-only.
- Review and lifecycle services own authorization and apply state.
- Reconciliation owns existing-document adoption and conflict decisions.
- Evolution owns verified before/after explanation and causal integrity.
- Release Evidence owns technical release provenance, not public Product Validation.
- AI providers are replaceable adapters behind the Provider SPI.
- Core contracts must remain usable without an AI provider.

## Canonical version policy

| Contract | Current value | RFC-0057 policy |
|---|---:|---|
| Manual `ProjectSpecification` default | DIR 0.2 | Retain for source compatibility |
| Builder output | DIR 0.3 | Canonical current runtime output |
| Specification Snapshot | format 1 | Retain; accepts DIR 0.3 |
| Review Bundle | format 1 | Retain |
| Evolution Report | format 1 | Retain |
| Relationship Projection Report | format 1 | Retain |

DIR 0.4 is not implemented by RFC-0057. Future schema expansion must preserve DIR 0.3 readers or introduce an explicit new Snapshot format and migration operation rather than silently rewriting stored state.

## Verification boundary

The delivered source ZIP has no `.git`, so branch, commit, divergence, tag presence, and clean-tree state are not inferable. Canonical Gradle verification requires JDK 21 and the repository Gradle 9.3.0 Wrapper. A network/DNS failure before Gradle distribution resolution is recorded as `NOT_EXECUTED_ENVIRONMENT_LIMITATION`, not as a code PASS or FAIL.
