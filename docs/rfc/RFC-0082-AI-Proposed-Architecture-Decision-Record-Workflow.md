# RFC-0082 — AI-Proposed Architecture Decision Record Workflow

Status: `IMPLEMENTED`

Track: Documentation Claims / Findings track (RFC-0077–0082) — separate from the Product-Owner-fixed RFC-0064–0074 sequence. **This is the final item in the track.**

Depends on: RFC-0078 (Evidence-Bound Finding and Severity Model) — implemented, `19fdd2b`. RFC-0079 (Cross-Artifact Synthesis Request and Advisory Document Tier) — implemented, `fac0449`. RFC-0080 (Executive Summary and Known Issues Register Document Types) — implemented, `d4a5104`. RFC-0081 (Productization Roadmap Document and Human Curation Step) — implemented, `3aa63e0`.

RFC-0082 normally depends on RFC-0073 (Official Reconciliation CLI Workflow) per the user's original track description. RFC-0073 is confirmed **not implemented** (no RFC doc — `docs/rfc/` has no RFC-0073 file — no git history, only a `docs/roadmap/ROADMAP.md` line item). The user explicitly approved skipping this dependency and proceeding directly to RFC-0082, the same pattern as the RFC-0071 skip recorded in RFC-0072's doc. RFC-0073 remains unblocked and unaffected by this RFC.

## Problem and decision

Separate from the existing `generate adr` CLI command (a human directly supplies `title`/`context`/`decision`/`consequences`/`alternatives` as text), an AI should propose an ADR draft citing `Finding` evidence, and a human approves or rejects it via the review pipeline. The AI must never hold canonical-approval or lifecycle-transition authority — the roadmap's explicit, repeatedly-preserved constraint throughout this whole track.

Research into the existing `generate adr` pipeline (`docpilot-cli/.../GenerateCommand.kt`'s `adr()`, `io.docpilot.core.generator.adr.*`) found two things that shaped this design:

1. **`facade.generateAdr(AdrGenerationRequest)` performs a real, second AI call, not a template render.** `AdrGenerationRequest` requires a `knowledge: KnowledgeBuildResult` and routes the supplied `title`/`context`/`decision`/`consequences`/`alternatives` as **prompt variables** through `DefaultAdrGenerator` → `DocumentService` → `GenerationPipeline` → `AiProvider.generate(...)` — the model expands/rewrites them into prose; nothing is inserted verbatim. Feeding an already AI-drafted proposal into this type would trigger an unwanted second, redundant AI pass that could silently rewrite the accepted content, and this workflow has no natural way to produce a `KnowledgeBuildResult`. **This RFC does not call `AdrGenerator`/`GenerationPipeline`/`AdrGenerationRequest` at all.**
2. **`Document`/`DocumentSection`/`DocumentRenderer`** (`src/main/kotlin/io/docpilot/core/document/`) are simple, dependency-free data classes plus a stateless renderer with no AI or knowledge dependency. This is the layer this RFC reuses to render an already-drafted, human-accepted proposal — exactly matching the existing rendered-ADR shape (one section, id `"adr"`, title `"Architecture Decision Record"`) with zero new rendering code.

`AdrStatus.PROPOSED` (already existing, `AdrMetadata.kt`) is reused directly: an AI-drafted proposal is conceptually always "not yet decided" until a human's `DocumentationReviewDecision` exists. Only the adoption step — gated on an `ACCEPTED` decision — ever tags a rendered `Document`'s metadata `ACCEPTED`; the human's decision is what makes a draft canonical, never the AI.

This closes out the full reuse chain built across the track: `Finding` (RFC-0078) supplied as `SynthesisSource`s → `SynthesisEngine` (RFC-0079) drafts, fail-closed evidence-checked via `ClaimEvidenceBinder` (RFC-0072) before any AI call → a new heading-block parser (this RFC) → `DocumentationReviewDecision` (pre-existing, reused unmodified since RFC-0072/RFC-0081) records the human decision → only on `ACCEPTED` → `Document`/`DocumentSection`/`DocumentRenderer` (pre-existing, reused unmodified) render the final canonical ADR.

## Scope

| Concept | Type/Object | Purpose |
|---|---|---|
| AI drafting | `AdrProposalRequestBuilder`, `AiProposedAdr`, `AiProposedAdrBuilder` | Builds a `SynthesisRequest`, parses the five-section response |
| Human curation and adoption | `AdrProposalCurationBinding`, `AiProposedAdrAdoption` | Records the accept/reject decision; the only path to a canonical `Document` |

All new code lives under `src/main/kotlin/io/docpilot/core/documentation/adr/` (`AiProposedAdr.kt`, `AiProposedAdrAdoption.kt`), package `io.docpilot.core.documentation.adr`, sibling to `.backlog`/`.advisory`/`.synthesis`/`..specification.finding`/`.claim`. No existing file is modified. Core-library-only: no CLI wiring, no `DocumentationArtifactKind` entry.

## AI drafting

`AdrProposalRequestBuilder.request(sources, canonicalFacts, providerId, model)` composes with `SynthesisPrompt.render()`'s existing grounding instructions (does not modify RFC-0079's file) by appending a required format instruction: exactly five `## <Heading>` sections, in order — Title, Context, Decision, Consequences, Alternatives — each grounded only in the supplied canonical input.

`AiProposedAdrBuilder.build(result: SynthesisResult): AiProposedAdr?` parses `result.content` with a heading-block regex (`^##\s+(Title|Context|Decision|Consequences|Alternatives)\s*$`, multiline, case-insensitive). Fail-closed: requires exactly 5 heading matches whose name-set equals the required 5 (rejecting both missing and duplicated headings in one check) and every section body non-blank — any violation returns `null` for the whole draft, never a partial result. `AiProposedAdr.proposalId` reuses `SynthesisRecord.synthesisStableId` directly (no new ID scheme); `citedFindingIds` reuses `SynthesisRecord.sourceArtifactIds` directly.

**Known, documented limitation**: an unexpected extra heading the AI adds despite the instruction is not itself detected as an error — its text is silently absorbed into the preceding section's body rather than causing rejection. This is a deliberate simplicity/robustness tradeoff (avoiding a more complex "reject on any non-required heading" parser for marginal benefit), consistent with RFC-0080's "honest limitation" precedent rather than an oversight.

## Human curation and adoption — where AI authority stops

```kotlin
public object AiProposedAdrAdoption {
    public fun adopt(proposal: AiProposedAdr, decision: DocumentationReviewDecision): Document {
        require(decision.targetId == proposal.proposalId) { ... }
        require(decision.disposition == DocumentationReviewDisposition.ACCEPTED) { ... }
        // builds a Document/DocumentSection/DocumentMetadata directly — no AdrGenerator call
    }
}
```

`adopt()` is the **only** function in this RFC that produces a `Document`, and it structurally cannot run without a matching `ACCEPTED` `DocumentationReviewDecision` — this is the concrete mechanism enforcing "AI has no canonical-approval authority." The rendered `Document`'s metadata reuses `AdrMetadata.STATUS_KEY`/`TITLE_KEY`/`GENERATOR_KEY`/`DOCUMENT_TYPE`/`SECTION_ID` constants, but sets `GENERATOR_KEY` to `"adr-ai-proposed"` — deliberately distinct from the existing generator's `"adr"` value, since reusing that value would misleadingly imply the document passed through `DefaultAdrGenerator`'s AI-expansion path, which it did not. Callers render the result with the existing, unmodified `DocumentRenderer().render(document)`.

## Reuse of prior RFCs and pre-existing subsystems (five points)

1. `Finding` (RFC-0078) — supplied by the caller as `SynthesisSource`s.
2. `SynthesisEngine`/`SynthesisRequest`/`SynthesisResult`/`SynthesisRecord` (RFC-0079) — the entire AI-drafting path.
3. `ClaimEvidenceBinder.resolveRefs` (RFC-0072) — exercised transitively through `SynthesisEngine`; the fifth RFC in this track to depend on this one function.
4. `DocumentationReviewDecision`/`DocumentationReviewDisposition` (pre-existing review package) — the human curation decision shape, reused unmodified, same pattern as RFC-0072's `ClaimReviewBinding` and RFC-0081's `ProductizationCurationBinding`.
5. `Document`/`DocumentSection`/`DocumentRenderer`/`AdrMetadata`/`AdrStatus` (pre-existing ADR subsystem) — the rendering layer and status/metadata vocabulary, reused unmodified.

Explicitly **not** reused, and why: `AdrGenerator`/`GenerationPipeline`/`AdrGenerationRequest` — would trigger a redundant second AI call and require a `KnowledgeBuildResult` this workflow has no natural way to produce (see Problem and decision).

## Compatibility

- No DIR, Snapshot, Review Bundle, Evolution Report, Documentation Profile, or Bundle/Manifest format change.
- No CLI wiring, no `DocumentationArtifactKind` entry.
- No existing file modified.
- The existing `generate adr` command, `AdrGenerator`, and `GenerationPipeline` are completely unaffected — this RFC adds a parallel path, not a replacement.

## Out of scope

- CLI wiring for AI-proposed ADR drafting or curation.
- Persisting proposals or decisions (mirrors RFC-0081's `ProductizationRoadmapCurator.apply` — pure in-memory).
- Any change to the existing `generate adr` command or its underlying generator/pipeline.

## Verification (executed)

- Implementation: 2 new files under `src/main/kotlin/io/docpilot/core/documentation/adr/`. No existing file was modified.
- `./gradlew test --tests "io.docpilot.core.documentation.adr.*"` (root module): **PASS**, 9 tests, 0 failures (`AiProposedAdrTest` 5, `AiProposedAdrAdoptionTest` 4).
- `./gradlew test` (full multi-module regression — root, `docpilot-cli`, `docpilot-provider-openai`, `docpilot-provider-ollama`, `docpilot-release`): **PASS**, `BUILD SUCCESSFUL`, 0 failures.

## Acceptance criteria

1. An AI drafts an ADR proposal citing Finding evidence via `SynthesisEngine`, fail-closed evidence-checked before any AI call. **Met.**
2. The five-section response format is parsed fail-closed — any missing/duplicated heading or blank body rejects the whole draft. **Met.**
3. A human decision, reusing the pre-existing `DocumentationReviewDecision` unmodified, is the only path to adoption. **Met.**
4. `adopt()` cannot produce a `Document` without a matching `ACCEPTED` decision — AI never holds canonical-approval authority. **Met.**
5. The adopted `Document` renders via the existing, unmodified `DocumentRenderer`, with honest provenance metadata distinguishing it from the standard `AdrGenerator` path. **Met.**
6. No DIR, Snapshot, Review Bundle, Evolution Report, Bundle/Manifest, or existing-file changes; the existing `generate adr` command is unaffected. **Met.**
7. Full Gradle regression passes with 0 failures. **Met.**

## Track completion

This closes the RFC-0077–0082 track. RFC-0077 (Document Coverage Completion) shipped first, independent of the rest. RFC-0072 (Claims/Traceability, a prerequisite discovered to be unimplemented) and RFC-0078 (Finding model) established the fail-closed evidence-binding foundation. RFC-0079 (Synthesis/Advisory tier) and RFC-0080 (Executive Summary/Known Issues Register) built the cross-artifact AI-drafting pipeline and its first document types. RFC-0081 (Productization Roadmap/curation) and this RFC (AI-Proposed ADR) both needed to correct an initial framing assumption about "reusing the existing review pipeline" — in both cases, research found the actual reusable primitive was narrower (`DocumentationReviewDecision` alone) than assumed, and each RFC's doc records that correction rather than silently reusing something that didn't fit.
