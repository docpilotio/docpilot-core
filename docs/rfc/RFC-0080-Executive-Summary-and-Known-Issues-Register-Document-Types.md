# RFC-0080 — Executive Summary and Known Issues Register Document Types

Status: `IMPLEMENTED`

Track: Documentation Claims / Findings track (RFC-0077–0082) — separate from the Product-Owner-fixed RFC-0064–0074 sequence.

Depends on: RFC-0072 (Documentation Claims and Traceability) — implemented, `65eb9a1`. RFC-0078 (Evidence-Bound Finding and Severity Model) — implemented, `19fdd2b`. RFC-0079 (Cross-Artifact Synthesis Request and Advisory Document Tier) — implemented, `fac0449`.

## Problem and decision

RFC-0079 built the `SynthesisEngine`/`DocumentationTier.ADVISORY` pipeline but deliberately produced no concrete document type. RFC-0080 builds two: **Executive Summary** (a readiness narrative, synthesized across sources) and **Known Issues Register** (a defect register, compiled from `Finding`s). Both must render with **sentence-level AS-IS (evidence-backed) / TO-BE (proposed) labeling** — a genuinely new mechanism. Research confirmed no prior art: "AS-IS"/"TO-BE" appear nowhere in this codebase with an established meaning, and the existing evidence-backing discipline (`ClaimEvidenceBinder`, `evidenceRefs` on records) labels whole records as evidence-backed, never individual sentences within a document.

## Scope

| Concept | Type/Object | Purpose |
|---|---|---|
| AS-IS/TO-BE primitive | `StatementLabel`, `LabeledStatement` | Fail-closed by construction: AS-IS requires evidence, TO-BE forbids it |
| Known Issues Register | `KnownIssuesRegisterDocument`, `KnownIssuesRegisterBuilder`, `KnownIssuesRegisterMarkdownRenderer` | Deterministic — no AI call, built directly from `Finding`s (RFC-0078) |
| Executive Summary | `ExecutiveSummaryDocument`, `ExecutiveSummaryRequestBuilder`, `ExecutiveSummaryBuilder`, `ExecutiveSummaryMarkdownRenderer` | AI-authored — built via `SynthesisEngine` (RFC-0079) |

All new code lives under `src/main/kotlin/io/docpilot/core/documentation/advisory/` (`LabeledStatement.kt`, `KnownIssuesRegister.kt`, `ExecutiveSummary.kt`), package `io.docpilot.core.documentation.advisory`, sibling to `.synthesis` and `..specification.finding`/`.claim`. No existing file is modified.

**Core-library-only, no CLI/`DocumentationArtifactKind`/`ProjectSpecificationMarkdownRenderer` wiring** — the same choice RFC-0079 made. `ProjectSpecificationMarkdownRenderer`'s `DocumentationArtifactKind`/`when`-dispatch is a real, tightly-wired CLI registry; wiring in a new kind requires an enum value, a descriptor, and a `when` branch there, which was deliberately not done here to avoid re-opening the Bundle/Manifest format-version question RFC-0079 already sidestepped.

## The AS-IS/TO-BE primitive

```kotlin
public enum class StatementLabel { AS_IS, TO_BE }

public data class LabeledStatement(
    val label: StatementLabel, val text: String, val evidenceRefs: Set<String> = emptySet(),
) {
    init {
        require(text.isNotBlank())
        require(label != StatementLabel.AS_IS || evidenceRefs.isNotEmpty())   // AS-IS requires evidence
        require(label != StatementLabel.TO_BE || evidenceRefs.isEmpty())      // TO-BE forbids it — it's a proposal
    }
}
```

This single type is used by both document builders below, so the AS-IS/TO-BE invariant is enforced identically whether the statement came from a deterministic source (Known Issues Register) or an AI-authored one (Executive Summary).

## Known Issues Register — deterministic, no AI call

`KnownIssuesRegisterBuilder.build(findings: List<Finding>): KnownIssuesRegisterDocument` requires a non-empty list, orders Findings most-severe-first (`severity.ordinal` ascending — `CRITICAL` first), and maps each directly to an `AS_IS` `LabeledStatement` carrying that Finding's own `evidenceRefs`. **No `ClaimEvidenceBinder` re-check is performed here, deliberately**: every `Finding` is already guaranteed valid by `FindingFactory`'s own fail-closed construction (RFC-0078); re-validating identical references a second time would be redundant, not defense-in-depth. `KnownIssuesRegisterMarkdownRenderer.render(...)` produces a severity/category/summary/evidence-count table plus an `[AS-IS]`-tagged statement list, using the same `cell()`/`escapeText()` idiom as `ProfileDocumentCoverageMarkdownRenderer` and `ContractDocumentationMarkdownRenderer` (no shared table/escape helper exists across the renderer package today, so this follows that same per-file convention rather than introducing one).

Every statement here carries the Finding's own **specific** `evidenceRefs` — a strictly per-item guarantee, since no AI is involved at all.

## Executive Summary — AI-authored via `SynthesisEngine`

`ExecutiveSummaryRequestBuilder.request(sources, canonicalFacts, providerId, model)` composes with `SynthesisPrompt.render()`'s existing grounding instructions rather than replacing them: it appends a required line-format instruction to `canonicalNarrative` ("Format every non-blank line as either 'AS-IS: ...' or 'TO-BE: ...'. Use no other prefix, heading, or list marker.") before constructing the `SynthesisRequest`.

`ExecutiveSummaryBuilder.build(result: SynthesisResult): ExecutiveSummaryDocument?` parses `result.content`: splits on newlines, requires every non-blank line to start with `AS-IS:` or `TO-BE:` (case-insensitive) with non-blank remaining text — **any line lacking a valid prefix rejects the whole document** (returns `null`), never partially or silently mislabels one line. `content == null` (provider `FALLBACK`/`REJECTED`, from RFC-0079's own validation) also yields `null`.

A simple prefix-line parser was chosen over structured JSON output, even though `AiRequest.responseFormat: AiResponseFormat.JSON` already exists as an option. No JSON-parsing infrastructure exists anywhere in this codebase to safely consume untrusted AI-generated JSON — every existing codec here, including RFC-0069's Bundle/Manifest and RFC-0079's own `SynthesisEngine`, uses regex/line-based parsing, not a JSON library. Hand-rolling a JSON parser for untrusted AI output would add real risk (malformed/adversarial JSON edge cases) for no proportionate benefit over a strict line-prefix format.

### Honest limitation: per-statement vs. aggregate evidence attribution

AS-IS statements parsed from `SynthesisEngine` output carry the **aggregate** `evidenceRefs` of the whole request's sources — already fail-closed validated per-source before the AI ever ran (RFC-0079, via RFC-0072's `ClaimEvidenceBinder`) — not a per-sentence-specific citation. DocPilot cannot independently verify that one particular AI-generated sentence corresponds to one particular Evidence entry at natural-language granularity. The achievable and actually-delivered guarantee is: (a) every source bundled into the request is real, checked before the AI call; and (b) every line in the output is explicitly self-labeled, with any unlabeled line rejecting the whole document. This is real and useful, but strictly weaker than Known Issues Register's per-Finding attribution — that asymmetry is intentional and stated here rather than glossed over.

## Reuse of prior RFCs

1. `Finding` (RFC-0078) — Known Issues Register's entire input; no new Finding-shaped type invented.
2. `SynthesisEngine`/`SynthesisRequest`/`SynthesisResult`/`DocumentationTier` (RFC-0079) — Executive Summary's entire generation path; `ExecutiveSummaryRequestBuilder` composes with `SynthesisPrompt`, does not replace it.
3. `ClaimEvidenceBinder.resolveRefs` (RFC-0072) — already exercised transitively through both `FindingFactory` and `SynthesisEngine` before any RFC-0080 code runs; the fourth RFC in this track to depend on this one function without reimplementing it.

## Compatibility

- No DIR, Snapshot, Review Bundle, Evolution Report, Documentation Profile, or Bundle/Manifest format change.
- No CLI wiring, no `DocumentationArtifactKind` entry, no `ProjectSpecificationMarkdownRenderer` change.
- No existing file modified.

## Out of scope

- CLI wiring / `DocumentationArtifactKind` registration for either document type (see Compatibility).
- JSON-structured AI output (see the parser design decision above).
- Per-sentence evidence citation for Executive Summary beyond request-level source grounding (see Honest limitation above) — a future RFC could pursue this if a stronger guarantee is needed.
- RFC-0081/RFC-0082 (Productization Roadmap, AI-Proposed ADR) — separate, later items in this track.

## Verification (executed)

- Implementation: 3 new files under `src/main/kotlin/io/docpilot/core/documentation/advisory/`. No existing file was modified.
- `./gradlew test --tests "io.docpilot.core.documentation.advisory.*"` (root module): **PASS**, 14 tests, 0 failures (`LabeledStatementTest` 5, `KnownIssuesRegisterTest` 4, `ExecutiveSummaryTest` 5).
- `./gradlew test` (full multi-module regression — root, `docpilot-cli`, `docpilot-provider-openai`, `docpilot-provider-ollama`, `docpilot-release`): **PASS**, `BUILD SUCCESSFUL`, 0 failures.

## Acceptance criteria

1. `LabeledStatement` enforces AS-IS-requires-evidence / TO-BE-forbids-evidence by construction. **Met.**
2. Known Issues Register is built deterministically from `Finding`s, most-severe-first, each statement carrying its own Finding's specific `evidenceRefs`. **Met.**
3. Executive Summary is built via `SynthesisEngine`, rejecting the whole document if any output line lacks a valid AS-IS/TO-BE label. **Met.**
4. The per-statement-vs-aggregate evidence-attribution difference between the two document types is explicitly documented, not overstated. **Met.**
5. No DIR, Snapshot, Review Bundle, Evolution Report, Bundle/Manifest, or existing-file changes. **Met.**
6. Full Gradle regression passes with 0 failures. **Met.**
