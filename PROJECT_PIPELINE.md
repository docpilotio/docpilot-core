# DocPilot Project Pipeline

## 1. Analyze a project

Command:

```powershell
./gradlew :run --args="analyze C:\WorkSpace\architecture-samples"
```

Pipeline:

| Stage | Input | Output | Responsibility | Not responsible for |
|---|---|---|---|---|
| Project Loader | Project path | Loaded project context | Resolve and validate the project root | Source semantics |
| Source Scanner | Loaded project | `SourceIndex` | Discover and index supported source evidence | Documentation prose |
| Knowledge Builder | `SourceIndex` | Knowledge graph/result | Build structured relationships and knowledge | Presentation |
| Specification Builder | Knowledge result | `ProjectSpecification` DIR 0.3 | Produce canonical specification entities | Rendering format |
| Markdown Renderer | `ProjectSpecification` | Markdown | Deterministic presentation | Reinterpreting scanner or graph data |
| Prompt Package | Analysis artifacts | Prompt inputs and evidence | Prepare bounded AI context | Owning canonical truth |
| Output Writer | Rendered artifacts | Files | Persist generated outputs | Domain interpretation |

Verified v0.5 sample outputs:

```text
docs/project-summary.md
docs/source-index.md
docs/knowledge-graph.json
prompt-package/overview.md
prompt-package/knowledge-graph.json
prompt-package/evidence.json
prompt-package/instructions.md
```

## 2. Generate an AI architecture document

Command:

```powershell
./gradlew :docpilot-cli:run --args="generate architecture --project C:\WorkSpace\architecture-samples --provider ollama --model qwen3:8b --output C:\WorkSpace\architecture-samples\docs\ai-architecture.md"
```

Pipeline:

```text
Analysis evidence
→ Prompt orchestration
→ AI Provider SPI
→ Ollama provider
→ qwen3:8b
→ Generated Markdown
→ Output Writer
```

v0.5 validation scope:

- Required runtime provider: Ollama
- Verified model: `qwen3:8b`
- OpenAI runtime invocation: out of scope
- Invalid-provider handling: verified

## 3. Incremental documentation

```text
Previous ProjectSpecification
+
Current ProjectSpecification
→ Stable-ID diff
→ Specification changes
→ Deterministic update plan
```

Nested API and Property changes propagate to owning Type and Package scopes. Ownership moves preserve both previous and current affected scopes.

## 4. AI incremental documentation review

```text
IncrementalUpdatePlan
+
AI target-scoped patches
+
Existing managed documentation blocks
→ deterministic documentation diff
→ DocumentationReviewProposal
→ complete human decisions
→ accepted patches only
→ managed-block merge
```

Safety rules:

- patches outside the update plan are rejected;
- missing patches keep the proposal incomplete;
- partial decisions do not modify documentation;
- rejected patches never reach the merger;
- accepted `NO_CHANGE` entries do not rewrite content;
- Evidence references and stable target IDs remain visible in the review report.

