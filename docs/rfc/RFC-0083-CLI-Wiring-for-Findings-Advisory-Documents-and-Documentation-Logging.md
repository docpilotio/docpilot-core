# RFC-0083 — CLI Wiring for Findings, Advisory Documents, and Documentation Logging

Status: `IMPLEMENTED`

Track: Documentation Claims / Findings track (RFC-0077–0082) — this RFC is a follow-on, CLI-layer item after the track's "TRACK COMPLETE" milestone (RFC-0082). Unlike RFC-0078–0082, which were explicitly and deliberately core-library-only, this RFC's entire purpose is the CLI wiring those RFCs each named as their own "Out of scope."

Depends on: RFC-0072 (Claim/Evidence binding), RFC-0078 (`Finding`/`FindingFactory`), RFC-0079 (`SynthesisEngine`/`SynthesisRequest`/Advisory tier), RFC-0080 (`KnownIssuesRegisterBuilder`, `ExecutiveSummaryBuilder`), RFC-0081 (`ProductizationRoadmapBuilder`, `ProductizationRoadmapCurator`), RFC-0082 (`AiProposedAdrBuilder`, `AiProposedAdrAdoption`) — all implemented and unmodified by this RFC.

## Problem

Verified against the actual code (not just prior memory): grepping `docpilot-cli/src/main` for `synthesis|finding|executive|adr-propose|known-issue|productization` returns zero matches. `docpilot generate docs` — with any provider, with or without `--enrich` — can only ever produce the RFC-0077 five documents plus the pre-existing deterministic catalog plus the one-paragraph-per-artifact `--enrich` narrative. It structurally cannot produce a Finding, an Executive Summary, a Known Issues Register, a Productization Roadmap, or an AI-Proposed ADR, no matter how it is invoked, because nothing in the CLI calls any of that code.

Separately, `generate docs` has no logging at all. `GenerateCommand.architecture()`/`.adr()`/`.specification()` each create a `ProjectLogSession` and route their AI calls through `log.logging(provider)`; `GenerateCommand.documentation()` does neither — no log session, and today's existing `--enrich` AI calls in `docs` are not logged the way `architecture`/`adr` AI calls are.

## Two things this RFC deliberately does NOT try to do

1. **It does not integrate the six new capabilities into `generate docs`'s Bundle/Manifest/Snapshot/`DocumentationArtifactKind` catalog pipeline.** RFC-0079 and RFC-0080 each explicitly deferred this ("avoiding the Bundle/Manifest format-version question") and nothing in this RFC's research changed that calculus — `DocumentRenderer`/`ProjectSpecificationMarkdownRenderer`'s `DocumentationArtifactKind` dispatch has no entries for any of the six capabilities, and only AI-Proposed ADR adoption even produces a `Document` at all (the other four produce their own dedicated Markdown strings). Retrofitting all of this into the catalog/incremental-diff machinery is a substantially larger, format-version-sensitive change than "wire up the CLI," and is out of scope here. Instead, this RFC adds standalone `generate <noun>` subcommands, matching the existing `generate architecture` / `generate adr` pattern (single input → single rendered output), not the `generate docs` pattern.
2. **It does not invent a Finding-extraction algorithm.** Research confirms `FindingFactory.create` is called only from its own test file — there is no deterministic or AI-driven pipeline anywhere in the codebase that looks at a `ProjectSpecification` and proposes candidate Findings. Inventing one (what subjects to inspect, what counts as a "finding," what severity heuristic to use) is a substantial, independent design problem, not a wiring task, and doing it inside a CLI-wiring RFC would bury a real design decision inside a plumbing change. This RFC instead defines a CLI-level *input contract* for supplying Findings — a human- or tool-authored JSON file — the same way `generate adr` today takes human-supplied `title`/`context`/`decision`/`consequences`/`alternatives` as text rather than inventing them. A future RFC (out of scope here) can add an extraction command that *produces* this same JSON file; this RFC only defines and consumes the format.

## Scope

| Concept | New/changed CLI surface | Reuses (unmodified) |
|---|---|---|
| Logging for `generate docs` | `GenerateCommand.documentation()` gains a `ProjectLogSession`, matching `architecture()`/`adr()` | `ProjectLogSession`, `CliBootstrap` |
| Findings input contract | `generate findings --project <path> --input <findings.json> --output <validated-findings.json>` | `FindingFactory`, `DefaultSpecificationBuilder` |
| Known Issues Register | `generate known-issues --project <path> --findings <findings.json> --output <file>` | `KnownIssuesRegisterBuilder`, `KnownIssuesRegisterMarkdownRenderer` |
| Productization Roadmap + curation | `generate roadmap --project <path> --findings <findings.json> --output <file> [--decisions <decisions.json>]` | `ProductizationRoadmapBuilder`, `ProductizationRoadmapCurator`, `..MarkdownRenderer` |
| Executive Summary | `generate executive-summary --project <path> --findings <findings.json> --provider <id> --model <model> --output <file>` | `ExecutiveSummaryRequestBuilder`, `SynthesisEngine`, `ExecutiveSummaryBuilder`, `..MarkdownRenderer` |
| AI-Proposed ADR — draft | `generate adr-propose --project <path> --findings <findings.json> --provider <id> --model <model> --output <proposal.json>` | `AdrProposalRequestBuilder`, `SynthesisEngine`, `AiProposedAdrBuilder` |
| AI-Proposed ADR — adopt/reject | `generate adr-adopt --proposal <proposal.json> --decision accept\|reject [--comment <text>] --output <file>` | `AiProposedAdrAdoption`, `DocumentRenderer` |

All new code lives under `docpilot-cli/src/main/kotlin/io/docpilot/cli/command/`, as sibling files to `GenerateCommand.kt` (e.g. `FindingsCommand.kt`, `AdvisoryDocumentCommands.kt`, `AdrProposalCommands.kt`, plus a shared `FindingsJsonCodec.kt`). No file under `src/main/kotlin/io/docpilot/core/` is modified.

## Command design

### `generate docs` — logging fix (no new flag)

**Implementation correction from the original draft above**: the logging was placed inside `DefaultDocumentationGenerationWorkflow.execute()` (`docpilot-cli/.../command/DocumentationGenerationWorkflow.kt`) rather than in `GenerateCommand.documentation()`. This is a smaller, more localized change than originally planned and doesn't touch `GenerateCommand.kt`'s constructor wiring at all: `execute()` now resolves `project` first, creates `ProjectLogSession.create(project)` (or `null` if the path isn't a directory — matching the existing `require(Files.isDirectory(project))` failure mode, which now fires before any log write is attempted), logs a start line, delegates to `prepareAndExecute(options, log)`, and logs a completion line with the resulting `status`. Inside `prepareAndExecute`, the `--enrich` path wraps the resolved provider with `log?.logging(provider) ?: provider` before constructing `DocumentationEnrichmentEngine`, so enrichment AI calls are logged exactly like `architecture`/`adr` AI calls (`<project>/docpilot/log/<timestamp>/ai-NNN-{prompt,response}.txt` + `operations.log`).

This placement is safe for the existing test suite: `GenerateDocsCommandTest`'s tests inject a `DocumentationGenerationWorkflow` lambda directly into `GenerateCommand`, bypassing `DefaultDocumentationGenerationWorkflow` entirely, so none of them exercise the new logging code and none needed modification. `docpilot-cli/docpilot/` is already gitignored, consistent with `specification()`'s pre-existing use of `ProjectLogSession.create(Path.of("."))` in tests that pass `--project "."`.

### Findings input contract

**Input file** (`--input`, hand-authored or produced by a future extraction tool), a JSON array, one object per candidate Finding:

```json
[
  {
    "subjectStableId": "component:LoginViewModel",
    "semanticKey": "missing-error-state",
    "category": "reliability",
    "severity": "HIGH",
    "summary": "Login failure does not surface an error state to the user.",
    "evidenceRefs": ["evidence:e042"],
    "unresolvedRefs": []
  }
]
```

`generate findings` builds the project's `ProjectSpecification` the same way `generate docs` does (`ProjectKnowledgeLoader` + `DefaultSpecificationBuilder`), then calls `FindingFactory.create(specification, ...)` for every entry, **fail-closed for the whole batch**: if any single entry fails `FindingFactory`'s validation (unresolvable `evidenceRefs`, AI-authority rejection, blank field), the command fails with that entry's exact error and index, and writes nothing — this mirrors `SynthesisRequest`'s and every prior RFC's "no partial result" posture, rather than silently dropping bad entries. On success, writes the **output file**: a JSON array of the now-validated `Finding` objects (including the computed `id`), in the same shape `KnownIssuesRegisterBuilder`/`ProductizationRoadmapBuilder`/the Synthesis-source derivation below all consume as `--findings`. This output file is the one artifact every other new command in this RFC reads.

### Known Issues Register / Productization Roadmap (deterministic)

Both load the `--findings` JSON (list of already-validated `Finding`), then call their existing builder + renderer unchanged. `generate roadmap` additionally accepts an optional `--decisions <file>` — a JSON array of `{"targetId": "...", "disposition": "ACCEPTED"|"REJECTED", "comment": "..."}`, decoded into `DocumentationReviewDecision`/`DocumentationReviewDisposition` (reused as-is). When `--decisions` is supplied, the command calls `ProductizationRoadmapCurator.apply(document, decisions)` and renders via `renderCuration(...)` instead of `render(...)`. Both commands create a `ProjectLogSession` and log start/end, though neither calls an AI provider.

### Executive Summary / AI-Proposed ADR — draft (AI-calling)

Both need `List<SynthesisSource>`, not `List<Finding>` directly (confirmed: `ExecutiveSummaryRequestBuilder`/`AdrProposalRequestBuilder` take `sources: List<SynthesisSource>`). Rather than inventing a second input file format, this RFC derives one `SynthesisSource` per supplied `Finding`, deterministically:

```kotlin
SynthesisSource(
    artifactId = finding.subjectStableId,
    sourceKind = finding.category,
    sourceModelStableIds = listOf(finding.subjectStableId),
    evidenceRefs = finding.evidenceRefs.toList(),
    unresolvedRefs = finding.unresolvedRefs.toList(),
)
```

deduplicated by `artifactId` (`SynthesisRequest` requires unique artifact ids across sources; two Findings about the same subject collapse into one source, unioning their evidence). Because `SynthesisRequest` requires `sources.size >= 2`, both commands require **at least two distinct-subject Findings** in the input file and fail with that exact message (surfaced from `SynthesisRequest`'s own `require`) otherwise — this is an honest, existing constraint, not a new one this RFC invents. `canonicalNarrative` is built deterministically from the Findings: each Finding's `severity`, `category`, and `summary`, one per line, sorted by `id` — this is what `SynthesisPrompt.render()` embeds verbatim into the provider prompt.

The AI provider is obtained via `log.logging(bootstrap.createProvider(providerId))` — the same primitive `CliBootstrap.create(providerId, logSession)` uses internally — so `SynthesisEngine`'s AI calls are logged identically to `architecture`/`adr`/`docs`.

- `generate executive-summary`: `ExecutiveSummaryRequestBuilder.request(sources, canonicalNarrative, providerId, model)` → `SynthesisEngine(loggedProvider).synthesize(specification, request)` → `ExecutiveSummaryBuilder.build(result)`. If the builder returns `null` (provider `FAILURE`/`REJECTED`/malformed AS-IS/TO-BE lines), the command fails with `result.record.diagnostic` — no partial output is written. On success, renders via `ExecutiveSummaryMarkdownRenderer.render(document)` to `--output`.
- `generate adr-propose`: identical shape via `AdrProposalRequestBuilder`/`AiProposedAdrBuilder`. On success, writes the **full proposal** — `proposalId`, all five sections, `citedFindingIds`, and the `SynthesisRecord` fields needed to reconstruct a `DocumentationReviewDecision` later — to `--output` as JSON (not yet a rendered `Document`; adoption is a separate, human-gated step below).

**Why the proposal is persisted to a file instead of only printed**: `SynthesisRecord.synthesisStableId` (→ `proposalId`) is a deterministic hash of the request (provider, model, document type, canonical input, prompt identity, sorted artifact ids) — it does **not** depend on the AI's response content. If `adr-adopt` re-invoked the AI to regenerate the draft from the same inputs, it could reproduce the same `proposalId` but a *different* five-section body (most providers are not exactly deterministic even at `temperature=0.0`), silently adopting text the human never actually read. Persisting the drafted proposal verbatim and having `adr-adopt` read it back — no second AI call — closes that gap.

### AI-Proposed ADR — adopt/reject

`generate adr-adopt --proposal <proposal.json> --decision accept|reject [--comment <text>] --output <file>` reads the persisted `AiProposedAdr` back, builds `DocumentationReviewDecision(targetId = proposal.proposalId, disposition = if (decision == "accept") ACCEPTED else REJECTED, comment)`, and:
- `--decision reject`: prints "Proposal rejected; no document produced." and exits `0`. `AiProposedAdrAdoption.adopt` is never called (it structurally requires `ACCEPTED`).
- `--decision accept`: calls `AiProposedAdrAdoption.adopt(proposal, decision)` → `Document`, renders via the existing, unmodified `DocumentRenderer().render(document)` to `--output`. No AI call in this command at all; a `ProjectLogSession` is still created for consistency and audit-trail completeness (records the human decision and file paths).

## New JSON codecs (CLI-owned)

The codebase has no JSON parsing library anywhere (`JsonSpecificationSnapshotCodec` in core hand-rolls a minimal recursive-descent `JsonParser`/`JsonValue` pair, kept `private` to that file). This RFC adds an equivalent, independent hand-rolled parser scoped to `docpilot-cli` (`FindingsJsonCodec.kt`), following the exact same style (`JsonValue` sealed interface, `requiredString`/`optionalString`/`stringSet`/`requiredArray` extensions) — not reused from core (it's `private` there) and not a new external dependency. It encodes/decodes exactly four shapes: `Finding` list, `DocumentationReviewDecision` list, `AiProposedAdr`, and `SynthesisRecord` (embedded in the proposal file). No changes to `JsonSpecificationSnapshotCodec` or any other core JSON handling.

## Usage string additions (`Main.kt`)

```
docpilot generate findings --project <path> --input <file> --output <file>
docpilot generate known-issues --project <path> --findings <file> --output <file>
docpilot generate roadmap --project <path> --findings <file> --output <file> [--decisions <file>]
docpilot generate executive-summary --project <path> --findings <file> --provider <id> --model <model> --output <file>
docpilot generate adr-propose --project <path> --findings <file> --provider <id> --model <model> --output <file>
docpilot generate adr-adopt --proposal <file> --decision accept|reject [--comment <text>] --output <file>
```

## Compatibility

- No change to any `src/main/kotlin/io/docpilot/core/` file — all six RFC-0078–0082 capabilities remain exactly as implemented.
- No change to `generate docs`'s Bundle/Manifest/Snapshot format, `DocumentationArtifactKind`, or catalog behavior — only its logging wiring changes.
- No change to `generate architecture`/`generate adr`/`generate specification`/`review *`/`reconcile *`/`bundle verify`.
- Every new command is additive; no existing CLI flag or exit-code contract changes.

## Out of scope

- A Finding-extraction command that inspects a `ProjectSpecification` and proposes candidate Findings automatically — this RFC only defines and validates the input contract (`generate findings`'s `--input` format) a future RFC's extractor would target.
- Integrating any of the six capabilities into `generate docs`'s catalog/Bundle/Manifest pipeline.
- Persisting curation decisions or proposals inside the project's own state (`.docpilot/`, snapshots, manifest) — the `--output`/`--proposal`/`--decisions` files this RFC introduces are plain, caller-managed files, not part of any tracked/verified bundle.
- A `review roadmap`/`review adr-propose` subcommand under the existing `ReviewCommand` — confirmed in prior research that `ReviewLifecycleApplyWorkflow`/`StoredReviewBundle` are hard-wired to specification-diff/Markdown-patch semantics that don't fit Finding-shaped data, so this RFC keeps decision application inside `generate roadmap --decisions` / `generate adr-adopt` rather than forcing a fit.

## Acceptance criteria

1. `generate docs` creates a `ProjectLogSession` and logs start/end plus every AI call (including `--enrich`), matching `architecture`/`adr`. **Met** — verified by `DefaultDocumentationGenerationWorkflowLoggingTest`.
2. `generate findings` validates a human-authored JSON input fail-closed against the real `ProjectSpecification` via unmodified `FindingFactory`, and only ever writes a complete, fully-valid output file (never partial). **Met** — verified by `FindingCommandsTest` against a real analyzed temp Kotlin project, including the invalid-evidence-reference rejection path.
3. `generate known-issues` and `generate roadmap` produce the same Markdown a direct core-library call to `KnownIssuesRegisterBuilder`/`ProductizationRoadmapBuilder` would produce, byte-for-byte. **Met** — both call the unmodified builders/renderers directly with no CLI-side transformation of their output.
4. `generate roadmap --decisions` produces the same curated Markdown a direct call to `ProductizationRoadmapCurator.apply` + `renderCuration` would produce. **Met** — verified by the curation test.
5. `generate executive-summary` and `generate adr-propose` route their AI calls through `ProjectLogSession.logging`, and fail with the underlying diagnostic (not a generic error) when `SynthesisEngine` returns `FAILED`/`FALLBACK`/`REJECTED`. **Met** — verified by the Synthesis-rejection test using the `fixture` provider.
6. `generate adr-propose` followed by `generate adr-adopt --decision accept` on the same proposal file never re-invokes the AI provider, and the adopted `Document` renders via the unmodified `DocumentRenderer`. **Met** — `adr-adopt` only ever reads the persisted proposal file; it has no `AiProvider` dependency at all.
7. `generate adr-adopt --decision reject` never calls `AiProposedAdrAdoption.adopt`. **Met** — the reject branch returns before `adopt()` is reached.
8. No file under `src/main/kotlin/io/docpilot/core/` is modified. **Met** — confirmed by the root module's 448 tests running `UP-TO-DATE`.
9. Full `./gradlew test` (all modules) passes with 0 failures. **Met** — 530 tests, 0 failures, 0 errors.

## Verification (executed)

**Files added** (all under `docpilot-cli`, no `src/main/kotlin/io/docpilot/core/` file touched):
- `src/main/kotlin/io/docpilot/cli/command/finding/FindingsJsonCodec.kt` — independent hand-rolled JSON parser/codec for `FindingInput`, `Finding`, `DocumentationReviewDecision`, and `AiProposedAdr` (with embedded `SynthesisRecord`).
- `src/main/kotlin/io/docpilot/cli/command/finding/FindingCommands.kt` — `findings`/`knownIssues`/`roadmap`/`executiveSummary`/`adrPropose`/`adrAdopt`.
- `src/test/kotlin/io/docpilot/cli/command/finding/FindingsJsonCodecTest.kt` — 7 tests, round-trips for all four JSON shapes plus malformed-input rejection.
- `src/test/kotlin/io/docpilot/cli/command/finding/FindingCommandsTest.kt` — 7 tests, including fail-closed evidence validation against a real analyzed temp Kotlin project, deterministic Known Issues Register/Roadmap rendering, roadmap curation, a Synthesis-rejection path (via the `fixture` provider, whose canned response doesn't match the required AS-IS/TO-BE format — proving the command surfaces the diagnostic rather than writing partial output), ADR proposal adoption/rejection, and one `GenerateCommand`-level dispatch test.
- `src/test/kotlin/io/docpilot/cli/command/DefaultDocumentationGenerationWorkflowLoggingTest.kt` — 2 tests confirming `generate docs --enrich` writes `operations.log` start/completion lines and `ai-NNN-{prompt,response}.txt` payload files, and that no log directory is created when the project path is invalid.

**Files modified**:
- `src/main/kotlin/io/docpilot/cli/command/DocumentationGenerationWorkflow.kt` — logging (see correction above).
- `src/main/kotlin/io/docpilot/cli/command/GenerateCommand.kt` — new `findingCommands` field and six dispatch cases.
- `src/main/kotlin/io/docpilot/cli/Main.kt` — usage string additions.

**Test results**:
- `./gradlew :docpilot-cli:test --tests "io.docpilot.cli.command.finding.*" --tests "io.docpilot.cli.command.DefaultDocumentationGenerationWorkflowLoggingTest"`: **PASS**, 16 tests, 0 failures.
- `./gradlew test` (full multi-module regression — root, `docpilot-cli`, `docpilot-provider-openai`, `docpilot-provider-ollama`, `docpilot-release`): **PASS**, `BUILD SUCCESSFUL`, 530 tests total (448 root + 46 `docpilot-cli` + 5 + 16 + 15), 0 failures, 0 errors, 0 skipped.
- Root module's 448 tests ran `UP-TO-DATE` (unchanged since the last run), confirming no `src/main/kotlin/io/docpilot/core/` file was touched by this RFC.
