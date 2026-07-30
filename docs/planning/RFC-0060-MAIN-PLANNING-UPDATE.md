# RFC-0060 Main Planning Update

State: `IMPLEMENTED_WITH_VALIDATION_LIMITATIONS`

- Default Builder emits DIR 0.4.
- Deterministic Android framework Entry Point detection is integrated.
- Bounded participant traversal, direct-call Scenario projection, canonicalization, and
  discovery integrity are implemented.
- Snapshot format 2, RFC-0059 Diff/Incremental behavior, Profile Resolution, RFC-0052
  Artifact contracts, and Evolution format 1 remain compatible.
- Official architecture-samples validation produced a valid DIR 0.4 format-2 snapshot.
  It proved an Activity-rooted Feature, while Compose business destinations remain false
  negatives because route registration arguments are not canonical source Evidence.
- Public v1.0 remains `PRODUCT_VALIDATION_FAIL / NOT_APPROVED`; PV-009 remains `PENDING`;
  v1.1 RC remains `NOT_DECLARED`.

Next: RFC-0061 should strengthen navigation and route Evidence without introducing a
second source of truth.
