# RFC-0081 — Productization Roadmap Document and Human Curation Step

Status: `IMPLEMENTED`

Track: Documentation Claims / Findings track (RFC-0077–0082) — separate from the Product-Owner-fixed RFC-0064–0074 sequence.

Depends on: RFC-0078 (Evidence-Bound Finding and Severity Model) — implemented, `19fdd2b`. RFC-0079 (Cross-Artifact Synthesis Request and Advisory Document Tier) — implemented, `fac0449`. RFC-0080 (Executive Summary and Known Issues Register Document Types) — implemented, `d4a5104`.

## Problem and decision

RFC-0081's original scope, as given, was a document type organizing `Finding`s into a P0/P1/P2 backlog plus a human curation step "reusing the existing review prepare/decide/apply pipeline." Research corrected two assumptions in that framing before design began:

1. **"Prepare/decide/apply" is not real CLI terminology in this repo.** The only CLI-wired pipeline with those verbs is `docpilot-cli/.../ReconcileCommand.kt`'s `preview/apply/recover/verify` — a different subsystem (RFC-0055 reconciliation, `io.docpilot.core.reconciliation.*`, disposition `ACCEPT_GENERATED/KEEP_CURRENT/REJECT`). The `io.docpilot.core.incremental.specification.review` package (`DocumentationReviewModels.kt`/`ReviewBundleModels.kt`/`ReviewLifecycleModels.kt`) — the closer conceptual match — is not currently exposed by any CLI command at all.
2. **`DocumentationReviewEntry`/`DocumentationReviewProposal`/`StoredReviewBundle` are hard-wired to a specification-diff-driven Markdown-patch concept**: `existingMarkdown`/`proposedMarkdown` with `init{}`-enforced pairings to `operation`/`documentationChangeKind`, `IncrementalUpdateTarget` (DIR entity kinds — PACKAGE/TYPE/API/...), `ChangeKind` (ADDED/REMOVED/MODIFIED, a before/after spec diff), and `StoredReviewBundle`'s `ReviewSpecificationIdentity` (before/after specification SHA-256 pair). None of `Finding`'s fields (subjectStableId, semanticKey, category, severity) map onto these, and a Finding-adoption decision has no natural "existing Markdown → proposed Markdown" shape. Forcing Findings through these types would require fabricating fictional diff data — abusing their invariants, not honestly reusing them.

Separately, `DocumentationReviewDecision(targetId: String, disposition: DocumentationReviewDisposition, comment: String? = null)` **is** genuinely generic — zero diff-specific coupling, only non-blank-`targetId`/non-blank-or-null-`comment` invariants. This is exactly the type RFC-0072's `ClaimReviewBinding` already proved is a drop-in-valid target for any Stable ID.

**These findings were presented to the user, who confirmed (2026-08-02): partial reuse.** Reuse `DocumentationReviewDecision`/`DocumentationReviewDisposition` as-is for recording accept/reject; do not touch `DocumentationReviewEntry`/`Proposal`/`StoredReviewBundle`/`ReviewBundleRepository`/`ReviewLifecycleRepository`/`ApplyReceipt`. Build a new, proportionate, in-memory apply step instead — the same core-library-only discipline RFC-0079/0080 already established.

No `Priority`/`BacklogTier`/P0-P1-P2 precedent exists anywhere in the repo (the only similarly-named type, `GenerationPriority`, is an unrelated job-scheduling enum). `FindingSeverity` (RFC-0078) is the closest existing ranking concept; this RFC derives priority from it via an explicit mapping function rather than introducing an independent AI-judged priority — the user's description frames this as *organizing* Findings, not re-judging them.

## Scope

| Concept | Type/Object | Purpose |
|---|---|---|
| Priority | `BacklogPriority`, `BacklogPriorityMapping` | Deterministic P0/P1/P2 derived from `FindingSeverity` |
| Backlog document | `ProductizationBacklogEntry`, `ProductizationRoadmapDocument`, `ProductizationRoadmapBuilder`, `ProductizationRoadmapMarkdownRenderer.render` | Deterministic — no AI call |
| Human curation | `ProductizationCurationBinding`, `CuratedProductizationRoadmap`, `ProductizationRoadmapCurator`, `ProductizationRoadmapMarkdownRenderer.renderCuration` | Records adopt/defer decisions, partitions the backlog |

All new code lives under `src/main/kotlin/io/docpilot/core/documentation/backlog/` (`ProductizationRoadmap.kt`, `ProductizationCuration.kt`), package `io.docpilot.core.documentation.backlog`, sibling to `.advisory`/`.synthesis`/`..specification.finding`/`.claim`. No existing file is modified. Core-library-only: no CLI wiring, no `DocumentationArtifactKind` entry.

## The backlog document — deterministic, no AI call

```kotlin
public enum class BacklogPriority { P0, P1, P2 }

public object BacklogPriorityMapping {
    public fun of(severity: FindingSeverity): BacklogPriority = when (severity) {
        FindingSeverity.CRITICAL -> BacklogPriority.P0        // ship-blocking
        FindingSeverity.HIGH, FindingSeverity.MEDIUM -> BacklogPriority.P1   // important
        FindingSeverity.LOW, FindingSeverity.INFO -> BacklogPriority.P2      // backlog
    }
}
```

`ProductizationRoadmapBuilder.build(findings: List<Finding>)` requires a non-empty list, maps each `Finding` to a `ProductizationBacklogEntry(findingId, priority, statement)` where `statement` is an `AS_IS` `LabeledStatement` (RFC-0080) carrying that Finding's **own specific** `evidenceRefs` — a per-item guarantee, matching Known Issues Register (RFC-0080), not Executive Summary's weaker aggregate. No `ClaimEvidenceBinder` re-check is performed, for the same reason as RFC-0080: `FindingFactory` already guarantees validity. Entries are sorted P0 first. `ProductizationRoadmapMarkdownRenderer.render(...)` renders `## P0`/`## P1`/`## P2` sections with `[AS-IS]`-tagged entries.

## The human curation step

```kotlin
public object ProductizationCurationBinding {
    public fun decisionTargetId(entry: ProductizationBacklogEntry): String = entry.findingId
}

public data class CuratedProductizationRoadmap(
    val adopted: List<ProductizationBacklogEntry>,
    val deferred: List<ProductizationBacklogEntry>,
    val pending: List<ProductizationBacklogEntry>,
)

public object ProductizationRoadmapCurator {
    public fun apply(document: ProductizationRoadmapDocument, decisions: List<DocumentationReviewDecision>): CuratedProductizationRoadmap
}
```

`decisions` is `List<DocumentationReviewDecision>` from the pre-existing, unmodified `io.docpilot.core.incremental.specification.review` package — reused exactly as-is. `apply()` fail-closed rejects a decision targeting an unknown backlog entry and rejects duplicate decisions for the same target, then partitions every entry into `adopted` (ACCEPTED), `deferred` (REJECTED), or `pending` (no decision recorded). This is a new, proportionate, purely in-memory function — not `ReviewLifecycleApplyWorkflow`, which is file-system-coupled and tied to `StoredReviewBundle`'s specification-diff invariants that a Finding backlog does not have. `ProductizationRoadmapMarkdownRenderer.renderCuration(...)` (an extension function on the renderer object, defined alongside the curation types) renders `## Adopted`/`## Deferred`/`## Pending` sections.

## Reuse of prior RFCs (four points)

1. `Finding` (RFC-0078) — the entire backlog's source data.
2. `LabeledStatement`/`StatementLabel` (RFC-0080) — the AS-IS description shape for each backlog entry.
3. `DocumentationTier` (RFC-0079) — document tier marking (`ProductizationRoadmapDocument.tier` defaults to `ADVISORY`).
4. `DocumentationReviewDecision`/`DocumentationReviewDisposition` (pre-existing review package, predating this track) — the human curation decision shape, used completely unmodified, exactly as RFC-0072's `ClaimReviewBinding` established the pattern.

Explicitly **not** reused, and why: `DocumentationReviewEntry`/`DocumentationReviewProposal`/`StoredReviewBundle`/`ReviewBundleRepository`/`ReviewLifecycleRepository`/`ApplyReceipt` — all hard-wired to specification-diff/Markdown-patch semantics that Findings don't have (see Problem and decision).

## Compatibility

- No DIR, Snapshot, Review Bundle, Evolution Report, Documentation Profile, or Bundle/Manifest format change.
- No CLI wiring, no `DocumentationArtifactKind` entry.
- No existing file modified.

## Out of scope

- CLI wiring for backlog generation or curation decision recording.
- Persisting curation decisions (`ProductizationRoadmapCurator.apply` is a pure in-memory function; a future RFC could add persistence if needed, using a proportionate mechanism, not the diff-specific Review Bundle format).
- RFC-0082 (AI-Proposed ADR Workflow) — a separate, later item in this track.

## Verification (executed)

- Implementation: 2 new files under `src/main/kotlin/io/docpilot/core/documentation/backlog/`. No existing file was modified.
- `./gradlew test --tests "io.docpilot.core.documentation.backlog.*"` (root module): **PASS**, 10 tests, 0 failures (`ProductizationRoadmapBuilderTest` 4, `ProductizationRoadmapCuratorTest` 4, `ProductizationRoadmapMarkdownRendererTest` 2).
- `./gradlew test` (full multi-module regression — root, `docpilot-cli`, `docpilot-provider-openai`, `docpilot-provider-ollama`, `docpilot-release`): **PASS**, `BUILD SUCCESSFUL`, 0 failures.

## Acceptance criteria

1. Findings are organized into a P0/P1/P2 backlog via a deterministic, documented severity-to-priority mapping. **Met.**
2. Each backlog entry carries its own Finding's specific `evidenceRefs` (per-item guarantee). **Met.**
3. Human curation decisions reuse `DocumentationReviewDecision`/`DocumentationReviewDisposition` unmodified; a Finding's Stable ID is a drop-in-valid `targetId`. **Met.**
4. Curation `apply()` fail-closed rejects unknown/duplicate decision targets and partitions entries into adopted/deferred/pending. **Met.**
5. `DocumentationReviewEntry`/`Proposal`/`StoredReviewBundle`/Review Bundle persistence are correctly identified as unsuitable and not abused; the RFC doc states why. **Met.**
6. No DIR, Snapshot, Review Bundle, Evolution Report, Bundle/Manifest, or existing-file changes. **Met.**
7. Full Gradle regression passes with 0 failures. **Met.**
