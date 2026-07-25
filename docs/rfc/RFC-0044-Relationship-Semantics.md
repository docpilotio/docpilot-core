# RFC-0044: Relationship Semantics

## Status

Implementation and independent verification completed. Feature-branch Git integration is pending.

## Scope

RFC-0044 defines deterministic relationship endpoint semantics for DIR schema 0.3 without changing the public `RelationshipSpecification` shape, snapshot format 1, the incremental model, or the RFC-0043 complete-review-before-merge workflow.

## Endpoint contract

Every relationship endpoint has exactly one semantic kind:

- `INTERNAL`: the endpoint ID is an existing DIR module, package, component, API, or property ID.
- `EXTERNAL`: the endpoint ID has the non-empty `external:` namespace and represents a graph external type.
- `UNRESOLVED`: the endpoint ID has the non-empty `unresolved:` namespace and records an endpoint that cannot be resolved safely. Its suffix identifies the unresolved reference and endpoint direction (`source` or `target`), and the corresponding `UnresolvedItem` is required.

Relationship sources must resolve to `INTERNAL`. Raw graph IDs are never used as fallback endpoints.

Files normalize to the package belonging to that file's normalized module, including the explicit default package. Package graph nodes normalize by counterpart context in this order:

1. the file module when the endpoint is a file;
2. the counterpart component or internal endpoint module;
3. a unique qualified-package candidate;
4. otherwise `UNRESOLVED`.

Ambiguous package candidates are never selected with `firstOrNull()` or stable ordering.

Resolution occurs before relationship identity, duplicate removal, ordering, and structural self-relationship removal.

## Dependencies

`ComponentSpecification.dependencyIds` contains exactly the sorted unique targets of direct outgoing `DEPENDS_ON` relationships for that component. INTERNAL and EXTERNAL targets are included. UNRESOLVED targets, other relationship types, and transitive dependencies do not contribute.

## Validation and rendering

The validator:

- rejects EXTERNAL or UNRESOLVED relationship sources;
- verifies INTERNAL endpoint identity;
- requires each UNRESOLVED endpoint to have a corresponding `UnresolvedItem`;
- rejects structural self-relationships;
- enforces exact direct `DEPENDS_ON` projection in `dependencyIds`.

Markdown relationship output includes the source and target endpoint kind. Ordering remains `sourceId`, type, `targetId`, and relationship ID.

## Compatibility

- `RelationshipSpecification` public API is unchanged.
- DIR schema remains `0.3`.
- Snapshot format remains `1`; the existing deterministic codec shape is unchanged.
- Existing incremental execution and RFC-0043 review behavior are unchanged.

## Verification

- Four targeted suites passed: Builder, Validator, Renderer, and Snapshot codec.
- `.\gradlew.bat clean build`: PASS.
- `.\gradlew.bat clean test`: PASS — 85 XML files, 254 tests, 0 failures, 0 errors, 0 skipped.
- Isolated `architecture-samples` fixture CLI analysis: PASS with seven non-empty expected artifacts.
- `git diff --check`: PASS.

Phase 8 independent verification is accepted as completion evidence because the Phase 7 Worker final Structured Result is unavailable. Phase 9 and this integration-preparation run independently confirmed the CLI smoke.

## Known limitations and technical debt

- A dedicated `RelationshipEndpointResolverTest` file is absent; Builder integration and deterministic multi-module tests cover the resolver.
- Relationship-specific Incremental Diff is not implemented.
- `RelationshipChange` and `IncrementalUpdateTarget.RELATIONSHIP` remain future candidates and are not automatically assigned to RFC-0045.
- Release Candidate remains pending.
