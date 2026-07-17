# RFC-0015 — Prompt Orchestration

Status: Accepted

## Problem

`PromptPackage` alone does not provide reusable, testable prompt templates.
Prompt generation must remain independent from OpenAI, Ollama, and other providers.

## Decision

Add a small prompt rendering layer:

```text
PromptTemplate + PromptVariables
              ↓
        PromptRenderer
              ↓
        RenderedPrompt
```

Templates use Markdown and strict `{{variable}}` replacement. Missing variables
fail immediately. File templates are loaded as UTF-8 from a fixed repository root.

## Scope

- prompt templates,
- strict variable replacement,
- rendered prompts,
- UTF-8 file loading,
- Markdown preservation.

## Out of Scope

- conditions and loops,
- template includes,
- prompt caching,
- conversation history,
- streaming,
- tool calling,
- provider-specific prompt optimization.
