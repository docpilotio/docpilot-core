# RFC-0023: ADR Generator

- **Status:** Accepted
- **Target:** `docpilot-core`
- **Depends on:** RFC-0017 through RFC-0022

## Summary

RFC-0023 adds an ADR-specific generator that converts explicit architecture decision data and retrieved project knowledge into DocPilot's provider-independent `Document` model.

It also introduces the common `DocumentGenerator<R>` contract and makes both the architecture and ADR generators conform to it.

## Motivation

The generic `DocumentService` can produce a document but does not define the semantics of an Architecture Decision Record. ADR generation requires stable inputs for status, context, decision, alternatives, and consequences. These inputs must remain authoritative while the AI provider uses project knowledge only to improve explanation and evidence.

## Design

### Common generator contract

```kotlin
fun interface DocumentGenerator<in R> {
    fun generate(request: R): Document
}
```

Document-specific interfaces extend this contract. Existing callers of `ArchitectureGenerator` remain source compatible.

### ADR request

`AdrGenerationRequest` requires:

- project knowledge;
- AI model identifier;
- title;
- context;
- decision;
- consequences.

Alternatives default to an explicit statement that none were supplied. Status defaults to `accepted` and supports `proposed`, `accepted`, `deprecated`, and `superseded`.

### Prompt variables

The generator owns these reserved variables:

- `adr.title`
- `adr.status`
- `adr.context`
- `adr.decision`
- `adr.alternatives`
- `adr.consequences`

Callers cannot redefine them. The generation pipeline continues to own `knowledge`.

### Metadata

Generated ADR documents use:

```text
type = adr
template.id = adr
document.generator = adr
adr.status = <status>
adr.title = <title>
```

Generator and template metadata override caller metadata for the same keys.

## Processing flow

```text
AdrGenerationRequest
        -> TemplateRegistry (adr)
        -> GenerationRequest
        -> DocumentService
        -> Document
```

## Non-goals

This RFC does not add:

- automatic ADR numbering;
- ADR index management;
- supersession links;
- file persistence;
- approval workflows;
- Git history integration;
- CLI commands.

## Compatibility

The implementation builds on RFC-0022 without changing `DocumentService`, `GenerationPipeline`, or the document model. The built-in ADR prompt is extended with the ADR-specific reserved variables.

## Acceptance criteria

- `DocumentGenerator<R>` is available as a common contract.
- `ArchitectureGenerator` conforms to the common contract.
- ADR request validation is deterministic.
- ADR-specific prompt variables and metadata are generated correctly.
- Missing ADR templates fail with `TemplateNotFoundException`.
- Unit tests cover defaults, propagation, precedence, missing templates, and reserved variables.
