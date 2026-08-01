# RFC-0064 Starting Prompt

RFC-0064 is fixed as **Profile-aware Feature Documentation Rendering** by the Product Owner's
RFC-0064 through RFC-0074 first product-development roadmap. Before implementation, verify the
RFC-0063 completion Evidence and obtain approval for RFC-0064's detailed design, data contracts,
Acceptance Criteria, and verification plan.

The approved capability boundary is deterministic rendering of DIR 0.4 Feature, Entry Point,
Scenario, and ordered Scenario Step data into a Feature catalog and per-Feature documents, with
Evidence references, unresolved and completeness state, Documentation Profile resolution,
RFC-0052 Artifact Plan integration, and selective rendering.

The renderer must consume DIR 0.4 without rescanning source, must not invent Feature or Scenario
entities, and must preserve existing Artifact, Profile, Stable ID, Snapshot, Review, Reconciliation,
Evolution, and Release contracts. Do not introduce a format or identity change without an explicit
version, migration, compatibility, and rollback decision. Do not change public Product Validation
or PV-009 state.

Use `docs/planning/RFC-0064-RFC-0074-FIRST-PRODUCT-DEVELOPMENT-ROADMAP.md` as the governing scope
baseline. Historical MCP references and deferred signing or attestation candidates are not RFC-0064
scope.
