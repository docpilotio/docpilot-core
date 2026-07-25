# RFC-0044: Relationship Semantics

## Status

Completed and independently verified.

## Scope

RFC-0044 defines deterministic relationship endpoint semantics for DIR schema 0.3 without changing the public `RelationshipSpecification` shape, snapshot format 1, the incremental model, or the RFC-0043 complete-review-before-merge workflow.

## Endpoint Contract

Every relationship endpoint has exactly one semantic kind:

- `INTERNAL`: the endpoint ID is an existing DIR module, package, component, API, or property ID.
- `EXTERNAL`: the endpoint ID has the non-empty `external:` namespace and represents a graph-external type.
- `UNRESOLVED`: the endpoint ID has the non-empty `unresolved:` namespace and records an endpoint that cannot be resolved safely.

Relationship sources are internal. Raw graph IDs are never used as a fallback for unknown endpoints.

Files normalize to the package belonging to that file's normalized module, including the explicit default package, or to the module when no package exists. Package graph nodes resolve in module context. Ambiguity that cannot be resolved safely becomes unresolved rather than selecting an arbitrary candidate.

Resolution occurs before relationship identity, duplicate removal, ordering, and structural self-relationship removal.

## Dependencies

`ComponentSpecification.dependencyIds` contains the sorted unique targets of direct outgoing `DEPENDS_ON` relationships for that component. Other relationship types, unresolved targets, and transitive dependencies do not contribute.

## Validation and Rendering

The validator rejects blank, raw unknown, malformed external, malformed unresolved, and invalid structural self endpoints. It enforces the direct `DEPENDS_ON` projection.

Markdown relationship output includes source and target endpoint kinds. Ordering remains deterministic.

## Compatibility

- `RelationshipSpecification` public API is unchanged.
- DIR schema remains `0.3`.
- Snapshot format remains `1`.
- Existing incremental execution and RFC-0043 review behavior are unchanged.
- Relationship-specific incremental diff remains outside RFC-0044 scope.

## Verification

Phase 8 independent re-verification recorded 85 XML test result files, 254 tests, 0 failures, and 0 skipped. Phase 9 added isolated CLI smoke validation. See:

- [RFC-0044 completion handoff](../handoffs/RFC-0044-COMPLETION-HANDOFF.md)
- [Phase 8 independent re-verification](../history/mcp-transition/PHASE-8-RFC-0044-INDEPENDENT-REVERIFICATION.md)
- [Phase 9 completion smoke](../history/mcp-transition/PHASE-9-RFC-0044-COMPLETION-SMOKE.md)
