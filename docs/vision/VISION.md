# Vision

## Vision Statement

DocPilot aims to become the AI-native Specification Engineering Platform for Android projects.

Instead of treating documentation as a by-product, DocPilot treats project knowledge, Evidence, decisions, and change explanations as first-class engineering assets.

Its long-term vision is to keep architecture, specifications, diagrams, APIs, scenarios, contracts, and design knowledge continuously synchronized with source code through transparent Human-AI collaboration.

---

## Why DocPilot Exists

Android projects evolve every day. Documentation usually does not.

As projects grow, knowledge becomes fragmented across source code, documents, diagrams, tests, issue trackers, code reviews, and developer experience. DocPilot reconnects these sources through deterministic specifications and explicitly reviewed changes.

---

## Mission

Transform Android projects into continuously evolving, Evidence-based specifications that developers can review, trust, maintain, and compare over time.

---

## Product Principles

- Evidence before assumption
- deterministic Core before AI enrichment
- Stable IDs before generated prose
- explicit ownership and human decisions
- complete review before apply
- explainable before/after change intelligence
- versioned contracts and offline verification
- AI-provider independence
- public validation against reproducible Android corpora

---

## Long-Term Goals

- Understand Android projects rather than only parse files.
- Maintain living specifications and document contracts.
- Discover user-meaningful features, entry points, scenarios, and interactions.
- Produce diagrams and data contracts from deterministic intermediate representations.
- Detect architectural and behavioral changes.
- Explain why each documentation artifact changed.
- Reconcile generated knowledge with existing human documentation safely.
- Validate behavior against pinned public Android repositories.
- Support multiple platforms later through a platform-independent Core.
- Remain independent from any specific AI vendor.

---

## Initial Scope

- Android projects
- Kotlin
- Gradle
- Android Studio workflows
- publicly available Android repositories hosted on GitHub

Specialized profiles may later cover Wear OS, Bluetooth, Wi-Fi, background services, Compose, Room, WorkManager, and other Android domains.

---

## Documentation Expansion Direction

The DIR 0.3 and Snapshot format 1 baseline is fixed. RFC-0058 now implements Documentation Profile and Document Contract policy; the remaining planned production concepts include:

- Documentation Profile — implemented as runtime-only `kotlin-android@1` policy
- Document Contract — implemented with deterministic Profile Resolution
- Feature Specification
- Entry Point Specification
- Scenario Specification
- Interaction Step
- Contract Specification
- Documentation Claim
- Diagram Specification / Diagram IR

These concepts must be Evidence-backed, Stable-ID-addressable, deterministic when Core-derived, compatible with existing Artifact Planning, Review, Reconciliation, and Evolution contracts, and absent rather than invented when source Evidence is insufficient.

---

## Success Criteria

An Android developer should be able to change code and immediately understand:

- what changed,
- why it matters,
- which features, scenarios, specifications, and documents are affected,
- what Evidence supports each claim,
- what requires human review,
- and what architectural impact the change introduces.

---

## Validation Governance

Technical build or tag evidence does not by itself approve a public product release. Product Validation must use documented hard gates, reproducible fixtures, exact outputs, and organizationally independent review where required.

The current public v1.0 decision remains `PRODUCT_VALIDATION_FAIL` / `NOT_APPROVED`; PV-009 remains `PENDING`.

---

## Status

- Status: Active product direction
- Version: 0.3
- Canonical baseline: RFC-0058
- Current development track: v1.1 Product Capability
- Authority: Product Owner
