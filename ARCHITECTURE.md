# DocPilot Architecture

## Status

This document reflects the v0.5 MVP release-candidate baseline completed through RFC-0038.

## Core pipeline

```text
Target Project
    ↓
Project Loader
    ↓
Source Scanner
    ↓
SourceIndex
    ↓
Knowledge Builder
    ↓
KnowledgeBuildResult / Knowledge Graph
    ↓
Specification Builder
    ↓
ProjectSpecification (DIR 0.3)
    ↓
Markdown Renderer
    ↓
Deterministic Documentation
```

The renderer consumes `ProjectSpecification` only. It does not interpret `SourceIndex`, the Knowledge Graph, or `IncrementalUpdatePlan`.

## AI generation pipeline

```text
Target Project
    ↓
Analysis artifacts and prompt package
    ↓
AI Provider SPI
    ↓
Provider implementation
    ↓
AI model
    ↓
Generated document
    ↓
Output Writer
```

The v0.5 MVP release smoke test verified the Ollama provider with `qwen3:8b`. OpenAI runtime invocation is outside this release-validation scope.

## Incremental documentation

```text
Previous ProjectSpecification
+
Current ProjectSpecification
    ↓
Stable-ID Specification Diff
    ↓
SpecificationChange set
    ↓
Deterministic IncrementalUpdatePlan
```

RFC-0037 introduced Stable-ID-based specification diffing. RFC-0038 stabilized moved-entity propagation so affected scopes include both previous and current owners when APIs, Properties, or Types move while retaining identity.

Snapshot Incremental and Specification Incremental remain separate subdomains.

## Architectural boundaries

- Scanner extracts source evidence.
- Knowledge Builder constructs structured knowledge.
- Specification Builder creates canonical DIR entities.
- Renderer is presentation-only.
- AI providers are replaceable adapters behind the Provider SPI.
- Canonical project structure must not depend on AI output.
- Deterministic outputs and AI-generated outputs have different repeatability expectations.

## DIR version policy

- DIR `0.2` is the source-compatible legacy default for manually constructed `ProjectSpecification` instances.
- DIR `0.3` is the canonical output of `DefaultSpecificationBuilder`.
- Snapshot schema versions and DIR schema versions are independent.
