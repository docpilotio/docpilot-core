# RFC-0018 — AI Generation Pipeline

Status: Accepted

## Problem

Knowledge retrieval, prompt rendering, and AI providers exist independently.
There is no single API that connects them for one generation request.

## Decision

Add a small generation pipeline:

```text
KnowledgeQuery
    ↓
KnowledgeRetriever
    ↓
PromptRenderer
    ↓
AiProvider
    ↓
GenerationResult
```

The AI provider is supplied to the pipeline. Provider selection remains outside
this RFC.

The pipeline adds one reserved prompt variable:

```text
{{knowledge}}
```

It contains a deterministic Markdown representation of retrieved nodes,
relationships, and evidence.

## Scope

- `GenerationRequest`
- `GenerationResult`
- `GenerationPipeline`
- `DefaultGenerationPipeline`
- focused unit tests

## Out of Scope

- provider selection or fallback
- retry
- cache
- streaming
- conversation
- tool calling
- prompt optimization
