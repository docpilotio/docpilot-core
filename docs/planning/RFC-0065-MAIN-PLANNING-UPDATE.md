# RFC-0065 Main Planning Update

## Baseline and versions

- Baseline: `f81992e`, containing RFC-0064 commit `4fa2f82`.
- Branch: `codex/rfc-0065-contract-specification-foundation`.
- DIR: 0.5.
- Snapshot: format 3.
- Product Validation remains `PRODUCT_VALIDATION_FAIL / NOT_APPROVED`; PV-009 remains `PENDING`.

## Phase 0 decisions

| Product concept | Kind | Role | Required shape | Owner | Extraction |
|---|---|---|---|---|---|
| Public API | API | PUBLIC_API | input or output | canonical entity | RFC-0066 |
| Repository API | API | REPOSITORY_API | input or output | canonical entity | RFC-0066 |
| Data model | DATA | DATA_MODEL | member | canonical entity | RFC-0066 |
| DTO | DATA | DTO | member | canonical entity | RFC-0066 |
| Event | MESSAGE | EVENT | member | canonical entity | RFC-0066 |
| Callback | MESSAGE | CALLBACK | delivered input | canonical entity | RFC-0066 |
| Navigation argument | NAVIGATION | NAVIGATION_ARGUMENT | member | Entry Point/component | RFC-0066 |
| Persistence schema | PERSISTENCE | PERSISTENCE_SCHEMA | member | canonical entity | RFC-0066 |
| External boundary | EXTERNAL | EXTERNAL_SERVICE_BOUNDARY | input or output | explicit external/canonical entity | RFC-0066 |

Existing API and Property entities remain source-level canonical entities. Contracts bind to them but do not duplicate their discovery role. Existing Relationship remains the general specification graph; Contract relationships capture typed boundary semantics without changing DIR 0.4 bytes.

## Delivery sequence

1. Canonical models, identity, validation, ordering, and hash inputs.
2. Snapshot 3 strict codec and explicit empty migration.
3. Stable-ID diff and additive Evolution foundation.
4. Fixture validation for all nine roles and compatibility regression.
5. RFC-0066 handoff without source extraction or rendering.
