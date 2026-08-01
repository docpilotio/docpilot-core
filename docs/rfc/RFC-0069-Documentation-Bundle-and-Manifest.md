# RFC-0069 — Documentation Bundle and Manifest

## Status

Implemented on `codex/rfc-0069-documentation-bundle` (2026-08-02).

## Problem and goals

RFC-0068 produces deterministic Markdown but did not provide a portable proof of exactly which specification, snapshot, profile, plan, and files formed that result. RFC-0069 adds a machine-readable Documentation Bundle manifest, a generation receipt, and offline verification without re-analyzing source code.

Non-goals are changing DIR 0.5, Snapshot Format 3, stable artifact IDs, extraction/rendering semantics, AI enrichment, remote link checks, signing, upload, or Product Validation state.

## Model and formats

Bundle Format 1 is UTF-8 canonical JSON with LF and a final newline, stored at `.docpilot/documentation-bundle.json`. The artifact index is ordered by stable artifact ID and records ID, document type, `text/markdown`, output-root-relative path, DocPilot ownership, content SHA-256, byte size, stable `RENDER` action, dependency IDs, and link counts. Duplicate IDs and case-insensitive path collisions fail closed.

The manifest binds the project/specification identity, DIR version, Snapshot Format 3 payload SHA-256, exact profile ID/version/semantic SHA-256, RFC-0068 plan SHA-256, artifact aggregate SHA-256, receipt identity/hash, link status, aggregate SHA-256, and manifest SHA-256. `manifestSha256` is SHA-256 of the canonical payload with that field empty. It is not recursively included in the aggregate hash.

The receipt is stored at `.docpilot/documentation-generation-receipt.json`. Its stable ID and semantic SHA-256 exclude timestamps, locale, machine/user identity, temporary names, and absolute output paths. AI enrichment is explicitly absent; Format 1 makes no provider/model claims.

Bundle stable ID depends only on Bundle Format 1, project stable ID, and exact profile identity. Document or snapshot changes alter semantic/manifest hashes, not the bundle stable ID. Content hashes use exact emitted UTF-8 bytes; renderers remain responsible for canonical LF. Semantic and content hashes are distinct concepts.

## Ownership and transactions

`.docpilot/documentation-ownership.manifest` remains RFC-0068 reconciliation/apply protection state. It is neither replaced by nor authoritative for the Bundle index. The Bundle Manifest is portable evidence of a complete generated set.

Apply stages documents and ownership changes, saves Snapshot Format 3, then writes receipt and Bundle manifest and performs final offline verification. Failure restores changed documents, ownership manifest, Bundle manifest, and receipt; snapshot-last behavior is retained. Preview computes identities without writing. A repeated identical apply returns `NO_CHANGES` and does not rewrite documents, snapshot, manifest, or receipt.

Paths are normalized repository-relative paths. Absolute, traversal, Windows-drive, duplicate, and case-colliding paths fail closed. Verification distinguishes `VALID`, `INVALID`, `INCOMPLETE`, `UNSUPPORTED`, and `TAMPERED`; malformed/noncanonical or self-integrity failures are never ignored.

## CLI and compatibility

`docpilot bundle verify --bundle <root-or-manifest> [--strict] [--json]` verifies Format 1 offline. Exit codes are 0 valid, 2 invalid arguments, 4 unsupported format, 5 incomplete/missing, 6 tampered/changed, and 7 invalid manifest. Existing `generate specification`, `generate docs`, options, ownership format, Profile behavior, Snapshot readers, DIR readers, and provider SPI remain compatible. RFC-0069 fields are additive in generate-docs JSON.

## Known limitations and RFC-0070 seam

Format 1 verifies manifest self-integrity, path/index constraints, exact file bytes and sizes, and offline completeness. Full Markdown AST link/fragment validation, unexpected-managed-file policy, persisted selective-generation manifest merging, registry-backed offline profile revalidation, and receipt-file tamper verification remain explicitly incomplete and must not be represented as passing. RFC-0070 may add an additive enrichment collection carrying provider, model, canonical input, template, target, narrative hash, and outcome without changing Format 1 identities when enrichment is not applied.
