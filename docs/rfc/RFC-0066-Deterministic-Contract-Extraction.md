# RFC-0066 — Deterministic Contract Extraction

Status: `IMPLEMENTED_WITH_VALIDATION_LIMITATIONS`

RFC-0066 adds an AI-independent extraction stage after Feature Discovery. It consumes `SourceIndex`, canonical DIR entities, source Evidence, and Compose navigation observations, then emits canonical DIR 0.5 Contracts before validation and Snapshot format 3 persistence.

## Evidence support matrix

| Role | Deterministic evidence | Support | Ambiguity policy |
|---|---|---|---|
| PUBLIC_API | Kotlin `DEFAULT`/`PUBLIC`/`PROTECTED` API plus declaration Evidence | YES | unresolved parameter or result type remains explicit |
| REPOSITORY_API | qualified Spring or DocPilot repository annotation plus API shape | YES | no name/package inference |
| DATA_MODEL | qualified DocPilot data-model annotation plus members | YES | no data-class business inference |
| DTO | qualified kotlinx serialization, Jackson, or DocPilot DTO annotation | YES | simple annotation name is insufficient |
| EVENT | qualified Spring event or DocPilot event annotation plus members | YES | no `Event` suffix inference |
| CALLBACK | function-typed API parameter | YES | only delivered input shape is projected |
| NAVIGATION_ARGUMENT | existing Compose argument observation and resolved Entry Point | YES | missing owner/evidence suppresses Contract |
| PERSISTENCE_SCHEMA | qualified Room Entity or DocPilot persistence annotation | YES | Room simple-name collision is rejected |
| EXTERNAL_SERVICE_BOUNDARY | qualified Retrofit HTTP annotation plus API shape | YES | dynamic endpoint behavior is not inferred |

## Architecture and rules

`DeterministicContractExtractionEngine` owns a visible role registry, owner/type resolution, shape projection, Stable IDs, Evidence binding, unresolved type creation, duplicate merging, and canonical ordering. Scanner output remains syntax observation; it never creates Contracts. Qualified annotations are resolved from explicit imports or already-qualified text. Wildcard imports are intentionally not guessed.

Type projection preserves nullability and nested generic/collection arguments. Resolution uses qualified identity, explicit import, or a globally unique canonical component. Multiple simple-name candidates create a deterministic `UnresolvedItem`; zero project candidates remain explicit external types. Contract identities use RFC-0065 `ContractIdentity`, excluding time, absolute paths, source lines, and discovery order.

Duplicate observations merge only when Stable ID, kind, role, and owner agree. Incompatible observations fail closed. Previous snapshots are immutable; each build replaces the Contract collection from current Evidence instead of appending.

## Boundaries

Rendering, Contract artifacts, AI enrichment, runtime behavior, whole-program data flow, and Product Validation are excluded. Initial framework coverage is Room, Retrofit, kotlinx serialization, the named Spring annotations, and explicit `io.docpilot.contract.*` annotations. Unsupported framework syntax produces no asserted business Contract.

