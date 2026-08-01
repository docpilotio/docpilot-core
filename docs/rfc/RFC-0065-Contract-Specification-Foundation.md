# RFC-0065 — Contract Specification Foundation

Status: IMPLEMENTED

## Decision

DIR 0.5 and Snapshot format 3 add an Evidence-backed canonical Contract collection. DIR 0.3/0.4 and Snapshot formats 1/2 retain their prior payload and reader behavior. Migration never infers Contracts: a migrated specification contains an empty Contract collection until RFC-0066 extraction runs.

## Canonical model

`ContractSpecification` contains a stable identity, semantic key, display name, kind, role, owner, source bindings, inputs, outputs, members, relationships, Evidence references, and unresolved references. Inputs, outputs, members, and types are nested canonical values rather than display strings.

Contract kinds are `API`, `DATA`, `MESSAGE`, `NAVIGATION`, `PERSISTENCE`, and `EXTERNAL`. The nine product concepts are preserved by `ContractRole`: `PUBLIC_API`, `REPOSITORY_API`, `DATA_MODEL`, `DTO`, `EVENT`, `CALLBACK`, `NAVIGATION_ARGUMENT`, `PERSISTENCE_SCHEMA`, and `EXTERNAL_SERVICE_BOUNDARY`.

Stable IDs use normalized semantic kind, role, owner, and semantic key. Display titles, timestamps, source lines, absolute paths, locale, discovery order, and AI output are excluded.

## Integrity

- Contract-level Evidence is mandatory and cannot be exclusively low-confidence.
- Owners and source bindings must resolve to canonical entities or be explicitly external.
- Ambiguous types and relationship endpoints require `UnresolvedItem` references.
- Unknown kinds, roles, types, versions, and malformed payloads fail closed.
- Collections use stable-ID order, except explicitly ordered values, which use semantic order followed by stable ID.
- Snapshot format 3 uses the existing canonical JSON payload and SHA-256 envelope integrity policy.

## Diff and evolution

Contract changes participate in stable-ID specification diff. Addition, removal, owner movement, shape, relationship, Evidence, and unresolved changes are visible as Contract modifications. Evolution format 1 receives additive `CONTRACT` subject/node and `CONTRACT_CHANGED` change values; Contract artifact impact remains deferred.

## Boundaries

RFC-0066 owns deterministic Kotlin/Android extraction. RFC-0067 owns Contract catalog/detail artifacts and Markdown rendering. AI does not create, migrate, or repair canonical Contracts. The `kotlin-android@1` Contract document capability remains deferred until RFC-0067.
