# RFC-0020: Document Service

Status: Accepted

## Problem

RFC-0018 produces a `GenerationResult`, and RFC-0019 defines a stable
provider-independent `Document` model. The core still lacks one application
service that connects those two capabilities. Callers would otherwise need to
repeat provider-result inspection, failure handling, metadata construction, and
document assembly.

## Decision

Introduce a small document service package:

- `DocumentRequest`
- `DocumentService`
- `DefaultDocumentService`
- `DocumentGenerationException`

`DefaultDocumentService` delegates generation to an injected
`GenerationPipeline`. A successful AI response becomes one `DocumentSection`.
A failed AI response becomes a `DocumentGenerationException` that preserves the
original `AiError`.

Generated documents include deterministic provenance metadata:

- `ai.providerId`
- `ai.modelId`
- `ai.finishReason`

Callers may add metadata but may not replace these reserved keys.

## Processing Flow

```text
DocumentRequest
      |
      v
GenerationPipeline.generate
      |
      v
GenerationResult
      |
      +-- Success --> Document
      |
      +-- Failure --> DocumentGenerationException
```

## Scope

- Orchestrating an existing `GenerationPipeline`
- Converting successful generated text into the RFC-0019 document model
- Preserving provider/model/finish-reason provenance
- Providing explicit generation failure behavior
- Unit tests for success, failure, and reserved metadata validation

## Out of Scope

- File persistence
- Markdown or plain-text file writing
- HTML or PDF output
- Template registry or template selection
- AI provider selection
- Parsing generated Markdown into multiple sections
- Architecture-specific or ADR-specific document generation
- CLI integration
