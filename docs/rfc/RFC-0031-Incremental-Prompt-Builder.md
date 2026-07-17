# RFC-0031 — Incremental Prompt Builder

Status: Accepted  
Version: 1.0  
Depends on: RFC-0025, RFC-0029, RFC-0030

## Summary

RFC-0031 converts one RFC-0030 `GenerationJob` into a deterministic,
provider-neutral `PromptPlan`.

The prompt plan contains separate system and task instructions, bounded source
context, explicit constraints, and an output contract. It does not invoke an AI
provider, perform HTTP communication, save generated Markdown, or validate an
AI response.

## Processing Position

```text
ProjectChangeSet
        +
KnowledgeBuildResult
        +
GenerationJob
        +
Existing section content (optional)
        ↓
IncrementalPromptBuilder
        ↓
PromptPlan
        ↓
RFC-0032 provider adapter and generation execution
```

## Goals

1. Build prompts independently of Ollama, OpenAI, Claude, or Gemini.
2. Produce identical prompt plans for identical logical input.
3. Include only changed files, affected knowledge, and related evidence.
4. distinguish new-section creation from existing-section updates.
5. Enforce the context token budget assigned by RFC-0030.
6. Return an explicit Markdown section output contract.
7. Report missing or truncated context without failing valid deletion flows.

## Non-goals

RFC-0031 does not define:

- AI-provider invocation,
- provider message schemas,
- retries or timeouts,
- response parsing,
- Markdown persistence,
- response review and validation,
- model-specific tokenizers,
- or prompt execution queues.

## Core API

```kotlin
fun interface IncrementalPromptBuilder {
    fun build(request: PromptBuildRequest): PromptPlan
}
```

`PromptBuildRequest` combines:

- one `GenerationJob`,
- the current `KnowledgeBuildResult`,
- the `ProjectChangeSet`,
- and optional existing section content.

`PromptPlan` contains:

- `systemInstruction`,
- `taskInstruction`,
- a structured `PromptContext`,
- sorted `PromptConstraint` values,
- `PromptOutputContract`,
- deterministic estimated input tokens,
- the assigned input token budget,
- and non-fatal warnings.

## Context Selection

`DefaultPromptContextSelector` applies the following deterministic selection
order:

1. changed files excluding `UNCHANGED`,
2. knowledge nodes named by the generation job,
3. evidence explicitly named by the job,
4. evidence referenced by affected nodes,
5. evidence located in changed files,
6. optional previous section content.

Duplicate items are removed. Final collections use stable path, line, and ID
ordering.

Knowledge nodes or evidence removed from the current graph do not make prompt
construction fail. The builder records warnings so file-deletion prompts remain
possible.

## Budgeting

RFC-0031 uses `GenerationJob.contextTokenBudget` as the maximum estimated input
budget. Fixed instructions and constraints are estimated first. The remaining
budget is passed to `PromptContextSelector`.

`DeterministicPromptTokenEstimator` follows RFC-0025's provider-independent,
character-bounded approach. It estimates one token per four characters by
default. This is intentionally not a claim about any provider tokenizer.

Evidence summaries are normalized to one line and bounded to 500 characters,
matching RFC-0025's default evidence-summary limit. Existing section content is
truncated deterministically when necessary.

## Constraints

Every prompt receives common constraints that:

- forbid unsupported facts and relationships,
- preserve source identifiers and project terminology,
- limit output to the requested section,
- prevent unnecessary rewrites,
- and require direct Markdown without a code fence.

The dependencies section additionally forbids guessed versions and confusion
between direct and transitive dependencies. The executive summary receives a
constraint against duplicating low-level detail.

## Output Contract

The default output contract is:

```text
format: MARKDOWN_SECTION
includeHeading: true
allowAdditionalSections: false
```

Provider adapters in RFC-0032 may translate the structured plan into their own
message format, but must preserve this contract.

## Error Policy

Prompt construction fails when the assigned budget cannot contain the fixed
prompt and a minimal context allowance.

The following conditions are warnings rather than failures:

- an affected node is absent from the current graph,
- affected evidence is absent,
- relevant context is omitted by the budget,
- previous section content is truncated.

## RFC-0030 Compatibility Correction

This package also narrows the RFC-0030 system-context keywords by removing the
overly broad `project` and `main` terms. Those terms matched ordinary
`src/main/...` paths and generic project node IDs, causing the two reported
planner tests to select an unintended `system-context` job.

No planner API changes are introduced.

## Acceptance Criteria

RFC-0031 is complete when:

1. a `GenerationJob` can be converted into a `PromptPlan`,
2. the prompt package imports no AI provider implementation,
3. output is deterministic for reordered graph and evidence input,
4. unrelated evidence is excluded,
5. new and existing section instructions differ,
6. deletion prompts work with missing current knowledge,
7. estimated input does not exceed the assigned budget,
8. insufficient budgets fail explicitly,
9. RFC-0030's two reported tests pass,
10. and the project test suite passes in a Gradle-enabled environment.
