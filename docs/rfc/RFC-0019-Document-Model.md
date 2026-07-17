# RFC-0019: Document Model

Status: Accepted

## Problem

AI generation currently returns provider text, but DocPilot has no stable,
provider-independent representation for generated documents. Passing raw strings
directly to CLI, file, HTML, or PDF outputs would couple later output features to
AI provider responses.

## Decision

Introduce a small document model consisting of:

- `Document`
- `DocumentMetadata`
- `DocumentSection`
- `DocumentFormat`
- `DocumentRenderer`

A document contains a title, ordered sections, an output format, and optional
metadata. Section identifiers must be unique. Rendering is deterministic and has
no dependency on an AI provider.

RFC-0019 supports Markdown and plain-text rendering only.

## Scope

- Stable document and section value types
- Optional document metadata
- Markdown rendering
- Plain-text rendering
- Input validation and deterministic metadata ordering

## Out of Scope

- Converting `GenerationResult` into a `Document`
- Document generation services
- File persistence
- HTML or PDF output
- Diagram rendering
- Template selection
- Provider selection
