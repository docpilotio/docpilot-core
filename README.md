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

> Sprint 0 — Project Foundation

Current focus:

- project constitution,
- vision,
- core domain definition,
- specification language,
- validation strategy,
- and the first end-to-end MVP design.

No stable release is available yet.

---

## Documentation

The current foundation documents include:

- `CONSTITUTION.md`
- `docs/vision/VISION.md`
- `docs/cdd/CDD-0001-Core-Domain-Definition.md`
- `docs/dsd/DSD-0001-DocPilot-Specification-Language.md`
- `docs/decisions/DEC-0006-Initial-Validation-Target.md`

---

## License

Licensed under the Apache License 2.0.
