# RFC-0058 — Documentation Profiles and Document Contracts

## Status

`IMPLEMENTED_WITH_ENVIRONMENT_VERIFICATION_LIMITATION`

## Track

v1.1 Product Capability

## Purpose

RFC-0058 defines a deterministic Core contract for deciding which documents a project profile expects, why each document exists, who reads it, which sections and Evidence it requires, which Renderer capabilities it depends on, where it would be written, who may own it, and how unsupported or incomplete content is represented.

This RFC establishes policy and resolution. It does not replace the existing RFC-0052 Artifact Catalog or `DocumentationArtifactPlan`, does not generate new Profile Markdown, and does not infer Feature or Contract entities that DIR 0.3 does not contain.

## Selected design

Candidate A was approved:

```text
Immutable built-in Profile
        ↓
Profile validation and canonicalization
        ↓
Profile + ProjectSpecification + Renderer capabilities + Ownership state
        ↓
DocumentationProfileResolution
        ↓
Compatibility binding to the existing RFC-0052 Artifact Catalog
```

The Profile is reusable policy. The Resolution is a project-specific readiness result. RFC-0052 remains the sole CREATE / UPDATE / KEEP Artifact operation plan.

## Architecture boundary

Added package:

```text
io.docpilot.core.documentation.profile
```

The package owns:

- Profile, Document, Section, Evidence, path, ownership, and capability contracts;
- immutable built-in Profile registration;
- Profile validation and canonicalization;
- Profile and Resolution semantic identities;
- deterministic project-specific resolution;
- compatibility binding to existing RFC-0052 Artifacts.

`ProjectSpecificationMarkdownRenderer` now implements `DocumentationRendererCapabilityProvider` and declares only capabilities it already supports. Its Artifact descriptors, output paths, Markdown rendering, selection behavior, and content remain unchanged.

## Profile identity

Profile IDs use:

```text
^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$
```

The first built-in Profile is:

```text
id: kotlin-android
version: 1
```

Profile version 1 is the only supported runtime version in this RFC. Unknown IDs or unsupported versions fail explicitly.

## Document contract

A `DocumentDefinition` defines:

- `DocumentType`;
- stable key;
- purpose;
- primary and secondary audiences;
- multiplicity;
- fixed or patterned output path;
- required and optional sections;
- section-level Evidence requirements;
- section and document Renderer capabilities;
- completeness policy;
- ownership policy;
- dependency rules;
- required production model.

Stable definition identity:

```text
document-definition:{profileId}:{documentStableKey}
```

Stable document instance identity:

```text
document:{profileId}:{documentStableKey}:{scopeId}
```

Section identity:

```text
section:{profileId}:{documentStableKey}:{sectionId}
```

Profile version is not embedded in these Stable IDs. Version changes preserve semantic continuity while changing the Profile semantic hash.

## Multiplicity

Supported policy values:

- `SINGLE`
- `PER_MODULE`
- `PER_PACKAGE`
- `PER_COMPONENT`
- `PER_FEATURE`
- `PER_EXTERNAL_SYSTEM`

DIR 0.3 can resolve Project, Module, Package, Component, and external Relationship targets. It cannot resolve Feature production entities. `PER_FEATURE` therefore produces an explicit deferred contract and does not create temporary or AI-inferred Feature IDs.

## Path policy

Path validation rejects:

- blank paths;
- absolute paths;
- drive-qualified Windows paths;
- UNC-style roots;
- `.` and `..` traversal;
- malformed or unknown placeholders;
- fixed paths used with scoped multiplicity;
- patterns that do not identify their multiplicity;
- duplicate fixed Profile paths;
- duplicate resolved paths.

Backslashes are normalized to `/` before semantic identity is calculated. Scope placeholder values are converted to deterministic safe segments and Stable-ID hashes rather than copied as filesystem paths.

## Section Evidence

`SectionEvidenceRequirement` defines:

- minimum Evidence count;
- allowed Evidence classes: `VERIFIED`, `CORE_DERIVED`, `AI_INFERRED`;
- Evidence subject: project purpose, architecture, module, feature, contract, test, or any.

Current DIR Evidence is deterministically classified from its type. Subject matching uses bounded Evidence metadata and never changes source facts.

Missing section behavior is explicit:

- `MARK_UNKNOWN`
- `DEFER_DOCUMENT`
- `BLOCK_DOCUMENT`
- `OMIT_OPTIONAL_SECTION`

No empty Mermaid blocks, invented Features, invented Contracts, or hidden UNKNOWN states are produced.

## Renderer capability contract

Capability declarations include:

- Markdown section rendering;
- module dependency diagram;
- feature class diagram;
- sequence diagram;
- state-flow diagram;
- data-flow diagram;
- Evidence reference rendering;
- UNKNOWN finding rendering.

The current deterministic Markdown Renderer declares:

- `MARKDOWN_SECTION_RENDERING`
- `EVIDENCE_REFERENCE_RENDERING`
- `UNKNOWN_FINDING_RENDERING`

It does not claim Diagram capabilities.

A missing required capability produces `UNSUPPORTED`. A missing optional capability omits that optional section and records a non-blocking finding.

## Completeness

Document planning states are:

- `READY`
- `PARTIAL`
- `DEFERRED`
- `BLOCKED`
- `UNSUPPORTED`

Findings include:

- missing specification element;
- missing required Evidence;
- missing Renderer capability;
- missing Feature model;
- missing Contract model;
- unsupported multiplicity;
- path conflict;
- ownership conflict;
- reconciliation required;
- Profile version mismatch;
- unbound legacy Artifact.

Status precedence is fail-closed: path and ownership conflicts block; missing required models defer; missing required capabilities are unsupported; required blocked sections block; explicit incomplete sections are partial.

## Ownership and Reconciliation

RFC-0058 reuses RFC-0055 `DocumentationOwnership`:

- `DOCPILOT_OWNED`
- `USER_OWNED`
- `SHARED_MANAGED`
- `UNKNOWN`
- `CONFLICTED`

Profile definitions cannot default generated documents to `USER_OWNED`, `UNKNOWN`, or `CONFLICTED`. `SHARED_MANAGED` requires reconciliation.

Resolved path collision policy:

```text
DOCPILOT_OWNED → permitted by ownership policy
SHARED_MANAGED → PARTIAL + RECONCILIATION_REQUIRED
USER_OWNED / UNKNOWN / CONFLICTED → BLOCKED + OWNERSHIP_CONFLICT
```

Profile policy does not authorize deletion or overwrite. RFC-0055 remains the merge and user-decision boundary.

## Built-in kotlin-android Profile

Version 1 defines:

| Document | Multiplicity | Path | DIR 0.3 expected state |
|---|---|---|---|
| Project Overview | SINGLE | `project/project-overview.md` | READY or PARTIAL |
| Feature Catalog | SINGLE | `project/feature-catalog.md` | DEFERRED |
| Architecture Overview | SINGLE | `architecture/architecture-overview.md` | READY or PARTIAL |
| Module Architecture | SINGLE | `architecture/module-architecture.md` | READY or PARTIAL |
| Feature Specification | PER_FEATURE | `features/{featureId}-{slug}.md` | DEFERRED |
| Domain Model | SINGLE | `contracts/domain-model.md` | DEFERRED |
| Database Schema | SINGLE | `contracts/database-schema.md` | DEFERRED |
| External API Contract | SINGLE | `contracts/external-apis.md` | DEFERRED |
| Test Strategy | SINGLE | `quality/test-strategy.md` | READY or PARTIAL |

Feature documents require a Feature production model. Domain, Database, and external API documents require a canonical Contract production model. DIR 0.3 components and external relationship endpoints are not silently promoted into those models.

## Legacy coexistence

The existing workflow remains the default:

```text
No Profile workflow selected
→ existing ProjectSpecificationMarkdownRenderer
→ existing Artifact Catalog
→ existing RFC-0052 Artifact Plan
→ unchanged paths and semantic hashes
```

Profile paths are additive contracts. They do not move or delete the existing Artifact set.

`ProfileArtifactCompatibility` binds only:

- exact path matches; or
- explicit one-to-one legacy kind compatibility for Project Overview and Architecture Overview.

Unbound contracts remain visible. They are not treated as RFC-0052 CREATE operations until a later Profile-aware rendering RFC explicitly projects them into an Artifact Catalog.

## Semantic identity

Profile SHA-256 includes canonical:

- Profile ID and version;
- display name and project kinds;
- Document type, stable key, purpose, audience, multiplicity, path;
- sections, titles, order, required state, Evidence policy, missing behavior;
- Renderer capabilities;
- completeness and ownership policies;
- dependency rules and required model.

Resolution SHA-256 includes:

- Profile identity;
- resolved document and section contracts;
- source IDs, paths, statuses, Evidence refs, capabilities, findings;
- RFC-0052 compatibility bindings.

Excluded values include timestamps, absolute repository paths, filesystem order, map insertion order, object identity, and AI narrative.

Section title changes preserve Section Stable ID but change semantic identity because they alter the reviewable document contract.

## Persisted formats

Unchanged:

- DIR Builder output: 0.3
- manual `ProjectSpecification` default: 0.2
- Specification Snapshot: format 1
- Review Bundle: format 1
- Relationship Projection Report: format 1
- Reconciliation and Ownership formats: 1
- Evolution Report: format 1

Profiles and Resolutions are runtime-only in RFC-0058. No Profile codec or Snapshot metadata is added.

## Verification

Executed targeted verification:

- changed Profile and Renderer source subset compilation with JDK 21 and local Kotlin compiler: PASS;
- 18 RFC-0058 targeted test methods through an isolated runner: PASS;
- 4 existing `ProjectSpecificationMarkdownRendererTest` regression methods: PASS;
- source-diff inspection confirms the only pre-document production changes are the new Profile package and additive Renderer capability declaration: PASS.

Canonical Gradle verification:

```text
./gradlew clean test
```

was not started because the environment could not resolve `services.gradle.org` to download Gradle 9.3.0. State: `NOT_EXECUTED_ENVIRONMENT_LIMITATION`.

The targeted compiler is Kotlin 1.9.0, while the canonical build declares Kotlin 2.4.0. Targeted compilation and execution are supporting evidence, not a replacement for the Gradle suite.

## Out of scope

- DIR 0.4;
- Feature, EntryPoint, and Scenario production models;
- Contract extraction;
- Diagram IR or Mermaid rendering;
- Profile-aware Markdown generation;
- new CLI or MCP commands;
- Snapshot, Review, Reconciliation, or Evolution codec changes;
- removal or migration of legacy Artifacts;
- RFC-0054 completion;
- public v1.0 approval;
- PV-009 completion;
- v1.1 Release Candidate declaration.
