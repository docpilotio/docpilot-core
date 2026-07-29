# RFC-0057 DIR 0.4 Migration Readiness

## Decision

`READY_FOR_CONTRACT_DESIGN_NOT_READY_FOR_SCHEMA_IMPLEMENTATION`

RFC-0057 does not implement DIR 0.4. The current runtime baseline remains DIR 0.3 with Specification Snapshot format 1.

## Candidate additions

Future documentation expansion may require:

- `FeatureSpecification`
- `EntryPointSpecification`
- `ScenarioSpecification`
- `InteractionStep`
- `ContractSpecification`
- `DocumentationClaim`
- `DiagramSpecification`

RFC-0058 defines Documentation Profiles and Document Contracts before these concepts are added to the runtime model.

## Compatibility principles

1. DIR 0.3 Snapshot data remains readable.
2. No in-place automatic migration of stored Snapshot files.
3. Migration is explicit, deterministic, content-addressed, and reversible.
4. Existing Stable IDs are preserved exactly.
5. New Feature and Scenario IDs use separate deterministic namespaces.
6. Missing fields stay absent or unresolved; AI cannot synthesize canonical facts.
7. Existing RFC-0052 Plan and RFC-0053 Projection hashes remain byte-compatible for unchanged DIR 0.3 fixtures.
8. Existing RFC-0055 ownership and reconciliation contracts remain valid.
9. RFC-0056 continues to verify and compare format-1 DIR 0.3 states.
10. Legacy and profile-based documents may coexist through separate Artifact descriptors and ownership manifests.

## Snapshot direction

Preferred future design:

```text
Snapshot format 1 → retains DIR 0.3 reader and writer
Snapshot format 2 → introduced only when DIR 0.4 persistence is required
Explicit migration → format 1 / DIR 0.3 to format 2 / DIR 0.4
Original input → retained for rollback and audit
```

A later RFC must decide whether format 2 stores a fully expanded model or an additive envelope. RFC-0057 makes no format change.

## Differ and renderer requirements

- The Differ must treat schema transition as an explicit compatibility event rather than an ordinary field diff.
- Stable IDs common to both schemas remain identity anchors.
- New entities are additive only when deterministic identity and Evidence rules exist.
- Renderers must tolerate absent profile/feature/scenario data.
- Existing legacy Artifact paths remain stable unless an approved profile contract explicitly owns a new path.
- RFC-0052 plans must distinguish legacy artifact retention from profile artifact creation.

## Reconciliation and Evolution requirements

- Ownership manifests define whether legacy or profile artifacts own each managed region.
- Reconciliation must never overwrite manual content solely because a new profile exists.
- Evolution must distinguish schema migration, new discovered facts, document layout changes, and user-authorized ownership changes.
- Coverage must be PARTIAL or BLOCKED when migration Evidence is missing or integrity fails.

## RFC-0058 entry conditions

RFC-0058 may start when:

- current contract versions are fixed by the canonical manifest;
- profile identity and document ownership are defined without changing DIR 0.3;
- required/optional document sections and Evidence rules are explicit;
- legacy document coexistence is specified;
- no production model is added before contract review and approval.
