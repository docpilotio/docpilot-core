# RFC-0021: Template Registry

- Status: Accepted
- Target: docpilot-core
- Depends on: RFC-0015, RFC-0018, RFC-0019, RFC-0020

## Summary

DocPilot needs stable, discoverable definitions for the different documents it can generate. This RFC introduces a provider-neutral document template model and a registry that supports deterministic registration, lookup, and listing.

## Motivation

Before this RFC, callers constructed `PromptTemplate` instances directly for every generation request. That makes document types difficult to discover, encourages duplicated prompts, and prevents future generators and CLI commands from referring to a stable template identifier.

## Decision

Introduce package `io.docpilot.core.template` with:

- `TemplateId`: validated stable identifier.
- `DocumentTemplate`: template metadata plus an RFC-0015 `PromptTemplate`.
- `TemplateRegistry`: registration, optional lookup, required lookup, and deterministic listing.
- `InMemoryTemplateRegistry`: thread-safe process-local implementation.
- `DuplicateTemplateException` and `TemplateNotFoundException`.
- `BuiltInTemplates`: architecture, ADR, API, and README definitions.

`TemplateRegistry.list()` is ordered by `TemplateId`, not registration order. Duplicate registration is rejected rather than silently replacing an existing template.

## Prompt compatibility

`DocumentTemplate.prompt` reuses the existing provider-neutral `PromptTemplate`. Built-in templates include the reserved `{{knowledge}}` placeholder consumed by `DefaultGenerationPipeline`. This RFC does not change `GenerationRequest` or `DocumentService`; that integration belongs to a later generator/service RFC.

## Built-in identifiers

- `architecture`
- `adr`
- `api`
- `readme`

The initial prompts emphasize evidence-backed output and prohibit unsupported claims.

## Non-goals

This RFC does not add:

- file-system template loading;
- YAML or JSON formats;
- template inheritance or includes;
- remote registries;
- mutable replacement or removal;
- template versioning;
- CLI integration;
- automatic conversion from a template into `DocumentRequest`.

## Consequences

### Positive

- Document types have stable identifiers.
- Prompt definitions are centralized and reusable.
- Tests and callers receive deterministic template ordering.
- The registry remains independent from AI providers and output formats.

### Trade-offs

- The in-memory registry is process-local.
- Built-in prompts are compiled into core.
- Template-to-service integration remains explicit for now.

## Validation

The test suite covers:

- registration and retrieval;
- optional and required lookup;
- duplicate rejection;
- deterministic listing;
- built-in templates;
- identifier validation.

## Follow-up

RFC-0022 may introduce an Architecture Generator that resolves the `architecture` template and constructs a document generation request without exposing prompt assembly to callers.
