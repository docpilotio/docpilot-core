# RFC-0061 Main Planning Update

State: `IMPLEMENTED_WITH_DOCUMENTED_LIMITATIONS`

RFC-0061 adds structured Compose route and registration observations, verified
destination links, deterministic integrity, and DIR 0.4 Compose Entry Point, Feature,
and Scenario projection.

- targeted Compose/Feature/Entry Point/Scenario/Snapshot/Profile tests: PASS;
- full `clean test`: PASS;
- isolated architecture-samples generation: PASS;
- repeated architecture-samples generation: `NO_CHANGES`;
- Snapshot format 2 and DIR 0.4 validation: PASS.

Protected contracts remain unchanged: Snapshot format 1 reader, Profile identities,
RFC-0052 Artifact identities and paths, Evolution format 1, legacy Markdown output,
providers, and MCP production code.
