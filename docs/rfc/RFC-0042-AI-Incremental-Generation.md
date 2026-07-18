# RFC-0042 — AI Incremental Generation

## Status

Implemented.

## Purpose

Generate AI documentation patches only for targets selected by the specification incremental plan. The provider receives changed specification fragments instead of the complete `ProjectSpecification`, and returns target-scoped Markdown patches rather than a complete document.

## Design

- `DefaultSpecificationIncrementalPromptBuilder` serializes only Added, Removed, and Modified plan actions.
- `DefaultAiIncrementalDocumentationGenerator` uses the existing `AiProvider` SPI without provider-specific branching.
- `MarkerAiDocumentationPatchCodec` accepts strict target-scoped response blocks.
- `ManagedBlockAiDocumentationMerger` replaces or appends stable managed Markdown blocks.
- `AiIncrementalMetrics` records prompt, response, and full-specification character counts.
- No-change plans skip the provider completely.
- Patches for targets outside the update plan are rejected.

## Response Contract

```text
<<<DOCPILOT_PATCH id=TARGET_ID>>>
Markdown for the changed target only
<<<END_DOCPILOT_PATCH>>>
```

## Compatibility

The existing Provider SPI, Builder, Renderer, snapshot workflow, and RFC-0041 CLI contracts are unchanged. No breaking change is introduced.
