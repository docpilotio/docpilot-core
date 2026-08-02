# RFC-0079 — Cross-Artifact Synthesis Request and Advisory Document Tier

Status: `IMPLEMENTED`

Track: Documentation Claims / Findings track (RFC-0077–0082) — separate from the Product-Owner-fixed RFC-0064–0074 sequence.

Depends on: RFC-0070 (Structured AI Documentation Enrichment) — implemented, the sibling subsystem this RFC parallels. RFC-0072 (Documentation Claims and Traceability) — implemented, `65eb9a1`. RFC-0078 (Evidence-Bound Finding and Severity Model) — implemented, `19fdd2b`.

## Problem and decision

Today's only AI documentation request model (RFC-0070, `DocumentationEnrichmentEngine`) is "1 artifact – 1 request": `DocumentationEnrichmentRequest` carries a single `DocumentationEnrichmentTarget` (one `artifactId`+`sectionId`), and its output is patched into an *existing* Canonical document's body via `DocumentationEnrichmentSections.apply()`. There is no batching or multi-target concept anywhere in that subsystem, and `apply()` structurally rejects re-patching a document that already carries enrichment markers — it cannot produce a net-new standalone document.

RFC-0079 introduces a second, distinct AI request kind — `SynthesisRequest`/`SynthesisEngine` — that bundles two or more sources (Contracts/Features/Evidence) into a single AI context, producing a standalone document. Because this output synthesizes across multiple canonical sources rather than narrowly describing one, it is explicitly marked with a new `DocumentationTier.ADVISORY` value, distinct from `CANONICAL` (today's deterministic, Profile-rendered documents).

## Scope

| Concept | Type/Object | Purpose |
|---|---|---|
| Tier marker | `DocumentationTier { CANONICAL, ADVISORY }` | New, standalone content-provenance classification |
| Request model | `SynthesisSource`, `SynthesisRequest` | Bundles 2+ sources into one AI context |
| Provenance record | `SynthesisRecord`, `SynthesisResult` | Mirrors `DocumentationEnrichmentRecord`'s audit shape; `tier` defaults to `ADVISORY` |
| Prompt | `SynthesisPrompt` | Deterministic canonical-input serialization + AI instruction text |
| Engine | `SynthesisEngine` | The fail-closed construction/invocation entry point |

All new code lives in one file, `src/main/kotlin/io/docpilot/core/documentation/synthesis/DocumentationSynthesis.kt`, package `io.docpilot.core.documentation.synthesis` — mirroring `DocumentationEnrichment.kt`'s own convention of housing one cohesive AI-request subsystem in a single file, since this RFC extends that subsystem's sibling concern. No existing file is modified.

## Model

```kotlin
public enum class DocumentationTier { CANONICAL, ADVISORY }

public data class SynthesisSource(
    val artifactId: String, val sourceKind: String,
    val sourceModelStableIds: List<String> = emptyList(),
    val evidenceRefs: List<String> = emptyList(), val unresolvedRefs: List<String> = emptyList(),
)

public data class SynthesisRequest(
    val documentType: String, val sources: List<SynthesisSource>,
    val canonicalNarrative: String, val providerId: String, val model: String,
)
```

`SynthesisRequest` requires `sources.size >= 2` and unique `artifactId`s per source — the structural encoding of "cross-artifact": a single-source request is rejected at construction, before any engine or provider is involved. `sourceKind` is a plain non-blank `String` ("CONTRACT"/"FEATURE"/"EVIDENCE"/etc.) rather than a closed enum, following the same open-vocabulary precedent used for `Finding.category` (RFC-0078) and `Evidence.type`.

`SynthesisRecord` reuses `DocumentationEnrichmentStatus` (`APPLIED/FAILED/FALLBACK/SKIPPED/NOT_APPLIED/STALE/REJECTED`, RFC-0070) directly rather than introducing a parallel status enum, and carries `tier: DocumentationTier = DocumentationTier.ADVISORY` — this field **is** the "explicitly marked as Advisory tier" requirement. `CANONICAL` exists in the enum only as the conceptual counterpart describing today's deterministic Profile-rendered documents; this engine never assigns it.

## Reuse of prior RFCs (three points, nothing reimplemented)

1. **`ClaimEvidenceBinder.resolveRefs`** (RFC-0072) — called once per source, fail-closed, **before** any AI provider call: `SynthesisEngine.synthesize()` iterates `request.sources` and validates each source's `evidenceRefs`/`unresolvedRefs` resolve against the given `ProjectSpecification`'s Evidence and Contracts. A request bundling a fabricated reference never reaches the provider. This is the third independent RFC to reuse this exact function (after RFC-0072's own `Claim` and RFC-0078's `Finding`), confirming it as the shared referential-integrity primitive for this whole track.
2. **`DocumentationEnrichmentStatus`** (RFC-0070) — shared status vocabulary, not duplicated.
3. **The `AiProvider`/`AiRequest` interface** (unmodified) — `AiRequest.messages: List<AiMessage>` already supports the multi-message payload this RFC needed; no provider-interface change was required.

## Deliberate divergence from `DocumentationEnrichmentEngine.validate()`

`SynthesisEngine.validate(content)` intentionally does **not** apply RFC-0070's markdown-structure ban or its literal-canonical-reference-restatement ban. Those rules exist because an enrichment *narrative fragment* is spliced into an existing Canonical document's body and must not look like it is redefining that document's structure. A synthesis result **is** a whole standalone Advisory document — it is expected to contain full Markdown (headings, lists, code fences) and to reference its source entities. Reapplying RFC-0070's narrow rules here would reject legitimate Advisory output. `validate()` instead checks: non-blank; length ≤ 20,000 characters (a whole-document budget, versus RFC-0070's 4,000-character narrative-fragment budget); and the same path/secret-pattern block used by RFC-0070's `sanitize()`/`validate()`.

## Compatibility

- No DIR, Snapshot, Review Bundle, Evolution Report, or Documentation Profile format change.
- **No CLI wiring and no RFC-0069 Bundle/Manifest change.** The Bundle/Manifest (`docpilot-cli/.../DocumentationBundle.kt`, `DocumentationBundleFormat.VERSION = 1`) hardcodes `"ownership":"DOCPILOT_OWNED"` as a JSON literal per artifact with no tier field; persisting an Advisory marker there would require a genuine schema field and a `formatVersion` bump past 1, breaking RFC-0072's and RFC-0077's explicit promise not to change the bundle format. This RFC therefore stays core-library-only — the same choice already made for RFC-0072's `ClaimTraceabilityChecker`. The Advisory marker lives on `SynthesisRecord`, ready for a future RFC to wire into persistence when actually needed.
- `DocumentationOwnership` (`io.docpilot.core.reconciliation.ReconciliationModels.kt`) is unmodified and unrelated: it governs edit ownership for reconciliation/merge, a different axis from `DocumentationTier`'s content-provenance classification. The two are not conflated.
- No existing file modified — this RFC adds only `DocumentationSynthesis.kt` and its test.

## Out of scope

- CLI wiring (`GenerateCommand`/`DocumentationGenerationWorkflow`) for triggering synthesis requests.
- Any RFC-0069 Bundle/Manifest schema change to persist `DocumentationTier` (see Compatibility).
- Concrete Advisory document types (RFC-0080's job — Executive Summary, Known Issues Register).
- Any change to `DocumentationEnrichmentSections`/canonical-document patching — Advisory output is a new standalone document, not a patch to an existing one.

## Verification (executed)

- Implementation: 1 new file, `src/main/kotlin/io/docpilot/core/documentation/synthesis/DocumentationSynthesis.kt`. No existing file was modified.
- `./gradlew test --tests "io.docpilot.core.documentation.synthesis.*"` (root module): **PASS**, 8 tests, 0 failures (`DocumentationSynthesisTest`).
- `./gradlew test` (full multi-module regression — root, `docpilot-cli`, `docpilot-provider-openai`, `docpilot-provider-ollama`, `docpilot-release`): **PASS**, `BUILD SUCCESSFUL`, 0 failures.

## Acceptance criteria

1. A new request kind bundles 2+ sources (Contracts/Features/Evidence) into a single AI context, structurally rejecting a single-source request. **Met.**
2. Output is explicitly marked as a distinct Advisory tier, separate from Canonical, via `DocumentationTier`/`SynthesisRecord.tier`. **Met.**
3. Source `evidenceRefs` are fail-closed validated against real Evidence/Contract, before any AI provider call, by direct reuse of `ClaimEvidenceBinder.resolveRefs`. **Met.**
4. No DIR, Snapshot, Review Bundle, Evolution Report, or Bundle/Manifest format changes; no existing file modified. **Met.**
5. Full Gradle regression passes with 0 failures. **Met.**
