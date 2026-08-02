# RFC-0072 — Documentation Claims and Traceability

Status: `IMPLEMENTED`

Track: v1.1 Product Capability

Depends on: none implemented as a prerequisite in this RFC.

RFC-0072 normally depends on RFC-0071 (Diagram IR and Mermaid Rendering) per the `RFC-0064-RFC-0074-FIRST-PRODUCT-DEVELOPMENT-ROADMAP.md` default dependency order (`... → RFC-0069 → (RFC-0070 / RFC-0071) → RFC-0072 → ...`). That dependency is explicitly skipped here: Diagram IR/Mermaid rendering is unrelated to Claim/Evidence/Contract binding and Claim traceability, and the user has approved proceeding directly to RFC-0072 ahead of RFC-0071. RFC-0071 remains unblocked and unaffected by this RFC.

## Problem and decision

No `Claim`/`ClaimId`/`ClaimRecord` type has ever existed in this codebase. `docs/vision/VISION.md:79` and `docs/dsd/DSD-0001-DocPilot-Specification-Language.md:480` name `DocumentationClaim` as a planned concept, with fields explicitly "deferred to RFC-0059 and later RFCs" — no prior field spec exists to honor. The roadmap describes RFC-0072's scope as adding "stable Claim, Evidence, entity, section, and review bindings, including orphan, stale, and broken traceability detection," while limiting AI authority so it "cannot create canonical entities or Evidence, mutate Stable IDs or endpoints, change Coverage or Artifact Plans, approve review, or transition lifecycle state."

RFC-0072 introduces the first concrete `Claim` model: a Stable-ID-addressable assertion that binds a subject (an Entity, a Document Section, or a Document) to canonical `evidenceRefs` — validated fail-closed against both DIR `Evidence` and `Contract` — plus a deterministic checker that re-validates existing Claims against a (possibly newer) `ProjectSpecification` to detect orphan, stale, and broken traceability. It is strictly additive: no DIR schema, Snapshot format, Review Bundle format, or Evolution Report format changes, and no CLI wiring.

This is prerequisite groundwork for RFC-0078 ("Evidence-Bound Finding and Severity Model"), which will call `ClaimEvidenceBinder.resolveRefs()` directly for its own `Finding.evidenceRefs` validation rather than reimplementing fail-closed resolution.

## Scope

| Concept | Type/Object | File | Purpose |
|---|---|---|---|
| Claim identity | `ClaimId`, `ClaimSubject`, `ClaimSubjectKind`, `ClaimOrigin`, `Claim` | `Claim.kt` | The canonical assertion model and its subject/origin vocabulary |
| Stable ID scheme | `ClaimIdentity` | `ClaimIdentity.kt` | Deterministic `claim:<hash32>` derivation from `(subject, semanticKey)`, mirroring `ContractIdentity.of` |
| Evidence/Contract binding | `ClaimEvidenceBinder` | `ClaimEvidenceBinder.kt` | Fail-closed resolution of `evidenceRefs`/`unresolvedRefs` against `ProjectSpecification.evidence` and `.contracts` — the function RFC-0078 will call directly |
| AI authority boundary | `ClaimAiAuthority` | `ClaimAiAuthority.kt` | Content-level check rejecting AI assertions that restate or redefine canonical fields |
| Construction | `ClaimFactory` | `ClaimFactory.kt` | The single fail-closed entry point; only `deterministic()` may target an `ENTITY` subject |
| Traceability | `ClaimTraceability`, `ClaimTraceabilityChecker`, `ClaimTraceabilityIssue`, `ClaimTraceabilityIssueReason` | `ClaimTraceability.kt` | `boundFactsSha256` content hash plus a deterministic, callable ORPHAN/STALE/BROKEN checker |
| Review binding | `ClaimReviewBinding` | `ClaimReviewBinding.kt` | Confirms a Claim's Stable ID is a drop-in valid Review Bundle `targetId` |

All new code lives under `src/main/kotlin/io/docpilot/core/specification/claim/`, alongside `ContractIdentity` and `ProjectSpecificationValidator`, which a `Claim` binds against the same way Contract validation does.

## Claim model and Stable ID scheme

```kotlin
public enum class ClaimSubjectKind { ENTITY, SECTION, DOCUMENT }
public data class ClaimSubject(val kind: ClaimSubjectKind, val stableId: String)
public enum class ClaimOrigin { DETERMINISTIC, AI_NARRATIVE, AI_PATCH_PROPOSAL }

public data class Claim(
    val formatVersion: Int = 1,
    val id: ClaimId,
    val subject: ClaimSubject,
    val semanticKey: String,
    val assertion: String,
    val evidenceRefs: Set<String>,
    val unresolvedRefs: Set<String> = emptySet(),
    val origin: ClaimOrigin,
    val assertionSha256: String,
    val boundFactsSha256: String,
)
```

`ClaimId` is generated from `(subject.kind, subject.stableId, semanticKey)` only — never from `assertion` text, so identity survives narrative re-wording, exactly as a Contract's `id` does not change when its `displayName` changes:

```kotlin
"claim:${sha256(canonicalize([subject.kind.name, subject.stableId, semanticKey])).take(32)}"
```

This mirrors `ContractIdentity.of`'s canonicalization (field-separator join, NFC normalization, SHA-256, truncate). `Claim.id.value` doubles directly as a Review Bundle `targetId` — no separate review-identity field exists.

Drift in the underlying facts is captured separately by `boundFactsSha256` (see Traceability below), not by `ClaimId`.

## Evidence and Contract binding (`ClaimEvidenceBinder`)

```kotlin
public object ClaimEvidenceBinder {
    public fun unresolvedTargets(specification: ProjectSpecification, refs: Set<String>): Set<String>
    public fun resolveRefs(specification: ProjectSpecification, evidenceRefs: Set<String>, unresolvedRefs: Set<String>, label: String)
}
```

`resolveRefs` is fail-closed via `require()` (throws `IllegalArgumentException`, no silent drop) — the same idiom `ProjectSpecificationValidator`'s private DIR 0.5 `validateRefs` closure already uses for the identical category of check (referential/graph integrity). It requires: `evidenceRefs` non-empty; every ref resolves against the union of `ProjectSpecification.evidence.id` and `.contracts.id`; at least one resolved ref is not `LOW`-confidence Evidence (Contract refs are exempt from the confidence rule, since Contracts carry no confidence field); and every `unresolvedRef` exists in `ProjectSpecification.unresolved`.

**This is the exact function RFC-0078's `Finding` model will call directly** — `ClaimEvidenceBinder.resolveRefs(specification, finding.evidenceRefs, finding.unresolvedRefs, "Finding")` — rather than reimplementing fail-closed Evidence/Contract resolution.

## AI authority boundary

`ClaimAiAuthority.reject(origin, assertion, protectedStableIds): String?` mirrors `DocumentationEnrichmentEngine.validate()`'s nullable-rejection-reason style (content moderation, not structural integrity — hence non-throwing here, unlike `ClaimEvidenceBinder`). It rejects when a non-`DETERMINISTIC` assertion restates any of its own `evidenceRefs`/`unresolvedRefs`/subject Stable ID verbatim, or attempts to redefine a canonical field (`stable id:`/`evidence:`/`unresolved:`-shaped text).

The roadmap's five AI-authority constraints map to concrete enforcement points, all inside `ClaimFactory`:

| Constraint | Enforcement |
|---|---|
| AI cannot create canonical entities | Only `ClaimFactory.deterministic()` may construct a Claim with `subject.kind == ENTITY`; `aiNarrative()` requires `SECTION`, `aiPatchProposal()` forbids `ENTITY` |
| AI cannot mutate Stable IDs or endpoints | `ClaimId` is derived only from `(subject, semanticKey)`, both supplied by the caller before AI authorship is invoked; `ClaimAiAuthority.reject` additionally blocks an AI assertion from restating any Stable ID in its own text |
| AI cannot change Coverage or Artifact Plans | Out of scope — this RFC does not touch `SelectiveDocumentationArtifactPlanner`/`DocumentationArtifactPlanIntegrity` |
| AI cannot approve review | This RFC adds no review-decision-authoring code path; `ClaimReviewBinding` only proves a Claim ID is an eligible review target, decisions remain human-authored via existing `DocumentationReviewDecision` |
| AI cannot transition lifecycle state | This RFC adds no code touching `ReviewLifecycleModels.kt`/`ReviewLifecycleRepository` |

## Orphan, stale, and broken traceability detection

```kotlin
public object ClaimTraceability {
    public fun boundFactsHash(specification: ProjectSpecification, evidenceRefs: Set<String>): String
}

public enum class ClaimTraceabilityIssueReason { ORPHAN, STALE, BROKEN }
public data class ClaimTraceabilityIssue(val claimId: ClaimId, val reason: ClaimTraceabilityIssueReason, val detail: String)

public object ClaimTraceabilityChecker {
    public fun check(
        specification: ProjectSpecification,
        claims: List<Claim>,
        knownSectionStableIds: Set<String>? = null,
        knownDocumentArtifactIds: Set<String>? = null,
    ): List<ClaimTraceabilityIssue>
}
```

`boundFactsSha256` hashes the *resolved content* of what a Claim's `evidenceRefs` point at (Evidence `type/file/symbol/lineStart/lineEnd/summary/confidence`, or Contract `semanticKey/displayName/kind/role`) at construction time — not the assertion text and not the ref IDs. Recomputing this hash against a newer `ProjectSpecification` and comparing is the drift signal, avoiding the need for a persisted content hash on `Evidence`/`RenderedArtifact` (neither carries one today).

`ClaimTraceabilityChecker.check` is a deterministic, callable checker — it is **not** wired into any CLI command (RFC-0073/0074 territory, out of scope here). Per Claim, in priority order:

- **ORPHAN**: the subject no longer exists. `ENTITY` is auto-derived from the given `specification` (project id, modules, packages, components, features, entry points, contracts). `SECTION`/`DOCUMENT` are checked only when the caller supplies `knownSectionStableIds`/`knownDocumentArtifactIds`, since `ProjectSpecification` alone has no section/artifact concept (see Known limitation).
- **BROKEN**: not orphaned, but `ClaimEvidenceBinder.unresolvedTargets` against the new specification is non-empty, or an `unresolvedRef` no longer exists in `specification.unresolved`. This is the *only* way BROKEN can occur — `ClaimFactory` is fail-closed, so a Claim can never be constructed already-broken; BROKEN is exclusively a re-validation-against-change outcome.
- **STALE**: not orphaned or broken, but `boundFactsHash(newSpecification, claim.evidenceRefs) != claim.boundFactsSha256`.

A Claim receives at most one issue (mutually exclusive, checked in the order above).

## Review binding

```kotlin
public object ClaimReviewBinding {
    public fun decisionTargetId(claim: Claim): String = claim.id.value
}
```

`DocumentationReviewDecision.targetId` and `DocumentationReviewEntry.targetId` (`io.docpilot.core.incremental.specification.review.DocumentationReviewModels.kt`) are already plain non-blank `String`, so a Claim's Stable ID is a drop-in valid review target identity. `DocumentationReviewModels.kt`, `ReviewBundleModels.kt`, `ReviewLifecycleModels.kt`, and the Review Bundle format-1 codec are **unchanged** by this RFC — a full `toReviewEntry()`-style wrapper was deliberately not built, since `DocumentationReviewEntry` requires a full incremental-diff context (`ChangeKind`, `IncrementalUpdateTarget`, etc.) unrelated to a Claim itself.

## Compatibility

- DIR 0.2, 0.3, 0.4, 0.5 readers, Snapshot formats 1/2/3, Review Bundle format 1, Evolution Report format 1, Documentation Profile version `kotlin-android@1`, and RFC-0052 Artifact identities are unchanged.
- No existing type, validator, renderer, or CLI command is modified. This RFC adds only new files under `io.docpilot.core.specification.claim`.
- `ProjectSpecificationValidator`, `ContractIdentity`, and `DocumentationEnrichmentEngine` are read-only precedents for this RFC's design; none of them are modified.

## Known limitation

`ClaimTraceabilityChecker.check`'s ORPHAN detection for `SECTION` and `DOCUMENT` subjects requires the caller to supply `knownSectionStableIds`/`knownDocumentArtifactIds`, because `ProjectSpecification` alone has no section or rendered-artifact concept (those live in the separate `io.docpilot.core.documentation.profile` and `io.docpilot.core.api` layers). When omitted, ORPHAN checks for those subject kinds are skipped rather than falsely reported.

## Out of scope

- RFC-0078 (Evidence-Bound Finding and Severity Model) — the next consumer of `ClaimEvidenceBinder.resolveRefs()`; not implemented here.
- Any CLI wiring for Claim construction or traceability checking (RFC-0073/RFC-0074 territory).
- Any change to DIR, Snapshot, Review Bundle, or Evolution Report formats.
- RFC-0071 (Diagram IR and Mermaid Rendering) — explicitly skipped as a dependency per the header above; tracked separately, unaffected by this RFC.

## Verification (executed)

- Implementation: 8 new files under `src/main/kotlin/io/docpilot/core/specification/claim/` (`Claim.kt`, `ClaimHashing.kt`, `ClaimIdentity.kt`, `ClaimEvidenceBinder.kt`, `ClaimAiAuthority.kt`, `ClaimFactory.kt`, `ClaimTraceability.kt`, `ClaimReviewBinding.kt`). No existing file was modified.
- `./gradlew test --tests "io.docpilot.core.specification.claim.*"` (root module): **PASS**, 21 tests, 0 failures (`ClaimFactoryTest` 12, `ClaimEvidenceBinderTest` 2, `ClaimTraceabilityCheckerTest` 5, `ClaimReviewBindingTest` 2).
- `./gradlew test` (full multi-module regression — root, `docpilot-cli`, `docpilot-provider-openai`, `docpilot-provider-ollama`, `docpilot-release`): **PASS**, `BUILD SUCCESSFUL`, 0 failures.

## Acceptance criteria

1. `Claim`/`ClaimId`/`ClaimSubject`/`ClaimOrigin` are modeled with a deterministic Stable-ID scheme independent of assertion text. **Met.**
2. `evidenceRefs` resolve fail-closed against both DIR Evidence and Contract, via a function directly reusable by RFC-0078. **Met.**
3. AI-origin Claims cannot bind `ENTITY` subjects, and cannot restate Stable IDs or Evidence/Contract references in their own assertion text. **Met.**
4. Orphan, stale, and broken traceability detection is implemented as a deterministic, callable checker, not CLI-wired. **Met.**
5. No DIR, Snapshot, Review Bundle, or Evolution Report format changes. **Met.**
6. Full Gradle regression passes with 0 failures. **Met.**
