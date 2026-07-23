# RFC-0043 Main Planning Update

## Project Dashboard

```text
Current Version
DocPilot MCP v0.12.3

Current Status
Controlled Implementation Orchestration Windows real-environment stabilization complete

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

## DocPilot MCP v0.12.3 Completed Work

- Transport the multiline deterministic Codex Worker prompt through stdin instead of process arguments while retaining process-argument control-character validation.
- Resolve executables through Windows `PATH` and `PATHEXT`, preferring `npm.cmd` over extensionless `npm`.
- Execute `.cmd` and `.bat` files through a constrained `cmd.exe /d /c` wrapper.
- Persist Worker failure diagnostics: `workerExecution`, `exitCode`, `stdout`, `stderr`, `resultFileFound`, and `repositoryBefore`.
- Prohibit Worker-side `git add`, `git commit`, and HEAD changes in the Worker prompt.
- Capture structured results with Codex CLI `--output-last-message` and enforce an RFC/Work Order identity-bound schema with `--output-schema`.
- Remove stale Worker results before execution and clean up the runtime output schema afterward.
- Separate control-plane runtime results from implementation scope evidence.
- Make every CLI output-schema property required for Structured Outputs compatibility while retaining the runtime model's optional `git.commit` contract outside the CLI schema.
- Generate the Pending Handoff after successful verification and Alpha review.

## DocPilot MCP v0.12.3 Validation Evidence

```text
Focused Tests
PASS — 2 files / 2 tests

Typecheck
PASS

Build
PASS

Full Tests
PASS — 25 files / 183 tests

git diff --check
PASS

Actual Windows E2E
SUCCEEDED — Work Order RFC-9001-b3434741c2a1

Verification
PASSED

Alpha
PASSED_WITH_LIMITATIONS

Worker exit code
0

resultFileFound
true

Pending Handoff
created

Worker commit attempt
none

Git HEAD
unchanged — b3434741c2a1f969fd1ad48c4e4fb1e3fd510298

Orchestration lock released
true
```

The actual Windows E2E passed targeted, module, build, regression, smoke, and verification checks. It changed only `tools/docpilot-mcp/docs/e2e-orchestration-smoke.md` in the E2E worktree, created no commit, performed no push, left no staged files, and released the orchestration lock. Evidence is retained at `C:\WorkSpace\docpilot-core-orchestration-e2e.codex\evidence\v0.12.3-client-timeout-20260720-205348`.

## DocPilot MCP v0.12.3 Known Limitation

- Alpha is `PASSED_WITH_LIMITATIONS` because the `.docpilot` control-plane runtime artifact appears as untracked Repository Evidence.
- This is neither an implementation-scope violation nor a release blocker.
- Repository Evidence should later distinguish implementation diffs, control-plane runtime artifacts, verification caches, and user-authored repository changes structurally.

## Current Position

DocPilot MCP v0.12.3 implementation and actual Windows E2E stabilization are complete. Git commit, push, PR, merge, tag, and release are not complete. The existing Roadmap and RFC-0044 handoff remain unchanged.

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
