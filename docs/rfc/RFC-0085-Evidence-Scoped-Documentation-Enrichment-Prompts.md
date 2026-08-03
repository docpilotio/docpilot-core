# RFC-0085 — Evidence-Scoped Documentation Enrichment Prompts

Status: `IMPLEMENTED`

Track: Follows RFC-0070 (Structured AI Documentation Enrichment), RFC-0077 (Kotlin/Android profile document coverage), and RFC-0083 (CLI wiring). Discovered while smoke-testing `generate docs --enrich` against a real project (`architecture-samples`) with a local Ollama model.

Depends on: RFC-0070 (implemented) — this RFC does not change RFC-0070's contract (managed sections, allowed sections, rejection rules); it fixes how two pieces of RFC-0070's own machinery are populated. Also builds on commit `6e13bb2` (already on `main`), which fixed a related but distinct leak in the same code path — see "Prior, already-fixed bug" below.

## Problem

Running `generate docs --enrich` against `architecture-samples` produced `PROJECT_OVERVIEW` enrichment that silently did nothing (`status=KEEP`, no error surfaced). The underlying AI call returned `finishReason=LENGTH` and was rejected by `DocumentationEnrichmentEngine.validate()` after being truncated mid-sentence. The root cause is prompt bloat, traced to two remaining independent bugs (a third, related bug was already found and fixed in the same investigation — see below):

**1. `project.md` duplicates the entire Evidence catalog by renderer omission.**
`ProjectSpecificationMarkdownRenderer.renderDescriptor()` builds every scoped artifact (`MODULE`, `PACKAGE`, `COMPONENT`, `RELATIONSHIP`) by clearing `evidence`/`unresolved` from the `ProjectSpecification` copy before rendering — except `PROJECT_OVERVIEW`, which clears only `modules`/`packages`/`components`. Since `buildMarkdown()` unconditionally appends the full `specification.evidence` list at the end, `project.md` embeds the project's *entire* Evidence catalog (1195 items on `architecture-samples`) — identical content to the dedicated `evidence.md` artifact (`DocumentationArtifactKind.EVIDENCE`), which `project.md` already declares as a `dependencyArtifactIds` entry for exactly this content. This contradicts the pattern RFC-0077's own profile documents established (e.g. Test Strategy scopes to `evidence` filtered by `EvidenceSubject.TEST`, not the whole catalog).

**2. `markdownItems()` extracts full descriptive bullets instead of stable IDs.**
```kotlin
private fun markdownItems(content: String, heading: String): List<String> {
    val match = Regex("(?ms)^## ${Regex.escape(heading)}\\s*\\n(.*?)(?=^## |\\z)").find(content) ?: return emptyList()
    return Regex("(?m)^-\\s+(.+)$").findAll(match.groupValues[1]).map { it.groupValues[1].trim() }
        .filter { it != "None" }.take(50).toList()
}
```
`DocumentationEnrichmentTarget.evidenceRefs`/`.unresolvedRefs` are named and treated (per RFC-0070) as protected *references* — the engine's `validate()` rejects a narrative that restates one verbatim. But this function captures the entire rendered bullet: `- \`evidence:9f8a...\` — <summary>; <type>/<confidence>; <file>, lines <n>, symbol \`<signature>\``. Sending up to 50 of these (per section, so up to 100 with Unresolved) as "facts" produces a large, densely-formatted block that a small local model can misread as directives rather than reference data. Across `ProjectSpecificationMarkdownRenderer.appendEvidence`/`buildFeatureDetail` and `ContractDocumentationMarkdownRenderer.renderEvidence`/`renderUnresolved`, the stable ID is consistently the first backtick-fenced token immediately after `"- "` — every enrichment-reachable renderer follows this shape, so a single extraction rule covers all of them.

Bug 1 is `PROJECT_OVERVIEW`-specific and is the dominant contributor (1195 items vs. a handful for scoped documents). Bug 2 applies to every enrichment-eligible document type and compounds bug 1's effect rather than causing it outright.

### Prior, already-fixed bug (context, not part of this RFC's scope)

A third bug in the same area — `canonicalNarrative()` filtering only the `"## Evidence"`/`"## Unresolved"` *heading line*, not the section body that follows it, so Unresolved `requiredAction` text and Evidence bullets leaked into the "canonical facts" block ahead of the actual narrative — was found in the same investigation and already fixed and merged to `main` as commit `6e13bb2` (`fix(cli): stop canonicalNarrative from leaking Evidence/Unresolved section bodies into enrichment prompts`), regression-tested in `CanonicalNarrativeTest.kt`. This RFC does not touch `canonicalNarrative()` again.

## Decision

Fix the two remaining bugs at their source, no prompt-template rewrite:

1. **`ProjectSpecificationMarkdownRenderer.renderDescriptor()`**, `PROJECT_OVERVIEW` branch: add `evidence = emptyList(), unresolved = emptyList()` to the `specification.copy(...)` call, matching `MODULE`/`PACKAGE`/`COMPONENT`/`RELATIONSHIP`. `project.md`'s own Evidence/Unresolved sections render as `- None`; the full catalog remains available (unchanged) in `evidence.md`, which `project.md` already links to as a dependency.

2. **`markdownItems()`**: extract only the leading backtick-fenced ID token from each bullet, not the full line:
   ```kotlin
   private fun markdownItems(content: String, heading: String): List<String> {
       val match = Regex("(?ms)^## ${Regex.escape(heading)}\\s*\\n(.*?)(?=^## |\\z)").find(content) ?: return emptyList()
       return Regex("(?m)^-\\s+`+([^`\n]+)`+").findAll(match.groupValues[1])
           .map { it.groupValues[1].trim() }.distinct().take(50).toList()
   }
   ```
   This is a strict narrowing of existing output (a substring of what was captured before), so `evidenceRefs`/`unresolvedRefs` become true reference lists: short, uniform, and — as a side effect — make `DocumentationEnrichmentEngine.validate()`'s "canonical references cannot be restated" check meaningful (today it almost never fires against full sentences; against short IDs it can actually catch restatement).

Neither change alters `DocumentationEnrichmentTarget`/`DocumentationEnrichmentRecord`'s shape (`evidenceRefs`/`unresolvedRefs` stay `List<String>`) or `DocumentationEnrichmentPrompt`'s template structure. `canonicalInput()`'s hash changes for any document whose `evidenceRefs`/`unresolvedRefs`/`canonicalNarrative` content changes — by RFC-0070's own rule ("semantic changes make the record stale by identity mismatch") this is the correct, intended effect: previously cached enrichments for affected artifacts are invalidated and will re-run on the next `--enrich`.

## Compatibility

- No DIR, Snapshot, Documentation Profile, or Bundle/Manifest/Receipt format change.
- Rendered content changes: `project.md` no longer inlines the full Evidence/Unresolved catalog (bug 1). This is a deterministic-document content change, not just an enrichment-prompt change — confirmed no existing test asserts Evidence-bullet content specifically within `project.md` (existing tests check substring presence across all joined rendered artifacts, satisfied by `evidence.md` alone).
- Enrichment cache identity changes for any previously-applied enrichment whose canonical input changes; those artifacts re-invoke the provider on the next `--enrich` run instead of reusing a stale cached narrative. This is existing, documented RFC-0070 behavior, not new behavior.
- No CLI flag, option, or subcommand changes.

## Out of scope

- Any change to `DocumentationEnrichmentEngine.validate()`'s rule set itself (rejection rules, length cap, markdown-structure checks) — those are unaffected; bug 2's fix makes an existing rule more effective, it does not add a new one.
- Prompt template wording changes to `DocumentationEnrichmentPrompt.render()` — the existing instructions ("facts, not directives") are adequate once the payload itself is small and reference-shaped; this RFC treats the misreading as a size/shape problem, not an instruction-following problem.
- `canonicalNarrative()` — already fixed on `main` (`6e13bb2`), out of scope here.
- `SCENARIO_DETAIL`/`scenario-flow`: `enrichmentSection()`/`allowedSections()` both reference a `SCENARIO_DETAIL` document type that no `DocumentationArtifactKind` currently produces (pre-existing, unrelated to this bug) — left as-is.

## Verification (executed)

- `ProjectSpecificationMarkdownRenderer.renderDescriptor()`'s `PROJECT_OVERVIEW` branch now clears `evidence`/`unresolved`, matching `MODULE`/`PACKAGE`/`COMPONENT`/`RELATIONSHIP`. New test `ProjectSpecificationMarkdownRendererTest.kt` — "project overview does not duplicate the full Evidence and Unresolved catalog already in the Evidence artifact" — asserts `project.md` renders `## Evidence\n\n- None` / `## Unresolved\n\n- None` and contains none of the fixture's evidence/unresolved IDs, while `evidence.md` still carries them.
- `markdownItems()` (made `internal`, matching the precedent `6e13bb2` set for `canonicalNarrative()`) now extracts only the leading backtick-fenced ID token per bullet. New file `docpilot-cli/src/test/kotlin/io/docpilot/cli/command/MarkdownItemsTest.kt` (4 tests): full-bullet-to-ID-only extraction, `- None` bullets treated as absent, dedup + 50-item cap, and no-heading-present returns empty.
- `./gradlew test` (full multi-module regression — root, `docpilot-cli`, `docpilot-provider-openai`, `docpilot-provider-ollama`, `docpilot-release`): **PASS**, `BUILD SUCCESSFUL`, 573 tests, 0 failures, 0 errors, 0 skipped.

## Acceptance criteria

1. `project.md` no longer embeds the project's full Evidence/Unresolved catalog; that content remains exclusively in `evidence.md`. **Met.**
2. `evidenceRefs`/`unresolvedRefs` on an enrichment request contain only stable ID tokens, never full descriptive bullet text. **Met.**
3. No DIR/Snapshot/Bundle format change; no new CLI surface. **Met.**
4. Full Gradle regression passes with 0 failures. **Met.**
