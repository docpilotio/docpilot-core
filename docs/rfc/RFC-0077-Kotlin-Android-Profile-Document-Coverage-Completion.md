# RFC-0077 — Kotlin/Android Profile Document Coverage Completion

Status: `IMPLEMENTED`

Track: v1.1 Product Capability

Depends on: RFC-0058 (Documentation Profiles and Document Contracts), RFC-0065–0067 (Contract Specification Foundation, Extraction, Rendering)

## Problem and decision

`KotlinAndroidDocumentationProfile` (RFC-0058) declares eleven `DocumentType` values for `kotlin-android@1`, five of which have never been projected into a renderable Artifact: `MODULE_ARCHITECTURE`, `DOMAIN_MODEL`, `DATABASE_SCHEMA`, `EXTERNAL_API_CONTRACT`, and `TEST_STRATEGY`. `DocumentationArtifactKind` (`SelectiveSpecificationRenderer.kt`) currently defines only twelve values — `PROJECT_OVERVIEW`, `MODULE`, `PACKAGE`, `COMPONENT`, `RELATIONSHIP`, `EVIDENCE`, `ARCHITECTURE_OVERVIEW`, `FEATURE_CATALOG`, `FEATURE_DETAIL`, `CONTRACT_CATALOG`, `CONTRACT_DETAIL`, `INDEX` — and none of them correspond to the five missing types. `ProfileArtifactCompatibility` binds only Project Overview and Architecture Overview into the legacy RFC-0052 Artifact Catalog, so even though RFC-0058 documents Module Architecture and Test Strategy as expected `READY or PARTIAL` under DIR 0.3, `docpilot generate docs` never emits them. This was confirmed empirically: a full `--confirm --full` run against `architecture-samples` produced zero files under `architecture/module-architecture.md` or `quality/test-strategy.md`, and `contracts/domain-model.md`, `contracts/database-schema.md`, `contracts/external-apis.md` do not exist at all.

RFC-0077 closes this gap by adding five renderer implementations and five matching `DocumentationArtifactKind`/RFC-0052 Artifact descriptors, using only canonical data that already exists in DIR 0.5 (`ProjectSpecification.modules/packages/components`, `.contracts`, `.evidence`). It does not change DIR schema, Snapshot format, Contract extraction (RFC-0066), or Profile version. It does not add new Evidence, infer new entities, or grant AI any role in these five documents — they remain 100% deterministic, matching every other Profile document delivered so far.

## Scope

| Document | New `DocumentationArtifactKind` | Path | Source model | Multiplicity |
|---|---|---|---|---|
| Module Architecture | `MODULE_ARCHITECTURE` | `architecture/module-architecture.md` | `specification.modules/packages/components` (same data as existing per-Module/Package/Component artifacts, aggregated into one document) | SINGLE |
| Domain Model | `DOMAIN_MODEL` | `contracts/domain-model.md` | `specification.contracts` filtered to `ContractKind.DATA` (roles `DATA_MODEL`, `DTO`) | SINGLE |
| Database Schema | `DATABASE_SCHEMA` | `contracts/database-schema.md` | `specification.contracts` filtered to `ContractKind.PERSISTENCE` (role `PERSISTENCE_SCHEMA`) | SINGLE |
| External API Contract | `EXTERNAL_API_CONTRACT` | `contracts/external-apis.md` | `specification.contracts` filtered to `ContractKind.EXTERNAL` (role `EXTERNAL_SERVICE_BOUNDARY`) | SINGLE |
| Test Strategy | `TEST_STRATEGY` | `quality/test-strategy.md` | `specification.evidence` filtered to `EvidenceSubject.TEST`, plus test-owning Components already present in DIR 0.3 | SINGLE |

Each document reuses the section structure already defined for it in `KotlinAndroidDocumentationProfile.kt` (Overview / Inventory / Relationships / Constraints / Evidence / Unknowns for the three Contract-sourced documents; Overview / Module Inventory / Package-and-Component Boundaries / Dependencies / Evidence / Unknowns for Module Architecture; Overview / Observed Test Boundaries / Test Levels / Execution / Coverage Gaps / Evidence for Test Strategy). No new section vocabulary is introduced.

## Known limitation carried forward from RFC-0066/0067

RFC-0066's deterministic Contract extraction currently populates only two of the nine `ContractRole` values for `architecture-samples` (`PUBLIC_API`, `CALLBACK` — 69 and 3 respectively, per the RFC-0067 validation note). `DATA_MODEL`, `DTO`, `PERSISTENCE_SCHEMA`, and `EXTERNAL_SERVICE_BOUNDARY` remain fixture-validated only. This means Domain Model, Database Schema, and External API Contract will render as **legitimately empty documents** against `architecture-samples` immediately after RFC-0077 ships — an empty Contract collection is explicit, canonical output under RFC-0065's Integrity policy, not a defect. Populating those roles for real Kotlin/Android source (Room `@Entity` → `PERSISTENCE_SCHEMA`, `data class` DTOs → `DATA_MODEL`/`DTO`, Retrofit/service interfaces → `EXTERNAL_SERVICE_BOUNDARY`) is deliberately **out of scope** for RFC-0077 and belongs to a future RFC-0066 extraction extension. Module Architecture and Test Strategy have no such limitation — DIR 0.3 already contains everything they need.

## Artifact identity and paths

Stable IDs follow the existing convention used by `ARCHITECTURE_OVERVIEW` and `CONTRACT_CATALOG`:

```text
module-architecture:<project-stable-id>
domain-model:<project-stable-id>
database-schema:<project-stable-id>
external-api-contract:<project-stable-id>
test-strategy:<project-stable-id>
```

All five are `SINGLE` multiplicity, fixed paths, `DOCPILOT_OWNED` ownership, `BLOCK` conflict behavior — identical policy to every other Profile document already implemented. No new path-placeholder syntax is required.

## Compatibility

- DIR 0.3, 0.4, 0.5 readers, Snapshot formats 1/2/3, Review Bundle format 1, Evolution Report format 1, Documentation Profile version `kotlin-android@1`, and Profile semantic identity are unchanged (adding renderer coverage for already-declared document types does not alter Profile identity — RFC-0058 identity is computed from the Profile *definition*, not from which document types have a renderer bound).
- `ProfileArtifactCompatibility` gains three additional one-to-one bindings (Module Architecture, plus the two now-renderable Contract-sourced-but-previously-unbound documents where applicable); no existing binding changes.
- Existing 162-artifact output for `architecture-samples` (project/module/package/component/feature/contract/relationships/evidence) is unaffected; this RFC is strictly additive to the Artifact Catalog.
- `--enrich` continues to work unmodified: `MODULE_ARCHITECTURE` and `TEST_STRATEGY` are not currently in `enrichmentSection()`'s mapping (`DocumentationGenerationWorkflow.kt:359-366`) and are out of scope for enrichment in this RFC — they render as deterministic-only documents, consistent with Domain Model/Database Schema/External API Contract which also have no enrichment section defined in the Profile today.

## Out of scope

- Extending RFC-0066 Contract extraction to populate `DATA_MODEL`, `DTO`, `PERSISTENCE_SCHEMA`, `EXTERNAL_SERVICE_BOUNDARY` roles for real Kotlin/Android source (tracked as a future extraction RFC).
- Adding enrichment sections for the five new document types (tracked separately if desired; RFC-0070's existing bounded-narrative mechanism can be extended later without re-opening this RFC).
- Any change to DIR, Snapshot, Review, Reconciliation, or Evolution formats.
- RFC-0072 (Claims/Traceability), RFC-0078–0082 (Finding model, synthesis documents) — unrelated and unblocked by this RFC.

## Verification (executed)

- Implementation: `DocumentationArtifactKind` gained five values (`SelectiveSpecificationRenderer.kt`); a new `ProfileDocumentCoverageMarkdownRenderer` implements all five documents and is composed into `ProjectSpecificationMarkdownRenderer.describe()`/`render()`/`indexLabel()`, mirroring the existing `ContractDocumentationMarkdownRenderer` composition pattern. No DIR, Snapshot, Review, or Evolution code was touched.
- `ProjectSpecificationMarkdownRendererTest` updated: artifact count assertion raised from 10 to 15, with explicit path and content assertions for all five new documents; full suite re-run after the update.
- `./gradlew test` (full multi-module regression): **PASS**, 0 failures.
- `docpilot generate docs --project <isolated architecture-samples copy> --output <dir> --confirm --full`: all five new files created (`architecture/module-architecture.md`, `contracts/domain-model.md`, `contracts/database-schema.md`, `contracts/external-apis.md`, `quality/test-strategy.md`); all 162 pre-existing artifacts unchanged (`KEEP`). A second identical run produced `Result: NO_CHANGES`.
- `docpilot bundle verify --strict` on the applied output: `Bundle Status: VALID`, 0 missing files, 0 changed files, 0 broken links.
- Confirmed empirically: Domain Model and Database Schema render as explicit legitimately-empty documents against `architecture-samples` (0 `DATA`/`PERSISTENCE` Contracts currently extracted), each stating this is expected RFC-0066 coverage, not a failure. Module Architecture and Test Strategy render non-trivially (3 modules; 6 JVM unit test files and 7 instrumented test files detected deterministically from Evidence file paths and module source sets).
- `ProfileArtifactCompatibility` required no code change: all five new artifact paths (after the existing `docs/` prefix stripped by `logicalPath()`) exactly match the paths already declared in `KotlinAndroidDocumentationProfile.kt`, so they bind automatically via the existing `EXACT_PATH` branch.

## Acceptance criteria

1. All five documents are produced by `docpilot generate docs --full` against `architecture-samples` without requiring `--enrich`. **Met.**
2. No existing artifact identity, path, or content hash changes. **Met** — all 162 pre-existing artifacts reported `KEEP`.
3. Domain Model / Database Schema / External API Contract render correctly with zero Contracts (explicit empty state, matching RFC-0065 Integrity policy). **Met.**
4. Full Gradle regression passes; no `git diff --check` violations. **Met.**
5. `docpilot bundle verify --strict` passes on the applied output. **Met.**
