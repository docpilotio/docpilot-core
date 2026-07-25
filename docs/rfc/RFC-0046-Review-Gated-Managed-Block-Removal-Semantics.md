# RFC-0046: Review-gated Managed Block Removal Semantics

## Status

Implemented, locally verified, and integrated into local main. Remote synchronization
is recorded separately from the implementation contract.

Verification evidence:

- focused RFC-0046 and review regression tests: PASS;
- `clean build`: PASS;
- `clean test`: PASS;
- 87 test XML files, 265 tests, 0 failures, 0 errors, 0 skipped;
- isolated architecture-samples CLI smoke: PASS;
- DIR schema 0.3 and specification snapshot format 1 unchanged.

## 1. Purpose

RFC-0043 established complete human review before managed-block merge. RFC-0045
made removed specification relationships first-class incremental changes and
review targets. The current patch contract can only create or replace a managed
block. It cannot explicitly remove one.

As a result, DocPilot can detect and review that a canonical DIR target was
removed while leaving the corresponding generated documentation block behind.

RFC-0046 completes the managed-block lifecycle:

```text
Stable target removed from DIR
        ->
Incremental REMOVED action
        ->
Explicit REMOVE patch
        ->
Review proposal bound to exact documentation base
        ->
Complete ACCEPTED / REJECTED decision set
        ->
Fail-closed conflict and marker validation
        ->
Atomic accepted-only managed-block update
```

Removal is never inferred from blank Markdown, target absence, model prose, or a
missing patch. It is represented by an explicit operation and applied only after
complete user approval.

## 2. Product outcome

When a specification target is removed, a developer can:

- see an explicit proposal to remove its DocPilot managed block;
- inspect the existing block and supporting prior Evidence;
- accept or reject the removal independently from other changes;
- rely on no documentation mutation while any target is pending or missing;
- rely on apply failing if the documentation changed since proposal preparation;
- and receive one deterministic result for the complete accepted operation set.

Accepted removals eliminate stale generated content. Rejected removals preserve
the exact existing block.

## 3. Scope

RFC-0046 includes:

1. An explicit managed-block patch operation: `UPSERT` or `REMOVE`.
2. A dedicated AI response marker for `REMOVE`.
3. A review change kind representing managed-block removal.
4. Complete user decisions for removal entries under RFC-0043.
5. Fail-closed validation of target authorization, operation/change consistency,
   managed-block structure, reviewed base, and decision completeness.
6. Atomic application of all accepted UPSERT and REMOVE operations.
7. An in-process reviewed-base SHA-256 conflict check.
8. Deterministic patch, proposal, report, and merge behavior.

## 4. Non-goals

RFC-0046 does not:

- delete documentation files or other artifacts;
- use `DocumentationArtifactOperation.DELETE` for managed-block removal;
- remove handwritten Markdown outside a DocPilot managed block;
- remove a managed block automatically because a DIR target is absent;
- infer removal from blank Markdown or an omitted patch;
- persist proposals, decisions, reviewer identity, timestamps, or signatures;
- provide restart/resume review;
- add CLI, TUI, GUI, or web review interaction;
- change MCP source, MCP tests, MCP state, or make Core depend on MCP;
- add new relationship extraction;
- change DIR schema 0.3;
- change specification snapshot format 1;
- add remote review or approval services;
- publish, tag, push, or release DocPilot.

## 5. Baseline and prerequisites

Implementation baseline:

- local main: `28715ef60d732812ccb0fdf3a6ea14c2cef7b2dc`;
- RFC-0045 feature commit: `92d27077cc20c5b2c7703fba967420d0ce186615`;
- RFC-0045 local merge: `df3e0514d696c98861bdb0ec39e54878c1607948`;
- DIR schema: `0.3`;
- specification snapshot format: `1`;
- verification baseline: 86 XML files, 258 tests, 0 failures, 0 errors, 0 skipped.

Required existing capabilities:

- stable target IDs;
- `ChangeKind.REMOVED`;
- `IncrementalUpdateTarget.RELATIONSHIP`;
- target-scoped incremental plans;
- managed HTML comment block markers;
- RFC-0043 complete-review-before-merge;
- prior/current Evidence union;
- deterministic review reporting.

`origin/main` is behind local main at specification time. Remote synchronization
is an external Git decision and not part of this RFC.

## 6. Current architecture gap

### 6.1 Patch model

`AiDocumentationPatch` contains only:

```kotlin
targetId: String
markdown: String
```

It represents replacement content but no operation.

### 6.2 Patch codec

`MarkerAiDocumentationPatchCodec` accepts only:

```text
<<<DOCPILOT_PATCH id=TARGET_ID>>>
Markdown
<<<END_DOCPILOT_PATCH>>>
```

Blank Markdown is rejected. There is no unambiguous removal response.

### 6.3 Review

`DocumentationChangeKind` contains `CREATE`, `UPDATE`, and `NO_CHANGE`. A removed
specification target can still receive an UPDATE patch, but cannot request physical
managed-block removal.

### 6.4 Merger

`ManagedBlockAiDocumentationMerger` replaces an existing block or appends a new
one. It cannot remove a block and does not bind apply to the documentation reviewed
when the proposal was created.

## 7. Patch operation contract

### 7.1 Operation

Add:

```kotlin
public enum class AiDocumentationPatchOperation {
    UPSERT,
    REMOVE,
}
```

Extend the existing model additively:

```kotlin
public data class AiDocumentationPatch(
    public val targetId: String,
    public val markdown: String,
    public val operation: AiDocumentationPatchOperation =
        AiDocumentationPatchOperation.UPSERT,
)
```

The default preserves existing two-argument Kotlin construction.

### 7.2 Invariants

For every patch:

- `targetId` is non-blank;
- `UPSERT` requires non-blank `markdown`;
- `REMOVE` requires `markdown` to be empty;
- target IDs are unique across all operations in one response;
- a target cannot have both UPSERT and REMOVE operations;
- operation ordering is by target ID, with operation as a defensive tie-breaker.

The empty REMOVE payload is legal only because the explicit operation carries the
meaning. Empty or whitespace-only UPSERT content remains invalid. Callers must not
construct REMOVE by passing blank text without `operation = REMOVE`.

### 7.3 AI response format

UPSERT remains:

```text
<<<DOCPILOT_PATCH id=TARGET_ID>>>
Markdown for that target only
<<<END_DOCPILOT_PATCH>>>
```

REMOVE is:

```text
<<<DOCPILOT_REMOVE id=TARGET_ID>>>
```

There is no REMOVE body or closing marker.

The codec must reject:

- blank IDs;
- duplicate target IDs across PATCH and REMOVE markers;
- a REMOVE marker with trailing payload intended as content;
- malformed or nested markers;
- unknown DocPilot operation markers;
- a response containing no recognized operation.

Decoded operations are returned in deterministic target-ID order.

## 8. Removal authorization policy

An explicit REMOVE patch is authorized only when all conditions hold:

1. the target exists exactly once in `IncrementalUpdatePlan.actions`;
2. the action has `changeKind == ChangeKind.REMOVED`;
3. the target ID does not exist in the current specification for that action target;
4. the target ID existed in the previous specification;
5. the existing documentation contains exactly one valid managed block for the target.

An UPSERT remains valid for ADDED, MODIFIED, or REMOVED actions under existing
RFC-0045 behavior. This allows a removed specification target to be documented as
deprecated/removed instead of physically deleted when the generated operation is
UPSERT and the user accepts it.

A REMOVE patch for ADDED or MODIFIED fails. An absent patch remains a missing patch
and makes the proposal incomplete; absence never means removal.

## 9. Prompt contract

For every action, the prompt continues to require one explicit operation per
target. It documents both valid response forms.

For a `ChangeKind.REMOVED` action:

- BEFORE target context is included;
- prior Evidence remains visible through review;
- the prompt may request `DOCPILOT_REMOVE` when the correct documentation outcome
  is removal of the target's managed block;
- the provider may instead return an UPSERT patch documenting removal;
- the provider must not omit the target.

For ADDED or MODIFIED actions, only UPSERT is authorized.

The provider never approves removal. It proposes an operation. Human review is
the only approval boundary.

## 10. Reviewed-base conflict contract

### 10.1 Proposal fingerprint

`DocumentationReviewProposal` gains:

```kotlin
public val reviewedDocumentationSha256: String
```

The fingerprint is:

```text
lowercase hex SHA-256 of the exact UTF-8 bytes of existingDocumentation
```

No trimming, line-ending normalization, Unicode normalization, or managed-block-
only projection is applied. Any byte-level document change is a conflict.

The value must match `^[0-9a-f]{64}$`.

### 10.2 Preparation

`propose`:

1. validates the complete existing managed-block structure;
2. computes the fingerprint from the exact request document;
3. builds entries and missing target lists;
4. returns the fingerprint in the proposal.

### 10.3 Apply

Before deriving accepted operations or calling the merger, `apply` computes the
fingerprint of its `existingDocumentation` argument and compares it with the
proposal fingerprint.

Mismatch fails explicitly with a reviewed-base conflict. It does not:

- rebase;
- regenerate the proposal;
- apply only non-conflicting targets;
- normalize the document;
- or return partially merged output.

This is an in-process safety check only. RFC-0046 does not serialize or persist the
proposal.

## 11. Review model

### 11.1 Documentation change kind

Add:

```kotlin
DocumentationChangeKind.REMOVE
```

### 11.2 Review entry

For a REMOVE entry:

- `existingMarkdown` is required and non-null;
- `proposedMarkdown` is the empty string;
- patch operation is explicitly `REMOVE`;
- `specificationChangeKind` is `REMOVED`;
- prior Evidence is retained;
- the report renders a removal statement rather than presenting blank proposed Markdown.

The entry must retain or expose the explicit patch operation. The implementation
may add `operation` to `DocumentationReviewEntry`; it must not infer REMOVE later
from `proposedMarkdown.isBlank()`.

Entry consistency:

| Operation | Documentation kind | Existing block | Proposed Markdown |
| --- | --- | --- | --- |
| UPSERT | CREATE | absent | non-blank |
| UPSERT | UPDATE | present | non-blank |
| UPSERT | NO_CHANGE | present | non-blank |
| REMOVE | REMOVE | present | empty |

Every other combination fails.

### 11.3 Decisions

Existing dispositions remain:

- `ACCEPTED`
- `REJECTED`

No automatic or implicit decision is introduced.

- ACCEPTED REMOVE authorizes removal at apply time.
- REJECTED REMOVE preserves the exact managed block.
- missing REMOVE decision keeps the entire proposal pending.

## 12. Complete approval and atomic apply

RFC-0043 complete-review-before-merge remains mandatory.

Apply order:

1. validate proposal structural invariants;
2. validate unique and known decisions;
3. compare the exact reviewed-base fingerprint;
4. determine pending, accepted, and rejected targets;
5. if any decision or patch is missing, return `PENDING_REVIEW` with the exact
   original documentation;
6. validate every accepted operation against the current managed-block structure;
7. calculate the full merged result in memory;
8. return one `APPLIED` result.

No accepted operation is applied if any accepted operation is invalid.

Atomicity in this RFC means:

- the merger is a pure in-memory transformation;
- it returns one complete string or throws before returning;
- no artifact writer is called from the reviewer or merger;
- pending/incomplete review returns the original string byte-for-byte;
- failure exposes no partially transformed document as a successful result.

Artifact-level file writes remain outside this RFC and retain their existing
execution boundary.

## 13. Managed-block removal algorithm

The merger receives a mixed list of accepted UPSERT and REMOVE operations.

Before transformation:

- parse and validate all managed blocks once;
- reject malformed, mismatched, nested, or duplicate target markers;
- ensure every REMOVE target exists exactly once;
- ensure target IDs are unique across operations;
- validate all operation payloads.

Transformation:

- process operations in deterministic target-ID order;
- UPSERT replaces an existing block or appends one under the existing AI heading;
- REMOVE deletes the complete start marker, body, and end marker for that target;
- cleanup removes only separator whitespace introduced by the removed block;
- handwritten content and unrelated managed blocks remain byte-equivalent except
  for deterministic separator normalization at the removal boundary;
- if the last managed block is removed, the AI heading is removed only when it is
  the exact DocPilot-owned heading and its section contains no other content.

The implementation must define the heading cleanup as a bounded parser/validated
range operation. It must not use a broad regex capable of consuming neighboring
handwritten sections.

Output retains the existing canonical trailing newline behavior.

## 14. Fail-closed behavior

The operation fails explicitly for:

- malformed, mismatched, nested, or duplicate managed-block markers;
- blank or duplicate operation target IDs;
- conflicting UPSERT and REMOVE for one target;
- REMOVE with non-empty Markdown;
- UPSERT with blank Markdown;
- REMOVE outside the incremental plan;
- REMOVE for a non-REMOVED action;
- REMOVE when the previous target did not exist;
- REMOVE when the current target still exists;
- REMOVE when the managed block is missing;
- incomplete proposal or decisions being represented as applied;
- duplicate or unknown decisions;
- reviewed-base fingerprint mismatch;
- invalid proposal fingerprint;
- any unhandled operation or documentation change enum value.

No failure is downgraded to NO_CHANGE, UPDATE, acceptance, or partial success.

## 15. Determinism

Equivalent inputs must produce identical:

- decoded patch operations;
- proposal entry order;
- reviewed-base fingerprint;
- missing target order;
- accepted/rejected/pending target order;
- Markdown review report;
- merged documentation.

Ordering uses existing review target/parent/ID order for entries and lexical target
ID order for merge operations. Input list order must not affect output.

SHA-256 uses UTF-8 and lowercase hexadecimal output independent of platform locale.

## 16. Review report

The deterministic Markdown report adds:

- patch operation: UPSERT or REMOVE;
- reviewed base SHA-256;
- documentation change: REMOVE where applicable;
- explicit proposed outcome: `Managed block will be removed`;
- existing Markdown for removal review;
- decision and prior Evidence.

The report must never render empty proposed Markdown as if it were an ordinary
content patch.

## 17. Public API and compatibility

Expected additive changes:

- `AiDocumentationPatchOperation`
- defaulted `AiDocumentationPatch.operation`
- `DocumentationChangeKind.REMOVE`
- explicit operation on `DocumentationReviewEntry`
- `DocumentationReviewProposal.reviewedDocumentationSha256`
- mixed-operation support in the managed-block merger

Compatibility notes:

- existing two-argument `AiDocumentationPatch` construction remains source-compatible;
- adding enum values requires all in-repository exhaustive `when` expressions to
  add explicit branches;
- proposal construction changes may require a default only if it can preserve the
  fingerprint invariant; production code must never use a synthetic fingerprint;
- Provider SPI and model selection contracts remain unchanged;
- no DIR or snapshot serialization change occurs.

The implementation must update all in-repository consumers and tests explicitly.

## 18. Architecture boundaries

Core owns:

- patch operation semantics;
- codec validation;
- proposal fingerprint calculation;
- complete review and conflict validation;
- deterministic managed-block transformation.

Adapters may later own:

- persistence;
- user interaction;
- artifact writing;
- remote coordination.

MCP owns none of the RFC-0046 runtime contract. Core must not import MCP code,
state, schemas, or lifecycle concepts.

## 19. Expected implementation areas

Production candidates:

```text
src/main/kotlin/io/docpilot/core/incremental/specification/ai/
  AiIncrementalModels.kt
  AiDocumentationPatchCodec.kt
  AiDocumentationMerger.kt
  SpecificationIncrementalPromptBuilder.kt

src/main/kotlin/io/docpilot/core/incremental/specification/review/
  DocumentationReviewModels.kt
  DocumentationDiffReviewer.kt
  DocumentationReviewReportRenderer.kt
  ManagedDocumentationBlockReader.kt
```

Test candidates:

```text
src/test/kotlin/io/docpilot/core/incremental/specification/ai/
  AiIncrementalDocumentationGeneratorTest.kt
  managed-block merger and codec coverage

src/test/kotlin/io/docpilot/core/incremental/specification/review/
  DocumentationDiffReviewerTest.kt
  ManagedDocumentationBlockReaderTest.kt
  DocumentationReviewReportRendererTest.kt
  AiIncrementalDocumentationReviewWorkflowTest.kt

src/test/kotlin/io/docpilot/core/incremental/specification/
  RelationshipIncrementalDocumentationTest.kt
```

This list is a design boundary, not authorization to modify every file.

## 20. Verification plan

### 20.1 Codec and model

- existing UPSERT marker remains compatible;
- explicit REMOVE marker decodes;
- mixed operations sort deterministically;
- duplicate IDs across operation kinds fail;
- empty UPSERT and payload-bearing REMOVE fail;
- malformed and unknown markers fail.

### 20.2 Authorization

- REMOVED action plus previous target plus existing block permits REMOVE proposal;
- ADDED and MODIFIED actions reject REMOVE;
- current target presence rejects REMOVE;
- missing previous target rejects REMOVE;
- patch outside plan rejects;
- omitted operation remains a missing patch.

### 20.3 Review

- REMOVE entry contains existing Markdown and prior Evidence;
- report renders explicit removal outcome;
- accepted REMOVE applies only with complete decisions;
- rejected REMOVE preserves its block;
- pending or missing target preserves the complete original document;
- mixed accepted/rejected UPSERT and REMOVE produces the exact expected result.

### 20.4 Conflict safety

- unchanged exact document applies;
- handwritten change conflicts;
- unrelated managed-block change conflicts;
- line-ending-only change conflicts;
- whitespace-only change conflicts;
- tampered or malformed fingerprint fails;
- conflict never returns partial output.

### 20.5 Merger

- remove first, middle, and last block;
- remove the only block and safely clean an empty owned heading;
- preserve non-empty heading section content;
- preserve neighboring handwritten sections;
- missing, duplicate, mismatched, and nested markers fail;
- mixed UPSERT/REMOVE is deterministic under shuffled input;
- all operations validate before any result is returned.

### 20.6 Regression and compatibility

- RFC-0042 AI incremental generation;
- RFC-0043 complete review;
- RFC-0044 relationship semantics;
- RFC-0045 relationship incremental diff and review;
- snapshot round-trip;
- full `clean build`;
- full `clean test`;
- isolated architecture-samples CLI smoke;
- `git diff --check`;
- allowed/protected path review.

## 21. Completion criteria

RFC-0046 implementation is complete only when:

1. removal is represented by an explicit operation, never blank-content inference;
2. only a valid REMOVED plan target with an existing managed block can propose REMOVE;
3. every removal requires an explicit user decision in a complete decision set;
4. rejected, pending, missing, conflicted, or invalid removal leaves the full
   document unchanged;
5. apply rejects any exact reviewed-base fingerprint mismatch;
6. accepted mixed operations apply as one fail-closed in-memory transformation;
7. no file/artifact deletion path is introduced;
8. DIR 0.3, snapshot format 1, Provider SPI, and RFC-0043 invariants remain intact;
9. targeted, regression, full build/test, and isolated CLI smoke pass;
10. Canonical RFC, Planning, Handoff, and Roadmap match implementation evidence.

## 22. Known risks

- Broad marker removal could consume handwritten content.
- Heading cleanup could remove a user-owned heading with identical text.
- Defaulted API fields can hide invalid synthetic values if invariants are weak.
- Exact-document fingerprints intentionally reject benign formatting changes.
- AI output may propose REMOVE incorrectly; authorization and human approval must
  remain independent gates.
- Mixed operations can appear atomic in memory while a later artifact writer is
  not transactional; file-writing atomicity is outside this RFC.

Each risk must be addressed by an explicit invariant, test, or documented
limitation.

## 23. Deferred follow-up

Not approved by RFC-0046:

- durable review proposal and decision persistence;
- reviewer identity, timestamps, and signatures;
- resumable CLI review and apply;
- artifact/file deletion;
- remote collaboration;
- additional semantic relationship extraction;
- MCP workflow expansion;
- release provenance automation.

## 24. Decision record

Approved candidate:

```text
CANDIDATE-001
Review-gated Managed Block Removal Semantics
```

Approved RFC-0046 scope:

- explicit managed-block REMOVE;
- complete user approval;
- fail-closed and atomic application;
- in-process reviewed-base conflict check.

Approved non-goals:

- file deletion;
- persistent review;
- CLI/UI;
- MCP changes.

This decision approves the detailed specification. It does not by itself approve
implementation, commit, main merge, push, tag, or release.
