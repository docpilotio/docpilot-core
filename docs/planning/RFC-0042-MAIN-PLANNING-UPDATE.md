# RFC-0042 Main Planning Update

## Project Status

```text
Current Phase
Phase 2 — Post-MVP Evolution

Current Release
v0.5 MVP

Stable Release
release/v0.5.1

Completed RFC
RFC-0001 ~ RFC-0042

Current RFC
RFC-0043 — Documentation Diff & Review
```

## RFC-0042 Summary

RFC-0042 adds a provider-independent AI incremental generation layer over the specification incremental plan. Only changed package, type, API, and property targets are serialized into the prompt. The AI returns strict target-scoped Markdown patches. Stable managed blocks merge those patches into existing documentation without requesting or accepting a complete-document rewrite.

Token reduction is observable through prompt characters, response characters, full-specification characters, and the derived prompt reduction ratio. No-change execution skips the AI provider. Responses containing unchanged target IDs are rejected.

Existing public contracts for `AiProvider`, `SpecificationBuilder`, `SpecificationRenderer`, snapshot persistence, and the RFC-0041 CLI workflow were not changed. Breaking change: none.

## Architecture Update

```text
CLI / Application Integration Point
        │
        ▼
Specification Snapshot
        │
        ▼
Incremental Planner
        │
        ▼
AI Incremental Generator
        │
        ├── Changed-target Prompt Builder
        ├── Existing AiProvider SPI
        ├── Patch Response Codec
        └── Token Metrics
        │
        ▼
Managed-block Merge
        │
        ▼
Renderer / Writer Integration
```

## Implementation

### New classes

- `AiIncrementalGenerationRequest`
- `AiIncrementalGenerationResult`
- `AiIncrementalMetrics`
- `AiDocumentationPatch`
- `SpecificationIncrementalPromptBuilder`
- `DefaultSpecificationIncrementalPromptBuilder`
- `AiDocumentationPatchCodec`
- `MarkerAiDocumentationPatchCodec`
- `AiDocumentationMerger`
- `ManagedBlockAiDocumentationMerger`
- `AiIncrementalDocumentationGenerator`
- `DefaultAiIncrementalDocumentationGenerator`

### Modified classes

None.

### Deleted classes

None.

### Provider changes

None. Ollama and OpenAI remain behind the existing `AiProvider` contract.

### Merge changes

Stable HTML comment markers identify AI-owned target blocks. Existing matching blocks are replaced; new target blocks are appended under the AI incremental documentation heading.

### Public API

New additive public API only. Existing API unchanged.

## Test Result

```text
Compile
PASS — RFC-0042 main source subset compiled with kotlinc.

Incremental Tests
NOT RUN — Gradle wrapper JAR was absent from the uploaded source ZIP.

AI Tests
NOT RUN — Test source added; Gradle execution unavailable in the supplied archive.

Provider Tests
NOT RUN

Regression Tests
NOT RUN

Full Gradle Tests
NOT RUN — gradle/wrapper/gradle-wrapper.jar missing.
```

## ADR Candidates

- Adopt stable managed Markdown blocks as the merge boundary for AI-owned incremental content.
- Treat an incremental update plan as the authorization boundary for AI response targets.

## Technical Debt

- Integrate the new generator into the CLI specification command with explicit provider/model options.
- Persist AI-generated managed content separately or define its snapshot lifecycle.
- Replace character-based measurement with provider token usage when consistently available.
- Define removal semantics for obsolete AI-managed target blocks.
- Add retry and review workflow in later RFC scope.

## Next RFC

RFC-0043 can use the managed block boundaries and `AiDocumentationPatch` list as the Diff entry point. Review should compare existing and proposed target blocks before merge, expose accepted/rejected patch decisions, and keep provider execution separate from human review state.
