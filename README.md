# DocPilot Core

> AI-native Specification Engineering Framework for Android projects

DocPilot Core is the platform-independent foundation of the DocPilot ecosystem.

It helps Android developers transform project structure, architecture, behavior, and source-code evidence into continuously evolving specifications through transparent Human-AI collaboration.

DocPilot does not generate documentation directly from source code. It first builds structured knowledge and specification models, then renders those models into documentation and diagrams.

---

## Vision

Software projects evolve continuously.

Documentation usually does not.

DocPilot aims to help Android developers keep architecture, specifications, diagrams, APIs, and engineering knowledge synchronized with source code.

The first validation targets are publicly available Android projects hosted on GitHub so that analysis results can be reproduced, reviewed, benchmarked, and improved without exposing proprietary code.

---

## Primary User

The primary user of DocPilot is the Android developer.

The initial product scope focuses on:

- Android projects
- Kotlin
- Gradle
- Android Studio workflows
- public GitHub repositories used as validation targets

Specialized domains such as Wear OS, background services, Bluetooth, Wi-Fi, Compose, Room, and WorkManager may be supported through profiles or analyzers.

---

## Core Principles

- Human First
- Specification First
- Understanding Before Generation
- Evidence Before Assumption
- Explainable Changes
- Living Specifications
- AI Vendor Independence
- Platform-Independent Core

AI proposes.

Developers review, decide, apply, and validate.

---

## Core Architecture

```text
Source Code
    ↓
Project Analyzer
    ↓
Knowledge Model
    ↓
Specification Model (DIR)
    ↓
Renderers
    ├── Markdown
    ├── Mermaid
    ├── JSON
    ├── HTML
    └── PDF
```

---

## Initial Validation Strategy

DocPilot will first be validated against publicly available Android repositories.

Validation repositories should:

- use a recognized open-source license,
- use Kotlin as a primary or significant language,
- build with Gradle,
- expose meaningful architecture or component relationships,
- be pinned to a specific commit,
- and be suitable for repeatable analysis.

The first validation corpus will include different Android project styles such as:

- simple single-module applications,
- multi-module applications,
- MVVM or layered architectures,
- background services or workers,
- networking and persistence,
- Jetpack Compose or traditional View-based applications.

---

## Repositories

| Repository | Purpose |
|---|---|
| `docpilot-core` | Platform-independent specification, knowledge, evidence, and rendering foundation |
| `docpilot-droid` | Android-specific project analyzer |

Future repositories may provide CLI, IDE, automation, or integration layers.

---

## Current Status

Current phase:

> Phase 1 — MVP / POC

Completed RFCs:

> RFC-0001 through RFC-0043

Primary validation target:

> `C:\WorkSpace\architecture-samples`

Release target:

> v0.5 MVP / POC — runtime gates verified; incremental review implemented

Implemented MVP capabilities include source scanning, knowledge construction, DIR 0.3 specification building, deterministic Markdown rendering, Stable-ID-based incremental documentation planning, specification snapshot persistence, incremental CLI execution, AI target-scoped patch generation, deterministic documentation diff/review, prompt-package creation, and AI-assisted architecture generation through the Provider SPI.

Release-gate validation on July 17, 2026 verified:

- `./gradlew clean build`
- `./gradlew test`
- core CLI analysis against `C:\WorkSpace\architecture-samples`
- Ollama generation with `qwen3:8b`
- invalid-provider error handling

OpenAI real API invocation is not part of the v0.5 validation scope.

### DIR version policy

- DIR `0.2` remains the source-compatible legacy default for manually constructed `ProjectSpecification` instances.
- DIR `0.3` is the current output produced by `DefaultSpecificationBuilder`.
- Snapshot schema versions and DIR schema versions are independent version lines.
- Renderers consume `ProjectSpecification` only; they do not interpret `SourceIndex`, the Knowledge Graph, or `IncrementalUpdatePlan`.

No stable public release has been published yet. The remaining release-policy item is the artifact version, which is still `0.1.0-SNAPSHOT`.

---

## Documentation

The current foundation documents include:

- `CONSTITUTION.md`
- `docs/vision/VISION.md`
- `docs/cdd/CDD-0001-Core-Domain-Definition.md`
- `docs/dsd/DSD-0001-DocPilot-Specification-Language.md`
- `docs/decisions/DEC-0006-Initial-Validation-Target.md`
- `ARCHITECTURE.md`
- `PROJECT_PIPELINE.md`
- `docs/release/v0.5-MVP-RELEASE-NOTES.md`
- `snapshots/v0.5-mvp/RELEASE_SUMMARY.md`

---

## License

Licensed under the Apache License 2.0.
