# RFC-0043 Main Planning Update

## Project Dashboard

```text
Current Phase
Phase 1 — MVP / POC

Completed RFC
RFC-0001 ~ RFC-0043

Current RFC
RFC-0043 — Documentation Diff and Review (Completed)

Next RFC
RFC-0044 — To be confirmed by Main Planning

Primary Validation Target
C:\WorkSpace\architecture-samples

Primary Goal
Source → Project Analysis → Knowledge Model → ProjectSpecification(DIR)
→ Documentation(Rendering) → Incremental Update validation on a real Android project
```

The `Phase 2 — Post-MVP Evolution` label in the earlier RFC-0042 planning update is superseded by the user-confirmed Phase 1 MVP / POC definition. The first goal and validation target are unchanged.

## RFC-0043 Purpose

RFC-0043 adds a deterministic human-review gate between AI incremental patch generation and managed-block merge. It prevents AI-generated patches from becoming approved documentation until every proposed target has an explicit accepted or rejected decision.

## Implementation Summary

- Read existing RFC-0042 managed Markdown blocks by stable target ID.
- Compare existing and proposed Markdown per target.
- Classify documentation changes as `CREATE`, `UPDATE`, or `NO_CHANGE`.
- Preserve the specification change kind and target/parent stable IDs.
- Collect deterministic Evidence references from previous and current DIR targets.
- Detect missing patch targets and mark the review proposal incomplete.
- Reject patches outside the authorized `IncrementalUpdatePlan`.
- Reject duplicate targets, duplicate decisions, unknown decisions, malformed blocks, and mismatched markers.
- Require a complete decision set before merge.
- Merge accepted changes only; rejected changes never reach the merger.
- Keep original documentation unchanged for incomplete or partially decided reviews.
- Render deterministic Markdown dry-run review reports.
- Add a reviewable AI incremental workflow that separates provider generation from human review state.

## Architecture Change

```text
Previous Documentation
        +
AiDocumentationPatch list
        +
IncrementalUpdatePlan
        +
Previous / Current ProjectSpecification
        │
        ▼
DefaultDocumentationDiffReviewer.propose
        │
        ▼
DocumentationReviewProposal
        │
        ▼
Human ACCEPTED / REJECTED decisions
        │
        ▼
DefaultDocumentationDiffReviewer.apply
        │
        ├── incomplete or pending → no merge
        └── complete → accepted patches only
                         │
                         ▼
              ManagedBlockAiDocumentationMerger
```

The existing RFC-0042 generator is unchanged. Its provisional in-memory merged value is not treated as approved output by `DefaultAiIncrementalDocumentationReviewWorkflow`.

## Added Main Classes

- `DocumentationReviewModels.kt`
  - `DocumentationReviewRequest`
  - `DocumentationChangeKind`
  - `DocumentationReviewEntry`
  - `DocumentationReviewProposal`
  - `DocumentationReviewDisposition`
  - `DocumentationReviewDecision`
  - `DocumentationReviewApplyStatus`
  - `DocumentationReviewResult`
- `ManagedDocumentationBlockReader.kt`
  - `ManagedDocumentationBlockReader`
  - `HtmlCommentManagedDocumentationBlockReader`
- `DocumentationDiffReviewer.kt`
  - `DocumentationDiffReviewer`
  - `DefaultDocumentationDiffReviewer`
- `DocumentationReviewReportRenderer.kt`
  - `DocumentationReviewReportRenderer`
  - `MarkdownDocumentationReviewReportRenderer`
- `AiIncrementalDocumentationReviewWorkflow.kt`
  - `AiIncrementalDocumentationReviewWorkflow`
  - `DefaultAiIncrementalDocumentationReviewWorkflow`
  - review preparation status/result models

## Added Tests

- `DocumentationDiffReviewerTest`
- `ManagedDocumentationBlockReaderTest`
- `DocumentationReviewReportRendererTest`
- `AiIncrementalDocumentationReviewWorkflowTest`

Coverage includes:

- create/update/no-change classification
- deterministic target ordering
- previous/current Evidence union
- incomplete proposal handling
- partial-decision fail-safe behavior
- accepted-only merge
- rejected patch isolation
- unknown and duplicate decision validation
- unauthorized patch validation
- malformed managed block validation
- deterministic dry-run report
- separation of provisional AI merge from approved output

## Public API Change

Additive public API only.

Existing public APIs changed: none.

Breaking change: none.

## Test Result

```text
RFC-0043 production subset compile
PASS

RFC-0043 tests
PASS — 12 passed, 0 failed

RFC-0042 AI incremental + specification incremental regression tests
PASS — 14 passed, 0 failed

Total locally executed tests
PASS — 26 passed, 0 failed

Full Gradle clean test
NOT RUN — supplied ZIP omitted gradle/wrapper/gradle-wrapper.jar and no compatible Gradle installation was available.
```

Recommended repository verification:

```powershell
.\gradlew.bat :test --tests "*DocumentationDiffReviewerTest"
.\gradlew.bat :test --tests "*ManagedDocumentationBlockReaderTest"
.\gradlew.bat :test --tests "*DocumentationReviewReportRendererTest"
.\gradlew.bat :test --tests "*AiIncrementalDocumentationReviewWorkflowTest"
.\gradlew.bat clean test
```

## ADR Candidates

1. **Complete-review-before-merge policy**  
   A documentation review must have no missing patches and an explicit decision for every proposal entry before any accepted patch is merged.

2. **Managed block as review boundary**  
   Stable RFC-0042 HTML comment blocks are the canonical unit for AI incremental diff and review.

3. **Provider execution and human authority separation**  
   AI generation output is a proposal. Only a separate review application stage can produce approved merged documentation.

## Technical Debt

- Define explicit deletion semantics for obsolete managed blocks.
- Add CLI/UI decision capture and review report file output.
- Persist review decisions and review proposal hashes for auditability.
- Add reviewer identity, timestamp, and optional signature policy.
- Add conflict detection when documentation changes between proposal creation and apply.
- Run full Gradle and provider-module regression tests in the repository environment.
- Validate RFC-0043 against `C:\WorkSpace\architecture-samples` after CLI integration.

## Next RFC Handoff

RFC-0044 should be selected by Main Planning without changing the established Phase 1 MVP / POC goal. The next work may consume `DocumentationReviewProposal` and `DocumentationReviewResult`, but should not bypass the complete-review-before-merge invariant.
