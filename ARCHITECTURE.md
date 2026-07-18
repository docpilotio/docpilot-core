# DocPilot Architecture

## Status

This document reflects the Phase 1 MVP / POC baseline completed through RFC-0043.

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


## AI incremental review boundary

```text
Previous / Current ProjectSpecification
        ↓
IncrementalUpdatePlan
        ↓
AI target-scoped patches
        ↓
DocumentationReviewProposal
        ↓
Complete ACCEPTED / REJECTED decision set
        ↓
Accepted patches only
        ↓
Managed-block merge
```

RFC-0043 treats AI output as a proposal rather than approved documentation. Missing patches, partial decisions, malformed managed blocks, unauthorized targets, or invalid decisions prevent merge. Review entries preserve stable IDs, specification change kind, existing/proposed Markdown, and Evidence references.

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
