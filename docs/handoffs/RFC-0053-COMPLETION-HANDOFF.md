# RFC-0053 Completion Handoff

## RFC identity

- ID: RFC-0053
- Title: Semantic Relationship Expansion
- Track: Product Capability
- Feature branch: `feature/rfc-0053-semantic-relationship-spec`
- Baseline: `fde1700f9a71b8aa5da2ac08928323ab380ef42d`

## Implementation summary

Core now owns the official `DEPENDS_ON`, `EXTENDS`, `IMPLEMENTS`, `CALLS`, and
`IMPORTS` allowlist, length-framed canonical identity, Evidence-required
aggregation, deterministic high-cardinality thresholds, and Relationship
Projection Report format 1.

Source models provide explicit scanner-proven supertype and call observations.
Knowledge construction emits Evidence-backed edges, merges repeated occurrences,
and never selects ambiguous targets. The Specification Builder exposes an
enriched build result containing DIR plus the projection report while preserving
the existing `build()` API.

## Core contracts

- `SourceSuperTypeReference`
- `SourceCall`
- `SemanticRelationshipKind`
- `RelationshipIdentity`
- `RelationshipProjectionPolicy`
- `RelationshipProjectionReport`
- `SpecificationBuildResult`

## Threshold defaults

- CALLS per source: 128
- CALLS per project: 50,000
- IMPORTS per source package: 512
- IMPORTS per project: 20,000
- default overflow: `TRUNCATE_WITH_REPORT`

Structural relationships are not silently truncated.

## Incremental integration

- RFC-0045 stable-ID relationship diff is reused.
- RFC-0052 selects only RELATIONSHIP, PROJECT_OVERVIEW, and INDEX artifacts for
  a relationship-only change.
- unrelated Component artifacts remain KEEP.
- CLI and MCP contain no relationship interpretation or threshold rules.

## Verification

- Targeted identity/projection/semantic tests: PASS
- RFC-0052 selective planning integration: PASS
- Clean Build: PASS
- XML files: 100
- Tests: 312
- Failures: 0
- Errors: 0
- Skipped: 0
- `git diff --check`: PASS
- MCP protected paths: PASS

## Compatibility

- DIR schema remains `0.3`.
- Specification Snapshot format remains `1`.
- `RelationshipSpecification` shape remains unchanged.
- `dependencyIds` remains direct DEPENDS_ON-only.
- Review Bundle, Lifecycle, Receipt, and Journal contracts are unchanged.
- CLI remains a Thin Adapter.

## Known limitations

- The simple Kotlin extractor does not yet parse call sites automatically;
  scanners must populate `SourceCall`.
- Legacy supertype strings are promoted only for uniquely resolved in-project
  declarations whose target kind is proven.
- External or ambiguous supertype kinds require explicit
  `SourceSuperTypeReference`; Core does not guess.
- Projection Report persistence and quality gating are deferred to RFC-0054.
- No architecture-samples call-site smoke is claimed.

## Git integration

- Feature commit: NOT CREATED
- Main merge: NOT PERFORMED
- Push: NOT PERFORMED
- Release: NOT PERFORMED

## Completion readiness

Core semantic projection is implemented and locally verified. Git integration is
ready, with scanner limitations explicitly retained for follow-up planning.
