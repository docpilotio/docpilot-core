# RFC-0043 — Documentation Diff and Review

## Status

Implemented.

## Purpose

Insert a deterministic human-review boundary between RFC-0042 AI incremental patch generation and managed-block merge. Existing documentation and proposed target-scoped patches are compared before any approved output is produced. Only patches explicitly accepted through a complete review decision set are merged.

## Scope

RFC-0043 preserves the Phase 1 MVP / POC objective:

```text
Source
→ Project Analysis
→ Knowledge Model
→ ProjectSpecification (DIR)
→ Documentation Rendering
→ Incremental Update
```

The first validation target remains:

```text
C:\WorkSpace\architecture-samples
```

No long-term Software Knowledge Platform feature is implemented by this RFC.

## Architecture

```text
IncrementalUpdatePlan
        │
        ▼
AI Incremental Generator
        │
        ▼
Target-scoped AiDocumentationPatch list
        │
        ▼
AI Incremental Review Preparation
        │
        ├── Managed block reader
        ├── Existing/proposed Markdown comparison
        ├── Stable target and parent IDs
        ├── Specification change kind
        └── Evidence references
        │
        ▼
DocumentationReviewProposal
        │
        ▼
Human decisions for every proposed target
        ├── ACCEPTED
        └── REJECTED
        │
        ▼
Complete decision validation
        │
        ▼
Accepted patches only
        │
        ▼
ManagedBlockAiDocumentationMerger
```

## Decisions

- Review uses the RFC-0042 managed HTML comment blocks as the comparison boundary.
- Documentation changes are classified as `CREATE`, `UPDATE`, or `NO_CHANGE`.
- Specification change kind remains independently visible as `ADDED`, `REMOVED`, or `MODIFIED`.
- Evidence references are collected from both previous and current specification targets and sorted deterministically.
- Patches outside the incremental update plan are rejected.
- Missing patches make the proposal incomplete.
- Duplicate patches, duplicate update targets, unknown decisions, duplicate decisions, malformed managed blocks, and mismatched markers fail explicitly.
- Partial decisions never modify documentation. The original document is returned unchanged until every proposal entry has an explicit decision and no patch is missing.
- Rejected patches are never passed to the merger.
- Accepted `NO_CHANGE` entries do not trigger a rewrite.
- AI provider execution and human review state are separate. Review preparation never exposes the generator's provisional merged document as approved output.
- A deterministic Markdown report supports dry-run inspection before apply.

## Public API

New additive API:

- `DocumentationReviewRequest`
- `DocumentationChangeKind`
- `DocumentationReviewEntry`
- `DocumentationReviewProposal`
- `DocumentationReviewDisposition`
- `DocumentationReviewDecision`
- `DocumentationReviewApplyStatus`
- `DocumentationReviewResult`
- `ManagedDocumentationBlockReader`
- `HtmlCommentManagedDocumentationBlockReader`
- `DocumentationDiffReviewer`
- `DefaultDocumentationDiffReviewer`
- `DocumentationReviewReportRenderer`
- `MarkdownDocumentationReviewReportRenderer`
- `AiIncrementalDocumentationReviewWorkflow`
- `DefaultAiIncrementalDocumentationReviewWorkflow`
- `AiIncrementalReviewPreparationStatus`
- `AiIncrementalReviewPreparationResult`

Existing Builder, Renderer, Provider SPI, snapshot, incremental planner, AI incremental generator, and merger contracts are unchanged.

## Verification

- RFC-0043 production dependency subset compiled successfully.
- RFC-0043 tests: 12 passed, 0 failed.
- RFC-0042 AI incremental generation and specification incremental regression tests: 14 passed, 0 failed.
- Full Gradle execution was not available in the implementation environment because the supplied source archive did not include `gradle-wrapper.jar` and no compatible Gradle installation was present.

Recommended Windows verification:

```powershell
.\gradlew.bat :test --tests "*DocumentationDiffReviewerTest"
.\gradlew.bat :test --tests "*ManagedDocumentationBlockReaderTest"
.\gradlew.bat :test --tests "*DocumentationReviewReportRendererTest"
.\gradlew.bat :test --tests "*AiIncrementalDocumentationReviewWorkflowTest"
.\gradlew.bat clean test
```

## Compatibility

Breaking change: none.

Implementation style: additive.

## Deferred

- Target-block deletion semantics are not introduced. A removed specification target can be reviewed as a generated patch, but physical managed-block removal remains a later explicit policy decision.
- CLI commands for interactive decision capture are not introduced.
- Review decision persistence, identity, signatures, and audit storage remain future work.
