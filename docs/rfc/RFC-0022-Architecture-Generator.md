# RFC-0022: Architecture Generator

- Status: Implemented
- Target: DocPilot Core
- Depends on: RFC-0017, RFC-0018, RFC-0019, RFC-0020, RFC-0021

## 1. Summary

RFC-0022 introduces the first document-type-specific generator in DocPilot.
`ArchitectureGenerator` provides a focused API for producing an architecture
`Document` while reusing the generic template registry, generation pipeline,
and document service introduced by the preceding RFCs.

## 2. Motivation

The generic document infrastructure can produce arbitrary AI-backed documents,
but callers currently have to assemble template selection, generation options,
section metadata, and document metadata themselves. Product-facing generators
should encode those conventions once and expose a small domain-specific API.

Architecture documentation is the first supported product use case.

## 3. Design

The generation flow is:

```text
ArchitectureGenerationRequest
        |
        v
ArchitectureGenerator
        |
        +-- TemplateRegistry.get("architecture")
        |
        v
DocumentRequest + GenerationRequest
        |
        v
DocumentService
        |
        v
Document
```

### 3.1 API

The RFC adds:

```text
io.docpilot.core.generator.architecture
├── ArchitectureGenerationRequest
├── ArchitectureGenerator
└── DefaultArchitectureGenerator
```

`ArchitectureGenerationRequest` contains project knowledge, model selection,
knowledge query, prompt variables, output format, AI generation options, and
separate metadata maps for the AI request and resulting document.

### 3.2 Template selection

`DefaultArchitectureGenerator` resolves the template identified by
`BuiltInTemplates.ARCHITECTURE.id`. Missing registration is reported through
the existing `TemplateNotFoundException`.

### 3.3 Document conventions

Generated architecture documents use:

- metadata type: `architecture-document`
- section id: `architecture`
- section title: the template's `defaultSectionTitle`
- metadata `template.id=architecture`
- metadata `document.generator=architecture`

Template and generator metadata are authoritative and cannot be replaced by
caller-provided document metadata.

## 4. Error handling

This generator does not translate downstream failures. Template lookup,
prompt rendering, provider generation, and document conversion errors retain
their existing exception contracts.

## 5. Out of scope

RFC-0022 does not introduce:

- Markdown parsing into multiple document sections
- architecture diagram generation
- C4, UML, Mermaid, or PlantUML output
- architecture quality scoring
- project scanning or knowledge-graph construction
- file persistence
- CLI commands

## 6. Testing

Unit tests verify:

- correct architecture template resolution
- deterministic request mapping
- default request values
- generation and document metadata separation
- authoritative template metadata
- missing-template failure

## 7. Consequences

The architecture generator is deliberately thin. It establishes the pattern
for later document-type generators, including ADR generation, without
duplicating provider, prompt, retrieval, or document-model responsibilities.
