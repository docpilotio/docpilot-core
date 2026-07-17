# RFC-0025 — Prompt Context Budget and Deterministic Knowledge Rendering

Status: Proposed  
Version: 0.1  
Depends on: RFC-0024

## Summary

Introduce a deterministic prompt-context budget between knowledge retrieval and AI-provider invocation.

RFC-0025 prevents an unexpectedly large knowledge subgraph, relationship set, or evidence summary from producing an unbounded AI request. It adds:

- an immutable `KnowledgeContextPolicy`,
- a `KnowledgeContextRenderer` contract,
- a deterministic `DefaultKnowledgeContextRenderer`,
- explicit truncation reporting,
- and integration with `DefaultGenerationPipeline`.

The feature is provider-independent and performs no AI summarization, embeddings, or ranking.

## Motivation

The current pipeline retrieves a bounded number of nodes, but prompt size is not fully bounded:

- one node may have many related edges,
- evidence summaries may be long,
- template content and caller variables also consume context,
- provider and model context windows differ,
- local models may spend a long time processing oversized prompts.

A node-count limit alone is therefore not a prompt-size guarantee.

## Processing Position

```text
KnowledgeBuildResult
    ↓
KnowledgeRetriever
    ↓
KnowledgeResult
    ↓
KnowledgeContextRenderer + KnowledgeContextPolicy
    ↓
Bounded deterministic context text
    ↓
PromptRenderer
    ↓
AiProvider
```

## Design Principles

1. Deterministic: identical input and policy produce identical output.
2. Provider-independent: no Ollama, OpenAI, or model-specific dependency.
3. Evidence-preserving: retained facts continue to include evidence identity and source path.
4. Explicit truncation: omitted content is reported in the rendered context.
5. Backward-compatible: the generation pipeline receives a default renderer and policy.
6. Conservative: RFC-0025 does not perform semantic compression or AI summarization.

## Proposed API

### KnowledgeContextPolicy

```kotlin
data class KnowledgeContextPolicy(
    val maxNodes: Int = 20,
    val maxEdges: Int = 40,
    val maxEvidence: Int = 40,
    val maxEvidenceSummaryCharacters: Int = 500,
    val maxCharacters: Int = 24_000,
)
```

All values must be greater than zero.

### KnowledgeContextRenderer

```kotlin
fun interface KnowledgeContextRenderer {
    fun render(
        knowledge: KnowledgeResult,
        policy: KnowledgeContextPolicy,
    ): RenderedKnowledgeContext
}
```

### RenderedKnowledgeContext

Contains:

- rendered text,
- included counts,
- omitted counts,
- and whether the character budget truncated the output.

## Rendering Rules

1. Nodes, edges, and evidence retain their incoming deterministic order.
2. Category limits are applied before the total character limit.
3. Evidence summaries are normalized to one line.
4. Individual evidence summaries are truncated with an ellipsis.
5. A final `Context Limits` section reports omitted items.
6. The returned text never exceeds `maxCharacters`.
7. Partial lines are not emitted when the final character budget is exhausted.

## Default Limits

| Item | Limit |
|---|---:|
| Nodes | 20 |
| Relationships | 40 |
| Evidence | 40 |
| Evidence summary | 500 characters |
| Entire rendered knowledge context | 24,000 characters |

These limits are implementation defaults, not model-specific context-window claims.

## Non-Goals

RFC-0025 does not define:

- embeddings,
- vector search,
- semantic ranking,
- AI-generated summaries,
- tokenizers,
- provider-specific token counting,
- multi-step generation,
- retrieval-query expansion,
- or incremental documentation.

## Compatibility

`DefaultGenerationPipeline` gains constructor parameters with defaults:

```kotlin
knowledgeContextRenderer: KnowledgeContextRenderer =
    DefaultKnowledgeContextRenderer()

knowledgeContextPolicy: KnowledgeContextPolicy =
    KnowledgeContextPolicy()
```

Existing construction sites remain source-compatible.

## Package Structure

```text
src/main/kotlin/io/docpilot/core/generation/context/
    KnowledgeContextPolicy.kt
    KnowledgeContextRenderer.kt
    RenderedKnowledgeContext.kt
    DefaultKnowledgeContextRenderer.kt
```

Modified:

```text
src/main/kotlin/io/docpilot/core/generation/
    DefaultGenerationPipeline.kt
```

Tests:

```text
src/test/kotlin/io/docpilot/core/generation/context/
    KnowledgeContextPolicyTest.kt
```

## Acceptance Criteria

RFC-0025 is complete when:

1. rendered knowledge never exceeds the configured character budget,
2. node, edge, evidence, and evidence-summary limits are enforced,
3. truncation is explicitly reported,
4. output remains deterministic,
5. existing pipeline construction sites compile unchanged,
6. all existing tests pass,
7. and `./gradlew clean test` reports `BUILD SUCCESSFUL`.

## Follow-up Work

- RFC-0026 — Multi-step Architecture Generation
- RFC-0027 — Incremental Documentation
- Future RFC — tokenizer-aware and model-aware context budgeting
