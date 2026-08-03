# RFC-0084 — AI-Proposed Finding Extraction and Persisted Finding/Curation Registry

Status: `IMPLEMENTED` — Part A (`generate propose-findings`) and Part B (persisted Finding/curation registry) are both implemented and verified. The user explicitly chose to implement Part A first ("A로 갑니다"), then asked to continue with Part B ("RFC-0084 이어서 진행합니다").

Track: Documentation Claims / Findings track (RFC-0077–0082), CLI-layer follow-on to RFC-0083 (CLI Wiring for Findings, Advisory Documents, and Documentation Logging).

Depends on: RFC-0078 (`Finding`/`FindingFactory`), RFC-0079 (`SynthesisEngine`/`SynthesisRequest`/`SynthesisSource`), RFC-0081/RFC-0082 (`DocumentationReviewDecision`/`DocumentationReviewDisposition` reuse), RFC-0083 (`generate findings`, `generate roadmap`, `generate adr-propose`/`adr-adopt`, `FindingsJsonCodec`) — all implemented and, except where noted below, unmodified.

## Problem

RFC-0083 wired Finding validation and Advisory-document generation into the CLI but left two things explicitly out of scope, both confirmed still true by fresh research:

1. **No Finding auto-extraction.** `generate findings` only *validates* a hand-authored JSON file against the real `ProjectSpecification` via `FindingFactory.create`. Nothing in the codebase looks at a `ProjectSpecification` and proposes candidate Findings — `FindingFactory.create` is called only from its own test. The only existing gap/quality-signal producer, `DocumentationQualityValidator` (`src/main/kotlin/io/docpilot/core/validation/DocumentationQualityValidator.kt`), is dead/unwired code about DocPilot's own documentation-catalog completeness (missing renders, coverage percentages), not the target codebase's design/quality, and its checks are largely *absence*-shaped (e.g. "this component has no Evidence"), which cannot itself satisfy `FindingFactory`'s mandatory non-empty `evidenceRefs` — so it is not a usable Finding source as-is. `ProjectSpecification.unresolved` items are similarly evidence-free (confirmed: `UnresolvedItem` has no `evidenceRefs` field) and describe DocPilot's own extraction gaps, not code-quality observations.
2. **No persistence.** `generate roadmap --decisions` and `generate adr-propose` → `generate adr-adopt` are one-shot, caller-managed-file operations. Nothing survives a single invocation inside the project's own state; a curation decision made today cannot be "remembered" the next time `generate roadmap` runs without the human manually re-supplying the same decisions file.

## Decision

**Part A — AI-proposed Finding extraction, human-gated by the existing `generate findings` validator.** Rather than inventing a new "adopt" step, the extractor emits the *same raw candidate JSON shape* `generate findings --input` already consumes (`FindingInput`: subjectStableId/semanticKey/category/severity/summary/evidenceRefs/unresolvedRefs). A human reviews/edits that file, then runs the existing, unmodified `generate findings` to validate it — the AI never bypasses that fail-closed gate, matching this track's "AI proposes, deterministic re-validation decides" discipline used throughout RFC-0079–0082.

**Part B — a persisted Finding/decision registry**, modeled on `FileReviewBundleRepository`'s file-handling shape (JSON + `payloadSha256` + temp-file/decode-validate/atomic-move + optimistic concurrency) — **not** its domain types, which RFC-0081/0082 already established are Markdown-patch-specific and unsuitable for Finding-shaped data (confirmed again: `StoredReviewBundle`/`DocumentationReviewEntry` require `existingMarkdown`/`proposedMarkdown` fields and a specification-diff identity that a standalone Finding registry has no use for). This is new **core-library** code (unlike RFC-0083, which was deliberately CLI-only) because Findings and Decisions are core model types, and every other persisted-state repository in this codebase (`FileSpecificationSnapshotRepository`, `FileReviewBundleRepository`) lives in core beside its models, with the CLI only calling it.

## Part A — `generate propose-findings`

### Extraction request/parse (new core files, package `io.docpilot.core.documentation.finding`, sibling to `.adr`/`.advisory`/`.backlog`/`.synthesis`)

```kotlin
public object FindingProposalRequestBuilder {
    public fun request(sources: List<SynthesisSource>, canonicalFacts: String, providerId: String, model: String,
        documentType: String = "FINDING_PROPOSAL"): SynthesisRequest
}

public data class ProposedFinding(
    public val subjectStableId: String, public val category: String,
    public val severity: FindingSeverity, public val summary: String,
)

public object FindingProposalBuilder {
    public fun build(result: SynthesisResult, allowedSubjectIds: Set<String>): List<ProposedFinding>?
}
```

`FindingProposalRequestBuilder.request` composes with `SynthesisPrompt.render()`'s existing grounding instructions (same pattern as `AdrProposalRequestBuilder`/`ExecutiveSummaryRequestBuilder`) by appending a format instruction: for each subject where a real, evidence-grounded issue exists, output a `### Finding` block followed by exactly four labeled lines — `Subject:` (must be verbatim one of the supplied source artifact ids), `Category:`, `Severity:` (one of `FindingSeverity`'s names), `Summary:`. Zero or more blocks; zero is explicitly valid ("if nothing warrants a Finding, output nothing").

`FindingProposalBuilder.build` is fail-closed like `AiProposedAdrBuilder`: any block missing a required line, any severity that doesn't parse via `FindingSeverity.valueOf`, or any `Subject` not present in `allowedSubjectIds` (guards against a hallucinated/invented subject id) rejects the **whole batch** (`null`) — never a partial result. An empty, well-formed response (no blocks) returns `emptyList()`, which is a valid, non-error outcome. `allowedSubjectIds` is the CLI-supplied set of real subject ids the sources were built from — the AI's own text is never trusted as the source of truth for which subject a Finding is about.

### `generate propose-findings --project <path> --provider <id> --model <model> [--artifact <componentId>]... [--limit N] --output <file>`

1. Builds the real `ProjectSpecification` (same as `generate findings`).
2. Selects target components: `--artifact` (repeatable) if supplied, else the first `--limit` (default 20) components sorted by id that have non-empty `evidenceRefs`. Requires at least 2 selected components (`SynthesisRequest` itself enforces `sources.size >= 2`; this surfaces that constraint with a clear message before attempting a call).
3. Builds one `SynthesisSource` per component using the component's **own** `evidenceRefs` (`ComponentSpecification.evidenceRefs`, already real, already validated data — never AI-supplied), capped at 20 refs per component sent into the prompt.
4. Builds a compact `canonicalFacts` narrative: one line per component with only `id`, `kind`, `name`, and its Evidence **count** (not the full Evidence bullet text). **This is a deliberate, explicit safeguard**, informed by a bug found while smoke-testing RFC-0083 (fixed in a separate commit, tracked as a follow-up in `task_3195c67d`): dumping full raw Evidence descriptions into a prompt for many subjects at once produces requests tens of kilobytes long that derail smaller local models. `propose-findings` never does that — the AI only sees compact facts sufficient to reason about, and the CLI, not the AI, is what attaches real `evidenceRefs` to any resulting candidate.
5. `SynthesisEngine(log.logging(bootstrap.createProvider(providerId))).synthesize(specification, request)` → `FindingProposalBuilder.build(result, allowedSubjectIds = selected component ids)`. `null` (rejected batch) fails the command with `result.record.diagnostic`; an empty list succeeds and writes an empty JSON array with a clear "no candidates proposed" message.
6. For each `ProposedFinding`, builds a `FindingInput`-shaped candidate: `evidenceRefs` = **the selected component's own real evidenceRefs** (not anything the AI wrote), `semanticKey` derived deterministically (`"${category}-${sha256(summary).take(8)}"`, collision-suffixed if needed within one run), `unresolvedRefs` = empty.
7. Writes the array via a new `FindingsJsonCodec.encodeFindingInputs(inputs: List<FindingInput>): String` (RFC-0083's codec only had a decoder for this shape, since `generate findings --input` only ever *read* it before). The output file is directly usable, unedited or after human review, as `generate findings --input <this file> --output <validated.json>` — no new "adopt" command, no bypass of the existing validator.

## Part B — persisted Finding/curation registry

### New core repository (package `io.docpilot.core.specification.finding`, sibling to `Finding.kt`/`FindingFactory.kt`)

```kotlin
public object FindingRegistryFormat { public const val DEFAULT_RELATIVE_PATH: String = ".docpilot/findings/registry.json" }

public class FileFindingRegistryRepository(private val outputRoot: Path) {
    public fun load(projectId: String): FindingRegistryLoadResult   // NotFound | Valid(findings) | Invalid(reason)
    public fun merge(projectId: String, findings: List<Finding>): List<Finding>   // union by Finding.id, existing wins on exact-id collision (Finding is a value; same id ⇒ same content by construction)
}
```

and, reusing `DocumentationReviewDecision`/`DocumentationReviewDisposition` unmodified as the decision shape (same pattern as every prior RFC in this track):

```kotlin
public object CurationDecisionRegistryFormat { public const val DEFAULT_RELATIVE_PATH: String = ".docpilot/findings/decisions.json" }

public class FileCurationDecisionRegistryRepository(private val outputRoot: Path) {
    public fun load(projectId: String): CurationDecisionRegistryLoadResult
    public fun merge(projectId: String, decisions: List<DocumentationReviewDecision>): List<DocumentationReviewDecision>   // keyed by targetId; a new decision for an existing targetId overwrites (last write wins) — humans revise curation calls; this is not treated as a fail-closed conflict
}
```

Both mirror `FileReviewBundleRepository`'s file-handling shape: write to a temp file, decode-and-validate it back, atomic move (`ATOMIC_MOVE` falling back to plain replace, matching `FileSpecificationSnapshotRepository`), a stored `payloadSha256` for tamper/corruption detection on load. One JSON file per project id, both under `.docpilot/findings/` relative to the CLI's `--output` root — mirroring where `generate docs` already keeps its own manifest/bundle (`.docpilot/documentation-ownership.manifest`, `.docpilot/documentation-bundle.json`) rather than inside the analyzed project's source tree.

Explicitly **not** reused: `DocumentationBundleCodec`/`DocumentationBundleFormat` (assessed and rejected — hand-rolled regex parsing hardcodes `mediaType":"text/markdown"` into its artifact format, is `internal` to `docpilot-cli`'s `command` package creating a core→CLI dependency-direction problem, and folding a third record type into its flat SHA-256 chain is mechanically possible but brittle, as the existing `enrichments` extension already shows).

### CLI wiring

- `generate findings --output <file> [--project <path> ...]` — **unchanged behavior for `--output`** (still writes the explicit file), plus now **also** merges the newly-validated findings into `<output-root>/.docpilot/findings/registry.json` via `FileFindingRegistryRepository.merge`, where `<output-root>` is the directory containing `--output`'s file. This is additive persistence, not a replacement for the explicit output file.
- `generate known-issues` / `generate roadmap` / `generate executive-summary` / `generate adr-propose` gain `--findings-registry <output-root>` as an alternative to `--findings <file>` (mutually exclusive, exactly one required) — loads the currently-persisted registry for that output root instead of requiring an explicit file path.
- `generate roadmap` gains `--decisions-registry <output-root>`: loads previously-persisted decisions for that output root, merges any new ones from `--decisions <file>` (if also supplied) on top, applies curation with the merged set, and persists the merged set back — so a decision made in one invocation is automatically honored by the next, without re-supplying it.
- `generate adr-adopt` gains the same `--decisions-registry <output-root>` (optional): if supplied, the accept/reject decision this command records is **also** persisted into the same decisions registry (`targetId = proposal.proposalId`, no collision risk with roadmap's `targetId = Finding.id.value` in practice — the two id schemes have visibly distinct prefixes, `finding:` vs `documentation-synthesis:`). This unifies roadmap curation and ADR curation into one durable audit trail without inventing a second decision store.

## Compatibility

- No existing `--findings`/`--decisions` file-based flag is removed or changed; the registry flags are purely additive alternatives.
- No change to `Finding`, `FindingFactory`, `SynthesisEngine`, `SynthesisRequest`, `DocumentationReviewDecision`, `ProductizationRoadmapCurator`, `AiProposedAdrAdoption`, or any RFC-0078–0083 rendering/building code.
- `DocumentationBundleCodec`/`DocumentationBundleFormat`/`generate docs` are untouched.
- New `.docpilot/findings/` directory is independent of `.docpilot/documentation-ownership.manifest`, `.docpilot/documentation-bundle.json`, and `.docpilot/snapshots/` — no shared file, no format coupling.

## Part A — verification (executed)

**Implementation note**: `--artifact` is implemented as a single comma-separated value (`--artifact "id1,id2"`), not a repeatable flag — `CliArguments` (used throughout `FindingCommands`, shared with `generate findings`/`roadmap`/etc.) has no repeated-flag support, and extending it would have widened this change's blast radius across every command using it for no benefit specific to this RFC.

**Files added**:
- `src/main/kotlin/io/docpilot/core/documentation/finding/FindingProposal.kt` — `FindingProposalRequestBuilder`, `ProposedFinding`, `FindingProposalBuilder` (core, package `io.docpilot.core.documentation.finding`).
- `src/test/kotlin/io/docpilot/core/documentation/finding/FindingProposalTest.kt` — 13 tests covering the fail-closed parser (multi-block, any-order fields, missing/duplicate/extra lines, invalid severity, hallucinated subject rejection, empty-response-is-valid).

**Files modified**:
- `docpilot-cli/src/main/kotlin/io/docpilot/cli/command/finding/FindingsJsonCodec.kt` — added `encodeFindingInputs` (the decoder already existed from RFC-0083; only the encoder was missing).
- `docpilot-cli/src/main/kotlin/io/docpilot/cli/command/finding/FindingCommands.kt` — added `proposeFindings`.
- `docpilot-cli/src/main/kotlin/io/docpilot/cli/command/GenerateCommand.kt`, `docpilot-cli/src/main/kotlin/io/docpilot/cli/Main.kt` — dispatch + usage string.
- `docpilot-cli/src/test/kotlin/io/docpilot/cli/command/finding/FindingCommandsTest.kt` — 4 new tests (synthesis-rejection surfacing, unknown `--artifact` id, non-positive `--limit`, fewer-than-two-components).

**Test results**: `./gradlew test` (full multi-module) — **PASS**, `BUILD SUCCESSFUL`, 549 tests total (461 root + 52 `docpilot-cli` + 5 + 16 + 15), 0 failures, 0 errors, 0 skipped.

**Real-Ollama smoke test** (isolated `architecture-samples` copy, `qwen3.5:9b`, unspecified `--artifact` so the default `--limit 5` component-selection path was exercised): the resulting prompt was 9,580 characters (vs. the 40KB+ that motivated this RFC's compactness design) and the model proposed 2 well-formed candidates, each with real `evidenceRefs` (never AI-authored text). The output file was fed directly, unedited, into the existing `generate findings --input` and passed validation — closing the loop end-to-end with a real model on a real project, exactly as designed.

## Part B — verification (executed)

**Implementation note**: `known-issues`/`roadmap`/`executive-summary`/`adr-propose` need a project id to key the registry even when they don't otherwise need a full `ProjectSpecification` (`known-issues`/`roadmap` never analyzed the project before this RFC). Rather than forcing a full source-scanning analysis just to read one field, a small `projectId(project: Path): String = LocalProjectLoader().load(project).name.lowercase()` helper was added, deliberately matching `ProjectKnowledgeLoader.analyze()`'s own internal derivation (`ProjectDescriptor.id = project.name.lowercase()`) exactly — `LocalProjectLoader` is cheap (resolves the path/name/git-repository flag only, no source scanning), so the same project always maps to the same registry key everywhere, without an expensive redundant analysis pass.

**Files added**:
- `src/main/kotlin/io/docpilot/core/specification/finding/FindingRegistry.kt` — `FindingRegistryFormat`, `StoredFindingRegistry`, `JsonFindingRegistryCodec`, `FileFindingRegistryRepository`.
- `src/main/kotlin/io/docpilot/core/specification/finding/CurationDecisionRegistry.kt` — the parallel set for `DocumentationReviewDecision`.
- `src/test/kotlin/io/docpilot/core/specification/finding/FindingRegistryTest.kt` (6 tests) and `CurationDecisionRegistryTest.kt` (5 tests) — round-trip, dedup-by-id / last-write-wins merge semantics, project-id-mismatch and tampered-payload fail-closed detection, and confirmation that roadmap (`finding:`) and ADR (`documentation-synthesis:`) target-id prefixes coexist in one decisions store without collision.

**Files modified**:
- `docpilot-cli/src/main/kotlin/io/docpilot/cli/command/finding/FindingCommands.kt` — `findings` merges into the registry after writing `--output`; `knownIssues`/`roadmap`/`executiveSummary`/`adrPropose` route through a new `resolveFindings` helper (`--findings` XOR `--findings-registry`, exactly one required — existing `--findings <file>` behavior is unchanged, verified by the full pre-existing test suite passing unmodified); `roadmap`/`adrAdopt` gained `--decisions-registry`.
- `docpilot-cli/src/main/kotlin/io/docpilot/cli/Main.kt` — usage string.
- `docpilot-cli/src/test/kotlin/io/docpilot/cli/command/finding/FindingCommandsTest.kt` — 5 new tests: registry accumulation across two `generate findings` runs, `known-issues --findings-registry`, the `--findings`/`--findings-registry` mutual-exclusivity check, a two-invocation `roadmap --decisions-registry` proving a decision made in the first run is honored in the second without re-supplying `--decisions`, and `adr-adopt --decisions-registry` persistence.

**Test results**: `./gradlew test` (full multi-module) — **PASS**, `BUILD SUCCESSFUL`, 565 tests total (472 root + 57 `docpilot-cli` + 5 + 16 + 15), 0 failures, 0 errors, 0 skipped.

**Real-project smoke test** (same isolated `architecture-samples` copy used for Part A, no AI calls needed since Part B is deterministic): `generate findings` against the real AI-proposed candidates from the Part A smoke test persisted 2 Findings to `.docpilot/findings/registry.json`; re-running with the same candidates left the registry at 2 (dedup confirmed, not 4). `generate known-issues --findings-registry` rendered correctly from the persisted set. `generate roadmap --decisions <file> --decisions-registry <dir>` recorded one ACCEPTED decision; a **second, separate invocation** of `generate roadmap --decisions-registry <dir>` with **no `--decisions` flag at all** correctly rendered the same Finding as Adopted — proving a curation decision survives across CLI invocations, closing the exact gap RFC-0083 originally flagged as out of scope.

## Out of scope

- Any change to how `SynthesisEngine`'s existing `evidenceRefs`/`canonicalNarrative` size is bounded for `executive-summary`/`adr-propose` (the oversized-prompt issue found during RFC-0083's smoke test is tracked separately as `task_3195c67d`; `propose-findings`'s own prompt-compactness safeguard in this RFC does not retroactively fix that).
- Automatic component selection heuristics beyond "has non-empty evidenceRefs, sorted by id, capped at `--limit`" — no ranking by risk, size, or churn.
- Conflict resolution beyond "last write wins" for decisions — no versioned history, no multi-user concurrent-edit story beyond the existing `payloadSha256` optimistic-concurrency check inherited from the `FileReviewBundleRepository` pattern.
- Deleting or expiring registry entries (no `generate findings --remove`); the registry only ever grows via merge.

## Acceptance criteria

Part A:

1. `generate propose-findings` produces a `FindingInput`-shaped JSON file directly consumable, unedited, by `generate findings --input`. **Met** — verified by the real-Ollama smoke test above.
2. `FindingProposalBuilder.build` rejects the whole batch (never a partial result) on any malformed block, invalid severity, or hallucinated subject id not in `allowedSubjectIds`; an empty, well-formed response is a valid, non-error outcome. **Met** — `FindingProposalTest`.
3. Every candidate's `evidenceRefs` in the output file is the CLI's own real component evidence, never AI-authored text — verifiable by construction (the AI response is never used as a source of evidenceRefs anywhere in the pipeline). **Met**.
4. The `canonicalFacts` narrative sent to the provider contains no full raw Evidence bullet text — only id/kind/name/count per component. **Met** — the real-Ollama smoke test's request was 9,580 characters, not tens of kilobytes.

Part B:

5. `generate findings` merges newly-validated Findings into the persisted registry without duplicating an already-present `Finding.id`. **Met** — `FindingRegistryTest`, plus the real-project smoke test (2 candidates persisted, re-run stayed at 2).
6. `generate roadmap --decisions-registry` and `generate adr-adopt --decisions-registry` both read-merge-apply-persist against the same decisions store, and a decision made via one command is visible to a later invocation of either. **Met for roadmap** (two-invocation test + real-project smoke test) — `adr-adopt`'s persistence side is covered by `FindingCommandsTest`'s `adr-adopt --decisions-registry persists the decision` test; a cross-command (roadmap-decides-then-adr-adopt-reads, or vice versa) test was not separately written since both write through the identical `FileCurationDecisionRegistryRepository.merge`, already proven correct by `CurationDecisionRegistryTest`'s prefix-coexistence test.

Both parts:

7. No file under `src/main/kotlin/io/docpilot/core/documentation/{adr,advisory,backlog,synthesis}/` or `src/main/kotlin/io/docpilot/core/specification/finding/{Finding,FindingFactory}.kt` is modified. **Met** — Part B added new sibling files in the same package but did not modify either existing file.
8. Full `./gradlew test` (all modules) passes with 0 failures. **Met** — 565 tests, 0 failures.
