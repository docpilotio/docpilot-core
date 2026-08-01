# RFC-0067 — Contract Documentation Rendering

Status: `IMPLEMENTED_WITH_VALIDATION_LIMITATIONS`

## Problem and decision

Persisted DIR 0.5 Contracts were machine-readable but had no Profile-aware human documentation. RFC-0067 adds deterministic Contract Catalog and per-Contract Detail artifacts. The renderer consumes only `ProjectSpecification.contracts` plus referenced canonical Evidence and unresolved items; it does not read or rescan source and does not create, repair, classify, or rename Contracts.

`EXTERNAL_API_CONTRACT` remains the external-boundary document and is not overloaded. Additive `CONTRACT_CATALOG` and `CONTRACT_DETAIL` document/artifact kinds, `PER_CONTRACT` multiplicity, and `CONTRACT_DOCUMENTATION_RENDERING` capability represent all nine roles without changing Profile version 1 or serialized DIR/Snapshot formats.

## Profile and readiness

`kotlin-android@1` defines a single `contracts/catalog.md` and one `contracts/details/{contractId}.md` definition per Contract. DIR 0.5 satisfies `CONTRACT_MODEL`, including a legitimately empty collection. DIR 0.1–0.4 remains deferred with a version-specific diagnostic; an old migrated payload must first become DIR 0.5 through the existing pipeline. Missing renderer capability is unsupported, and ownership/path conflicts remain blocked by existing policy.

## Artifact identity and paths

- Catalog ID: `contract-catalog:<project-stable-id>`; path `docs/contracts/catalog.md`.
- Detail ID: `contract-detail:<contract-stable-id>`.
- Detail path: canonical display-name slug plus the first eight hex characters of SHA-256 over the Contract Stable ID.
- Paths are normalized repository-relative portable paths. Absolute Evidence paths fail closed.
- Catalog depends on every Detail. A Detail scope contains only its Contract, owner/source bindings, nested values, relationships, Evidence, and unresolved IDs.

This scope binding reuses the RFC-0052 planner: additions create Details and update Catalog; removals produce safe orphan/reconciliation candidates; shape, owner, relationship, Evidence, and unresolved changes update the affected Detail and Catalog; no matching change retains artifacts and yields `NO_CHANGES`. Unknown-owned collisions are never overwritten.

## Catalog and Detail

Catalog contains specification/Profile identity, total and unresolved counts, counts for every canonical role and kind, and a Stable-ID-ordered table with owner, shape counts, Evidence/unresolved counts, and deterministic Detail links.

Detail globally uses explicit `None.` for empty sections. Its fixed sections are Identity, Classification, Ownership, Source Bindings, Inputs, Outputs, Members, Relationships, Evidence, Unresolved, and Generation Metadata. Semantically ordered inputs/outputs/members preserve `semanticOrder` then Stable ID; other collections use Stable ID. Nested type kind, nullability, arguments, resolved target, cardinality, Evidence, and unresolved IDs are rendered without inference. Unresolved targets never become links.

## Review, reconciliation, and compatibility

Review Bundle format 1 and reconciliation already address stable artifact IDs, paths, rendered content/hash, ownership manifests, planned operations, and Evidence-scoped changes without a closed artifact-kind wire enum. Contract artifacts therefore participate additively without a format change. Stale removed Details are orphan/reconciliation targets and are not immediately deleted. Feature identities and output are unchanged.

Snapshot formats 1/2, Snapshot format 3, DIR 0.1–0.4 readers, DIR 0.5 canonical hashing, Specification Diff, Evolution format 1, Provider SPI, CLI JSON, and Product Validation state are unchanged. Unknown model enum values continue to fail closed.

## Failures and diagnostics

Rendering rejects non-DIR-0.5 input, unknown requested artifact IDs, and absolute Evidence paths. Older DIR input is deferred by Profile resolution. Canonical empty collections render an empty Catalog; they are not evidence of a failed extraction. AI is absent from the required path.

## Validation and limitations

All multi-module tests pass. Nine-role fixtures cover nested/nullable/unresolved types, stable ordering, Profile readiness, deterministic rendering, and absolute-path rejection. An isolated `architecture-samples` copy generated 72 Details plus Catalog: 69 PUBLIC_API and 3 CALLBACK. First execution was `FULL_REGENERATION`; the second was `NO_CHANGES` with a VALID Snapshot and identical artifact hashes. The original checkout was unchanged.

The sample has no Evidence-backed examples for the other seven roles; those remain fixture-validated. RFC-0067 does not add a standalone Contract-only CLI, Review format revision, or reconciliation CLI behavior. RFC-0068 receives documentation impact/coverage work; RFC-0069 owns full traceability; RFC-0072 owns AI narrative enrichment.
