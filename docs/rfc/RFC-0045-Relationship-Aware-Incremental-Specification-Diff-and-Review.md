# RFC-0045: Relationship-aware Incremental Specification Diff and Review

## Status

Implemented and locally verified in the RFC-0045 Feature Worktree. Git integration is pending.

## 1. Purpose

RFC-0044 made DIR relationships deterministic, validated, evidence-backed, and renderable. The specification incremental pipeline still compares only packages, types, APIs, and properties. A relationship-only change can therefore appear in a full render while producing no first-class `SpecificationChange`, no `IncrementalUpdateAction`, and no target-scoped AI review entry.

RFC-0045 closes that gap by carrying relationship changes through the existing stable-ID incremental and complete-review-before-merge pipeline.

```text
Previous / Current ProjectSpecification
        ->
Relationship stable-ID diff
        ->
SpecificationChange<RelationshipSpecification>
        ->
IncrementalUpdateTarget.RELATIONSHIP
        ->
Affected Type / Package scopes
        ->
Target-scoped AI patch
        ->
RFC-0043 complete human review
        ->
Accepted managed-block update
```

This RFC extends existing boundaries. It does not create a second diff engine, relationship model, renderer, review workflow, or provider path.

## 2. Product outcome

When a deterministic DIR relationship is added, removed, or its reviewable content changes, an Android developer must be able to determine:

- which relationship changed;
- whether it was added, removed, or modified;
- which internal source and target scopes are affected;
- which Evidence supports the previous and current relationship;
- which documentation target requires review;
- and whether the proposed documentation change was accepted or rejected.

Relationship-only changes must no longer disappear from incremental planning.

## 3. Goals

1. Detect relationship additions, removals, and modifications by stable `RelationshipSpecification.id`.
2. Represent those changes in `SpecificationDiff`.
3. Produce deterministic `RELATIONSHIP` update actions.
4. Propagate previous and current internal endpoint ownership into affected Type and Package scopes.
5. Render bounded BEFORE/AFTER relationship context for AI patch generation.
6. Union previous and current relationship Evidence in deterministic review entries.
7. Preserve RFC-0042 target authorization and RFC-0043 complete-review-before-merge.
8. Preserve DIR schema 0.3 and specification snapshot format 1.
9. Keep Core independent from MCP and every AI provider implementation.

## 4. Non-goals

RFC-0045 does not:

- extract new `EXTENDS`, `IMPLEMENTS`, `CALLS`, `IMPORTS`, or other semantic relationships;
- change RFC-0044 INTERNAL, EXTERNAL, or UNRESOLVED endpoint rules;
- change relationship identity construction;
- infer relationship meaning with AI;
- introduce transitive dependency or call-graph analysis;
- add a new renderer or selective renderer contract;
- add physical managed-block deletion semantics;
- persist review proposals, decisions, reviewer identity, timestamps, or signatures;
- add interactive CLI/UI review capture;
- change MCP source, MCP state, or make Core depend on MCP;
- add automatic approval or bypass complete review;
- combine Snapshot Incremental with Specification Incremental;
- publish, tag, or release DocPilot.

## 5. Baseline and prerequisites

Baseline:

- local main commit `92cffc2e16a451b04944733314820ddeff320d1e`;
- RFC-0044 feature commit `6e63dffce1df8cdf1d472741325e19e05794a3aa`;
- DIR schema `0.3`;
- specification snapshot format `1`;
- baseline: 85 test XML files and 254 passing tests;
- RFC-0045 verification: 86 test XML files and 258 passing tests.

Prerequisites satisfied:

- relationship endpoints are canonical DIR IDs or explicit EXTERNAL/UNRESOLVED IDs;
- relationship IDs are deterministic;
- relationship Evidence references are validated;
- structural self-relationships are removed;
- direct `DEPENDS_ON` projection is validated;
- renderer endpoint-kind output exists;
- RFC-0043 complete-review-before-merge exists.

`origin/main` synchronization is an external Git decision and must be fixed before implementation baseline selection if the implementation workflow requires the remote branch.

## 6. Current architecture gap

The following public incremental contracts currently omit relationships:

- `SpecificationDiff`
- `IncrementalUpdateTarget`
- `DefaultSpecificationDiffer`
- `DefaultIncrementalSpecificationPlanner`
- `DefaultSpecificationIncrementalPromptBuilder`
- `DefaultDocumentationDiffReviewer` Evidence lookup

The existing executor, generator, patch codec, managed-block merger, review decision model, and snapshot coordinator are reusable once a relationship action exists.

## 7. Diff contract

### 7.1 Model

`SpecificationDiff` gains:

```kotlin
public val relationshipChanges:
    List<SpecificationChange<RelationshipSpecification>> = emptyList()
```

`hasChanges` includes `relationshipChanges`.

No separate `RelationshipChange` data class is required. The canonical representation is the existing generic:

```kotlin
SpecificationChange<RelationshipSpecification>
```

The term “RelationshipChange” in planning or handoff material refers to that specialization unless implementation evidence demonstrates a need for a named type alias.

### 7.2 Stable identity

Relationship identity is `RelationshipSpecification.id`.

- Blank IDs are rejected.
- Duplicate IDs in either previous or current relationships are rejected.
- Matching IDs represent the same logical relationship.
- An ID present only in current is `ADDED`.
- An ID present only in previous is `REMOVED`.
- Matching IDs with unequal `RelationshipSpecification` values are `MODIFIED`.

RFC-0044 relationship IDs include relationship type and normalized endpoints. A type or endpoint change therefore normally appears as one `REMOVED` and one `ADDED` relationship, not a `MODIFIED` relationship. Description or Evidence changes can be `MODIFIED` while identity remains stable.

### 7.3 Parent ID

The relationship change `parentId` is:

- current `sourceId` for `ADDED`;
- previous `sourceId` for `REMOVED`;
- current `sourceId` for `MODIFIED`.

The parent is metadata for ordering, reporting, and review context. It does not claim that every relationship source is a Component; RFC-0044 permits any valid INTERNAL DIR entity as source.

### 7.4 Deterministic ordering

`relationshipChanges` are ordered by:

1. relationship stable ID;
2. `ChangeKind` only as a defensive tie-breaker.

Duplicate stable IDs fail before ordering. Input collection order must not affect output.

## 8. Incremental update plan

### 8.1 Target

`IncrementalUpdateTarget` gains:

```kotlin
RELATIONSHIP
```

One relationship change produces one `IncrementalUpdateAction`:

```text
target     = RELATIONSHIP
id         = relationship stable ID
parentId   = change parentId
changeKind = ADDED | REMOVED | MODIFIED
```

The action participates in the existing deterministic action order. The implementation must update every exhaustive `when` over `IncrementalUpdateTarget`.

### 8.2 Affected-scope policy

For each relationship change, the planner examines both endpoints from every available version:

- `ADDED`: current source and target;
- `REMOVED`: previous source and target;
- `MODIFIED`: previous and current source and target.

Endpoint-to-Type mapping:

- Component ID -> that Component;
- API ID -> its owning Component;
- Property ID -> its owning Component;
- Module ID -> no affected Type;
- Package ID -> no affected Type;
- EXTERNAL endpoint -> no affected Type;
- UNRESOLVED endpoint -> no affected Type.

Endpoint-to-Package mapping:

- Package ID -> that Package;
- Component ID -> the Component's `packageId`, or existing module fallback when the current planner already uses it;
- API or Property ID -> owning Component -> owning Package/module fallback;
- Module ID -> no inferred Package;
- EXTERNAL or UNRESOLVED endpoint -> no affected Package.

The planner unions previous and current endpoint scopes. This preserves both sides when ownership or endpoint context changes. All `changedTypeIds` and `changedPackageIds` remain unique and lexically sorted.

No scope is inferred from endpoint text, qualified name, relationship description, or AI output.

### 8.3 Relationship-only behavior

If relationships are the only changed DIR entities:

- `SpecificationDiff.hasChanges` is true;
- the plan contains at least one `RELATIONSHIP` action;
- `requiresUpdate` is true;
- affected Type/Package lists follow the endpoint policy;
- the executor must not return `NO_CHANGES`.

## 9. AI incremental generation

The existing Provider SPI and response markers remain unchanged.

For a `RELATIONSHIP` action, prompt context uses the relationship from the matching specification version:

```text
id
type
sourceId
targetId
description
sorted evidenceRefs
source endpoint kind
target endpoint kind
```

Rules:

- `ADDED`: AFTER context only.
- `REMOVED`: BEFORE context plus `removed=true`; no AFTER relationship is fabricated.
- `MODIFIED`: BEFORE and AFTER context.
- Context is target-scoped; the complete relationship catalog is not included.
- The prompt must not request new semantic extraction.
- AI patches outside the authorized plan remain rejected.
- No-change plans continue to skip the provider.

The patch target ID and managed-block identity are the relationship stable ID.

## 10. Documentation diff and review

### 10.1 Evidence

For a `RELATIONSHIP` action, review Evidence is the sorted unique union of:

- previous relationship `evidenceRefs`, when present;
- current relationship `evidenceRefs`, when present.

This gives removed relationships previous Evidence and added relationships current Evidence without inventing references.

### 10.2 Review invariant

RFC-0043 behavior is unchanged:

- unauthorized or duplicate patches fail;
- missing relationship patches make the proposal incomplete;
- partial decisions do not modify documentation;
- every entry requires ACCEPTED or REJECTED;
- rejected relationship patches never reach the merger;
- accepted `NO_CHANGE` entries do not rewrite content;
- only a complete decision set may apply accepted patches.

Relationship entries participate in the existing deterministic review order by target enum order, parent ID, and target ID.

### 10.3 Removed relationships

RFC-0045 detects and reviews removed relationships but does not introduce physical managed-block deletion. A removed relationship patch may document that removal or replace existing relationship content, subject to complete review. Deleting the managed block remains a separate explicit policy decision.

The implementation must not silently delete a block or omit a required patch merely because the DIR relationship was removed.

## 11. Renderer and execution boundaries

`ProjectSpecificationMarkdownRenderer` remains a full, presentation-only DIR renderer.

RFC-0045 does not require:

- a relationship-only renderer;
- changing `SpecificationRenderer`;
- changing `DocumentationArtifactWriter`;
- changing artifact operation types;
- interpreting `SpecificationDiff` inside the renderer.

The existing incremental executor can continue full artifact reconciliation when the plan requires an update. RFC-0045 improves change detection and target-scoped AI/review behavior, not physical section-level file writes.

## 12. Snapshot and persistence compatibility

- DIR schema remains `0.3`.
- Specification snapshot format remains `1`.
- `RelationshipSpecification` serialization shape remains unchanged.
- Existing valid RFC-0044 snapshots remain readable.
- `SpecificationDiff`, update plans, prompts, and review proposals remain runtime/transient unless another approved RFC introduces persistence.
- Snapshot integrity calculation must not change because of RFC-0045.

Snapshot regression tests must prove relationship-containing snapshots retain deterministic round-trip behavior.

## 13. Public API and compatibility

Expected additive API changes:

- `SpecificationDiff.relationshipChanges`
- `IncrementalUpdateTarget.RELATIONSHIP`

Existing model removed or renamed: none.

Compatibility notes:

- Adding a defaulted `SpecificationDiff` property preserves normal Kotlin source construction patterns but changes the data-class constructor/copy surface.
- Adding an enum constant requires downstream exhaustive `when` expressions to add a `RELATIONSHIP` branch.
- The project has no stable public release; nevertheless, all in-repository consumers and tests must be updated explicitly.
- `RelationshipSpecification`, `ProjectSpecification`, Provider SPI, renderer SPI, snapshot repository, and review decision shapes remain unchanged.

## 14. Error handling

The implementation fails explicitly for:

- blank relationship stable IDs;
- duplicate previous or current relationship IDs;
- a relationship action whose required BEFORE/AFTER value is unavailable contrary to `ChangeKind`;
- duplicate update target IDs across the complete plan;
- unauthorized relationship patches;
- missing relationship patches at review;
- unknown or duplicate relationship decisions.

It must not:

- downgrade malformed relationship input to no-change;
- select an arbitrary endpoint scope;
- synthesize Evidence;
- silently skip an unhandled `RELATIONSHIP` enum branch.

## 15. Determinism

The following must be independent of source collection order:

- relationship diff;
- relationship action order;
- affected Type and Package IDs;
- BEFORE/AFTER prompt text;
- Evidence union;
- review entry/report order;
- accepted/rejected/pending/missing target lists.

Equivalent previous/current DIR inputs must produce byte-identical prompt and review report output.

## 16. Expected implementation areas

Production candidates:

```text
src/main/kotlin/io/docpilot/core/incremental/specification/
  SpecificationDiff.kt
  IncrementalUpdatePlan.kt
  DefaultSpecificationDiffer.kt
  DefaultIncrementalSpecificationPlanner.kt

src/main/kotlin/io/docpilot/core/incremental/specification/ai/
  SpecificationIncrementalPromptBuilder.kt

src/main/kotlin/io/docpilot/core/incremental/specification/review/
  DocumentationDiffReviewer.kt
  related deterministic report/model code only if required
```

Test candidates:

```text
src/test/kotlin/io/docpilot/core/incremental/specification/
  DefaultSpecificationDifferTest.kt
  IncrementalDocumentationEngineTest.kt

src/test/kotlin/io/docpilot/core/incremental/specification/ai/
  AiIncrementalDocumentationGeneratorTest.kt
  prompt-builder focused coverage

src/test/kotlin/io/docpilot/core/incremental/specification/review/
  DocumentationDiffReviewerTest.kt
  DocumentationReviewReportRendererTest.kt
  AiIncrementalDocumentationReviewWorkflowTest.kt

src/test/kotlin/io/docpilot/core/incremental/specification/snapshot/
  JsonSpecificationSnapshotCodecTest.kt
```

This list is a design boundary, not authorization to change every listed file.

## 17. Verification plan

### 17.1 Differ

- relationship added;
- relationship removed;
- description/Evidence modified with stable ID;
- endpoint/type identity change becomes remove plus add;
- unchanged relationship;
- duplicate and blank ID rejection;
- deterministic ordering under shuffled input.

### 17.2 Planner

- one `RELATIONSHIP` action per relationship change;
- relationship-only plan requires update;
- Component endpoint propagation;
- API/Property owner propagation;
- direct Package endpoint propagation;
- Module/EXTERNAL/UNRESOLVED endpoint non-inference;
- previous/current union for removed and modified changes;
- deterministic action, Type, and Package ordering.

### 17.3 AI prompt and authorization

- target-scoped relationship fields only;
- BEFORE/AFTER rules for all change kinds;
- removed relationship marker;
- sorted Evidence;
- no full relationship catalog;
- unauthorized relationship patch rejection;
- provider skip on no changes.

### 17.4 Review

- previous/current Evidence union;
- missing patch keeps proposal incomplete;
- duplicate/unknown decision rejection;
- partial decisions preserve original documentation;
- rejected relationship isolation;
- accepted-only merge;
- deterministic Markdown report;
- RFC-0043 regression.

### 17.5 Compatibility and regression

- RFC-0044 Builder, Validator, Renderer, and snapshot tests;
- RFC-0037/0038 ownership and ordering tests;
- RFC-0039–0041 execution/snapshot/CLI tests;
- RFC-0042 AI incremental tests;
- RFC-0043 complete-review tests;
- full `clean build`;
- full `clean test`;
- isolated `architecture-samples` CLI smoke;
- `git diff --check`;
- allowed/protected path review.

## 18. Completion criteria

RFC-0045 implementation is complete only when:

1. relationship-only DIR changes create deterministic first-class diff entries and actions;
2. affected endpoint scopes follow the explicit previous/current policy;
3. AI context is relationship-targeted and provider-neutral;
4. relationship review includes prior/current Evidence and retains complete-review-before-merge;
5. removed relationships are reviewed without implicit block deletion;
6. DIR 0.3 and snapshot format 1 remain compatible;
7. all targeted and full tests pass;
8. isolated CLI smoke passes without modifying the source fixture;
9. no MCP source or project-state dependency is added;
10. Canonical RFC, Main Planning, Completion Handoff, and Roadmap accurately reflect actual evidence.

## 19. Known design risks

- Enum expansion can break exhaustive downstream `when` expressions at compile time.
- Relationship endpoint ownership propagation can become over-broad if module/package inference is guessed.
- A stable relationship ID currently encodes type/endpoints, so endpoint moves are remove/add rather than modified.
- Removed relationship review does not physically delete managed blocks.
- A full renderer may still rewrite one artifact even though AI/review targets are selective.

These risks must be tested or recorded as limitations; they must not be hidden behind successful aggregate test counts.

## 20. Follow-up candidates

Possible later work, not approved by this RFC:

- auditable review persistence and stale-document conflict detection;
- CLI review decision/apply workflow;
- explicit managed-block deletion semantics;
- evidence-backed semantic extraction for EXTENDS/IMPLEMENTS/CALLS;
- dedicated release provenance and determinism gates.
