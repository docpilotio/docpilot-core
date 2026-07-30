# DocPilot Core

> AI-native Specification Engineering Framework for Android projects

DocPilot Core is the platform-independent foundation of the DocPilot ecosystem.

It transforms project structure, architecture, behavior, and source-code Evidence into versioned specifications and reviewable documentation through transparent Human-AI collaboration. Source code is first converted into structured knowledge and a canonical specification model; deterministic Core services then plan, render, review, reconcile, and explain documentation changes.

---

## Vision

Software projects evolve continuously.

Documentation usually does not.

DocPilot helps Android developers keep architecture, specifications, relationships, review decisions, and generated artifacts synchronized with source code while preserving Evidence, Stable IDs, deterministic identities, and human approval boundaries.

The first validation target remains the public Android `architecture-samples` project so analysis results can be reproduced and compared without exposing proprietary code.

---

## Primary User

The primary user is the Android developer.

The current scope focuses on Kotlin and Gradle Android projects. Specialized domains such as Wear OS, background services, Bluetooth, Wi-Fi, Compose, Room, and WorkManager may later be supported through documentation profiles or analyzers.

---

## Core Principles

- Human First
- Specification First
- Understanding Before Generation
- Evidence Before Assumption
- Explainable Changes
- Living Specifications
- Deterministic Core
- Stable Identity
- AI Vendor Independence
- Platform-Independent Core

AI may propose narrative or bounded patches. Core facts, hashes, graph edges, coverage, ownership, lifecycle state, and accepted changes remain deterministic and reviewable.

---

## Canonical Architecture

```text
Source Code
    ↓
Project Loader / Source Scanner
    ↓
Knowledge Model
    ↓
ProjectSpecification (DIR 0.3)
    ↓
RFC-0058 Documentation Profile Resolution (optional, runtime-only)
    ↓
Specification Snapshot format 1 / existing Artifact Catalog
    ↓
RFC-0052 Artifact Plan
    ↓
Selective Deterministic Rendering
    ↓
Review Bundle / Lifecycle / Receipt / Journal
    ↓
RFC-0055 Reconciliation
    ↓
RFC-0056 Evolution Report + Causal Graph
```

AI Provider implementations remain adapters behind the Provider SPI. AI output is never the canonical source of structural facts.

---

## Repositories and Modules

| Path | Purpose |
|---|---|
| root `docpilot-core` | Core model, specification, incremental, review, reconciliation, validation, and evolution capabilities |
| `docpilot-cli` | Thin command-line adapters for supported Core workflows |
| `docpilot-provider-ollama` | Ollama provider adapter |
| `docpilot-provider-openai` | OpenAI provider adapter |
| `docpilot-release` | Release Evidence and deterministic release-gate capability |
| `tools/docpilot-mcp` | Temporary development orchestration tool; not a Gradle production module |

---

## Current Status

| Item | Canonical state |
|---|---|
| Active development track | v1.1 Product Capability |
| Current RFC state | RFC-0058 Documentation Profiles and Document Contracts implemented with environment verification limitation |
| Implemented RFC sequence | RFC-0001 through RFC-0053, RFC-0055 through RFC-0058 |
| RFC-0054 | Proposed but not approved or completed |
| Public v1.0 Product Validation | `PRODUCT_VALIDATION_FAIL` / `NOT_APPROVED` |
| PV-009 | `PENDING` |
| Technical v1 baseline | Documentation reports immutable `v1.0.0`; Git identity is not verifiable from source ZIPs without `.git` |
| Gradle artifact version | `0.1.0-SNAPSHOT` |
| Next proposed RFC | RFC-0059 Feature, Entry Point, and Scenario Specification Foundation |
| Primary validation target | `C:\WorkSpace\architecture-samples` |

RFC-0058 adds the runtime-only `kotlin-android@1` Documentation Profile, deterministic Document and Section contracts, Profile/Resolution hashes, explicit completeness findings, Renderer capability declarations, RFC-0055 ownership checks, and additive RFC-0052 compatibility binding. Canonical `./gradlew clean test`, exact XML totals, an official `architecture-samples` Profile fixture, Windows CLI smoke, Git clean-tree evidence, and independent PV-009 review remain unverified in the delivered ZIP baseline.

### Current technical formats

| Contract | Version |
|---|---|
| Builder-emitted DIR schema | `0.3` |
| Legacy manual `ProjectSpecification` default | `0.2` |
| Specification Snapshot | `1` |
| Snapshot-supported DIR | `0.3` |
| Review Bundle | `1` |
| Evolution Report | `1` |
| Relationship Projection Report | `1` |
| Documentation Profile | `kotlin-android@1` runtime-only |

Snapshot format and DIR schema are independent version lines. RFC-0058 does not introduce DIR 0.4, persist Profiles, or change Snapshot, Review, Reconciliation, Projection, or Evolution formats.

---

## Implemented Capabilities

- Kotlin/Gradle project loading, source scanning, and Evidence indexing
- deterministic knowledge construction and DIR 0.3 specification building
- deterministic Markdown and multi-artifact selective rendering
- Stable-ID specification diffing and incremental planning
- Specification Snapshot persistence and incremental CLI workflow
- provider-independent AI target-scoped patch generation
- deterministic documentation diff, Review Bundle persistence, lifecycle, receipt, journal, recovery, and thin CLI operations
- deterministic INTERNAL, EXTERNAL, and UNRESOLVED relationship semantics
- relationship-aware impact planning and Projection Report integrity
- release Evidence manifest and offline release-gate verification
- existing-document ownership, preview-first reconciliation, conflict handling, retained content, and user decision binding
- Documentation Quality Validator used by the product-validation workflow; its presence does not imply RFC-0054 completion
- deterministic Evolution Report, causal graph, coverage, strict codec, offline verification, and narrative-only AI boundary
- immutable Documentation Profiles, Document and Section contracts, section-level Evidence policy, Renderer capability requirements, completeness states, ownership conflict handling, and deterministic Profile Resolution

There is no official CLI or MCP command for RFC-0055 Reconciliation, RFC-0056 Evolution generation, or RFC-0058 Profile Resolution in this baseline.

---

## Documentation

Canonical current-state documents include:

- `docs/vision/VISION.md`
- `docs/cdd/CDD-0001-Core-Domain-Definition.md`
- `docs/dsd/DSD-0001-DocPilot-Specification-Language.md`
- `ARCHITECTURE.md`
- `PROJECT_PIPELINE.md`
- `docs/roadmap/ROADMAP.md`
- `docs/planning/DOCPILOT-CANONICAL-BASELINE.properties`
- `docs/planning/RFC-0057-CANONICAL-BASELINE-REPORT.md`
- `docs/planning/RFC-0057-CODE-DOCUMENT-CONSISTENCY-REPORT.md`
- `docs/planning/RFC-0057-DIR-0.4-MIGRATION-READINESS.md`
- `docs/rfc/RFC-0058-Documentation-Profiles-and-Document-Contracts.md`
- `docs/planning/RFC-0058-MAIN-PLANNING-UPDATE.md`
- `docs/handoffs/RFC-0058-COMPLETION-HANDOFF.md`
- `docs/release/DOCPILOT-V1-PRODUCT-VALIDATION-REPORT.md`
- `docs/release/DOCPILOT-V1-RELEASE-DECISION.md`

Historical RFC, planning, release, and handoff documents retain their original scope and evidence. Later canonical documents do not retroactively convert unexecuted validation into PASS.

---

## License

Licensed under the Apache License 2.0.
