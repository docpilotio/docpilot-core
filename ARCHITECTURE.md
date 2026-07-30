# DocPilot Architecture

RFC-0060 adds an Evidence-first Feature Discovery stage between base DIR projection and
validation. It recognizes exact Android framework contracts, performs bounded relationship
traversal, projects only direct-call Scenarios, verifies a deterministic discovery hash,
and emits canonical DIR 0.4. Renderers still consume only ProjectSpecification.

## Status

This document reflects the canonical source-tree baseline through RFC-0058.

RFC-0058 adds an optional runtime-only Documentation Profile and Document Contract boundary. It preserves the existing Renderer-owned Artifact Catalog, RFC-0052 operation Plan, legacy paths, and all persisted formats.

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
Optional RFC-0058 Profile / Renderer capabilities / Ownership state
    ↓
DocumentationProfileResolution + legacy compatibility binding
    ↓
Renderer-owned Artifact Catalog
+
Previous Specification / Existing Artifact Inventory
    ↓
RFC-0052 DocumentationArtifactPlan
    ↓
CREATE / UPDATE / RETAIN selection
    ↓
Selective deterministic rendering
    ↓
Versioned documentation artifacts
```

The Profile Resolver consumes canonical specification, declared Renderer capabilities, Artifact descriptors, and Ownership Manifests. It does not render content or create RFC-0052 operations. The renderer consumes canonical specification and plan inputs; it does not reinterpret source files or invent Evidence. RFC-0052 semantic Plan hashes remain unchanged.

## Documentation Profiles and Document Contracts

```text
Immutable kotlin-android@1 Profile
+
ProjectSpecification DIR 0.3
+
Renderer capability declaration
+
Artifact Catalog / Ownership Manifests
    ↓
Deterministic DocumentationProfileResolution
    ↓
READY / PARTIAL / DEFERRED / BLOCKED / UNSUPPORTED
```

RFC-0058 defines purpose, audience, multiplicity, safe path policy, required and optional Sections, section-level Evidence, Renderer capabilities, completeness, ownership, dependency rules, Stable IDs, and semantic SHA-256. Feature and Contract documents remain DEFERRED because DIR 0.3 has no canonical production model for them. Profile paths are additive contracts and are not written or migrated by RFC-0058.

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
- Documentation Profile Resolver evaluates deterministic document readiness without creating Artifact operations.
- Artifact Planner selects deterministic documentation work.
- Renderer is presentation-only and declares only capabilities it supports.
- Review and lifecycle services own authorization and apply state.
- Reconciliation owns existing-document adoption and conflict decisions.
- Evolution owns verified before/after explanation and causal integrity.
- Release Evidence owns technical release provenance, not public Product Validation.
- AI providers are replaceable adapters behind the Provider SPI.
- Core contracts must remain usable without an AI provider.

## Canonical version policy

| Contract | Current value | RFC-0058 policy |
|---|---:|---|
| Manual `ProjectSpecification` default | DIR 0.2 | Retain for source compatibility |
| Builder output | DIR 0.3 | Canonical current runtime output |
| Specification Snapshot | format 1 | Retain; accepts DIR 0.3 |
| Review Bundle | format 1 | Retain |
| Evolution Report | format 1 | Retain |
| Relationship Projection Report | format 1 | Retain |
| Documentation Profile | `kotlin-android@1` | Runtime-only; no codec |

RFC-0059 implements additive DIR 0.4 Feature, Entry Point, Scenario, and ordered
Scenario Step entities. Snapshot format 2 is strict for DIR 0.4; format 1/DIR
0.3 remains supported, and migration is explicit. Manual construction still
defaults to DIR 0.2 and the default builder still emits DIR 0.3 with empty
additive collections. Feature Markdown and Evolution Report integration remain
deferred.

## Verification boundary

The delivered source ZIP has no `.git`, so branch, commit, divergence, tag presence, and clean-tree state are not inferable. Canonical Gradle verification requires JDK 21 and the repository Gradle 9.3.0 Wrapper. A network/DNS failure before Gradle distribution resolution is recorded as `NOT_EXECUTED_ENVIRONMENT_LIMITATION`, not as a code PASS or FAIL.
