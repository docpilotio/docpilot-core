# RFC-0078 — Evidence-Bound Finding and Severity Model

Status: `IMPLEMENTED`

Track: Documentation Claims / Findings track (RFC-0077–0082) — separate from the Product-Owner-fixed RFC-0064–0074 sequence.

Depends on: RFC-0072 (Documentation Claims and Traceability) — implemented, `65eb9a1`.

## Problem and decision

DocPilot needs a canonical `Finding` model: an AI-judged assessment (severity, category, a short summary) bound to a subject, with `evidenceRefs` that must be fail-closed validated against real `Evidence`/`Contract` — the AI may freely decide *how severe* or *what kind* of issue something is, but it cannot fabricate the facts a Finding rests on.

Two other `Finding`-named types already exist in this codebase, and neither is what this RFC needs:
- `DocumentPlanningFinding` (`io.docpilot.core.documentation.profile.DocumentationProfileModels.kt`) — deterministic Profile-resolution completeness/blocking issues (`MISSING_SPECIFICATION_ELEMENT`, etc.), no `evidenceRefs`, no AI involvement.
- `DocumentationQualityFinding` (`io.docpilot.core.validation.DocumentationQualityValidator.kt`) — deterministic quality-validator issues with a 2-value `ERROR`/`WARNING` severity, no `evidenceRefs`.

This RFC introduces a third, distinct type — plain `Finding`, scoped to its own package `io.docpilot.core.specification.finding` — rather than colliding with or repurposing either existing type. RFC-0072's own doc already forward-declared the exact reuse point this RFC uses: `ClaimEvidenceBinder.resolveRefs(specification, finding.evidenceRefs, finding.unresolvedRefs, "Finding")`.

## Scope

| Concept | Type/Object | File | Purpose |
|---|---|---|---|
| Finding model | `FindingId`, `FindingSeverity`, `Finding` | `Finding.kt` | The canonical AI-judged assessment model |
| Stable ID scheme | `FindingIdentity` | `FindingIdentity.kt` | Deterministic `finding:<hash32>` derivation from `(subjectStableId, category, semanticKey)` — reuses `ClaimHashing` |
| Construction | `FindingFactory` | `FindingFactory.kt` | The single fail-closed entry point; reuses `ClaimEvidenceBinder.resolveRefs` and `ClaimAiAuthority.reject` |

All new code lives under `src/main/kotlin/io/docpilot/core/specification/finding/`. No existing file is modified.

## Finding model

```kotlin
public enum class FindingSeverity { CRITICAL, HIGH, MEDIUM, LOW, INFO }

public data class Finding(
    val formatVersion: Int = 1,
    val id: FindingId,
    val subjectStableId: String,
    val semanticKey: String,
    val category: String,
    val severity: FindingSeverity,
    val summary: String,
    val evidenceRefs: Set<String>,
    val unresolvedRefs: Set<String> = emptySet(),
)
```

Two design choices, each backed by direct in-repo precedent rather than invented from scratch:

- **`category: String`, not a closed enum.** This codebase already models open, extensible vocabularies as plain non-blank `String` — `Evidence.type`, `ComponentSpecification.kind`/`.role` — rather than a closed taxonomy fixed up front. Finding categories depend on later synthesis-document work (RFC-0079–0082) not yet scoped; a closed enum today would need a breaking migration later, and no enum-versioning infrastructure exists in this codebase.
- **`severity: FindingSeverity` is a closed 5-level enum** (`CRITICAL, HIGH, MEDIUM, LOW, INFO`). This extends the one severity precedent already in-repo, `DocumentationQualitySeverity { ERROR, WARNING }`, to the finer grain "severity/priority" needs; enum declaration order already encodes priority ordering.

`FindingId` is derived only from `(subjectStableId, category, semanticKey)` — never from `severity` or `summary` — so an AI can re-judge severity or refine wording without identity churn, mirroring `ClaimIdentity`'s independence from `Claim.assertion` text:

```kotlin
"finding:${sha256(canonicalize([subjectStableId, category, semanticKey])).take(32)}"
```

Unlike `ClaimSubject`, `Finding.subjectStableId` carries no `ENTITY`/`SECTION`/`DOCUMENT` kind typing — that restriction in RFC-0072 existed specifically to enforce "AI cannot create canonical entities," a constraint this RFC's scope does not ask for.

## Reuse of RFC-0072 (the point of this RFC)

`FindingFactory.create(...)` is the single fail-closed construction entry point, and makes exactly two calls into RFC-0072's existing code — no Evidence/Contract resolution or AI-content-guard logic is reimplemented:

1. **`ClaimEvidenceBinder.resolveRefs(specification, evidenceRefs, unresolvedRefs, "Finding")`** — the exact function RFC-0072's own doc forward-declared for this purpose. Fail-closed via `require()`: `evidenceRefs` must be non-empty, every ref must resolve against the union of `ProjectSpecification.evidence.id` and `.contracts.id`, at least one resolved ref must not be `LOW`-confidence Evidence, and every `unresolvedRef` must exist in `ProjectSpecification.unresolved`.
2. **`ClaimAiAuthority.reject(ClaimOrigin.AI_NARRATIVE, summary, protectedStableIds)`** — the same content-restatement guard that protects `Claim.assertion`, applied to `Finding.summary`. Called unconditionally (not because Finding adopts Claim's origin/subject-kind model, but because every Finding's `summary` is expected to be AI-authored per this RFC's premise), rejecting a `summary` that restates one of its own `evidenceRefs`/`unresolvedRefs`/`subjectStableId` verbatim, or attempts to redefine a canonical field.

`FindingHashing`/a duplicate SHA-256 helper was not written: `FindingIdentity` calls `io.docpilot.core.specification.claim.ClaimHashing` directly (`internal`, but module-visible from the sibling `finding` package in the same Gradle module).

## Compatibility

- No DIR, Snapshot, Review Bundle, Evolution Report, or Documentation Profile format change.
- No existing file modified — this RFC adds only new files under `io.docpilot.core.specification.finding`, plus this doc.
- `DocumentPlanningFinding` and `DocumentationQualityFinding` are unaffected; `Finding` is a distinct type in a distinct package.

## Out of scope

- Finding traceability (orphan/stale/broken) checking — RFC-0072 built `ClaimTraceability` for `Claim` because the Product-Owner roadmap required it; this RFC's scope (model + AI severity + fail-closed evidenceRefs) did not ask for the equivalent on `Finding`.
- A Review Bundle binding helper for `Finding` (RFC-0072's `ClaimReviewBinding` precedent exists if needed later).
- Any CLI wiring for Finding construction.
- A closed `FindingCategory` enum/taxonomy — deferred to whichever of RFC-0079–0082 (synthesis documents) defines the category vocabulary it actually needs.

## Verification (executed)

- Implementation: 3 new files under `src/main/kotlin/io/docpilot/core/specification/finding/` (`Finding.kt`, `FindingIdentity.kt`, `FindingFactory.kt`). No existing file was modified.
- `./gradlew test --tests "io.docpilot.core.specification.finding.*"` (root module): **PASS**, 14 tests, 0 failures (`FindingFactoryTest`).
- `./gradlew test` (full multi-module regression — root, `docpilot-cli`, `docpilot-provider-openai`, `docpilot-provider-ollama`, `docpilot-release`): **PASS**, `BUILD SUCCESSFUL`, 0 failures.

## Acceptance criteria

1. `Finding` is modeled with `severity`/`category`/`evidenceRefs`, distinct from the two pre-existing `Finding`-named types. **Met.**
2. `evidenceRefs` are fail-closed validated against both DIR Evidence and Contract by directly calling `ClaimEvidenceBinder.resolveRefs` — no reimplementation. **Met.**
3. `severity`/`category` are freely AI-assignable without affecting `FindingId`. **Met.**
4. An AI-authored `summary` cannot restate a canonical Evidence/Contract reference or the subject Stable ID verbatim. **Met.**
5. No DIR, Snapshot, Review Bundle, or Evolution Report format changes; no existing file modified. **Met.**
6. Full Gradle regression passes with 0 failures. **Met.**
