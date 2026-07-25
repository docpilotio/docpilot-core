# RFC-0053 Main Planning Update

## Dashboard

| Item | State |
| --- | --- |
| Track | Product Capability |
| RFC | RFC-0053 |
| Selected candidate | Plan A |
| Title | Semantic Relationship Expansion |
| Detailed specification | COMPLETE |
| Implementation | COMPLETE_WITH_LIMITATIONS |
| Clean build | PASS |
| Full regression | PASS — 100 XML / 312 tests |
| Main integration | NOT_STARTED |

## Baseline

- Main: `fde1700f9a71b8aa5da2ac08928323ab380ef42d`
- RFC-0052: implemented, verified, and integrated
- DIR schema: `0.3`
- Snapshot format: `1`
- Verified suite: 98 XML / 306 tests / 0 failures

## Selected scope

- `EXTENDS`, `IMPLEMENTS`, `CALLS`, and `IMPORTS`
- Evidence-required deterministic relationship identity
- deterministic endpoint resolution and aggregation
- Core-owned versioned threshold policy
- Core-owned Relationship Projection Report format 1
- RFC-0045 relationship incremental reuse
- RFC-0052 relationship artifact and summary selection reuse
- RFC-0054 quality-validation inputs

## Architecture decisions

- logical identity is kind plus normalized endpoints;
- repeated CALLS/IMPORTS merge Evidence, not identity;
- threshold runs after aggregation;
- retained sets use canonical deterministic ordering;
- overflow is visible through a versioned Core report;
- default overflow is `TRUNCATE_WITH_REPORT`;
- structural relationships are never silently truncated;
- dependencyIds remains direct DEPENDS_ON-only;
- CLI and MCP contain no interpretation or policy.

## Proposed implementation stages

1. Semantic kind and canonical identity utility.
2. Source observation contracts for supertypes and call sites.
3. type/call/import endpoint resolution.
4. Evidence-required aggregation.
5. versioned projection policy and report.
6. deterministic threshold and overflow behavior.
7. DIR projection/validation.
8. RFC-0045/RFC-0052 integration verification.
9. scale fixtures and deterministic benchmarks.
10. isolated architecture-samples smoke.
11. Completion Handoff and Roadmap evidence.

## Completion gate

- canonical identities for all official kinds;
- no arbitrary ambiguity resolution;
- all emitted relationships have Evidence;
- stable aggregation and threshold output;
- report counts/digests/SHA verify;
- structural relations do not silently truncate;
- dependencyIds remains DEPENDS_ON-only;
- exact selective relationship artifact updates;
- CLI/MCP rule duplication absent;
- full and scale tests pass.

## Implementation evidence

- canonical length-framed relationship identity: COMPLETE
- official five-kind allowlist: COMPLETE
- Evidence-required aggregation: COMPLETE
- CALLS/IMPORTS deterministic threshold policy: COMPLETE
- Projection Report format 1: COMPLETE
- enriched Specification Build result: COMPLETE
- RFC-0045/RFC-0052 integration tests: PASS
- Clean Build: PASS
- Full regression: 100 XML / 312 tests / 0 failures
- CLI/MCP semantic changes: NONE

## Known limitations

- The current simple Kotlin extractor does not automatically parse call sites.
  Scanner integrations must populate the Core `SourceCall` contract.
- Legacy `superTypes` are promoted only when a unique in-project declaration
  proves whether the target is a class or interface.
- External or ambiguous supertype syntax is not guessed. Explicit scanner
  `SourceSuperTypeReference` evidence is required.
- Projection Report persistence and quality pass/fail rules remain RFC-0054
  candidates.

## Explicit non-goals

- transitive graph analysis
- runtime tracing
- AI inference
- Documentation Quality pass/fail rules
- existing Markdown reconciliation
- CLI/UI/MCP relationship semantics
- v1.1 hardening

## Decisions captured

- Plan A selected.
- Plan B remains the proposed RFC-0054 direction.
- DIR schema remains 0.3.
- Projection Report is a separate format-1 Core contract.
- Threshold default numbers require benchmark confirmation during implementation.

## Canonical sources

- `docs/rfc/RFC-0053-Semantic-Relationship-Expansion.md`
- `docs/planning/RFC-0053-MAIN-PLANNING-UPDATE.md`
- `docs/planning/RFC-0053-CANDIDATE-PLAN-A-SEMANTIC-RELATIONSHIP-EXPANSION.md`
- `docs/planning/RFC-0053-TWO-PLAN-SYNC-PACKET.md`
- `docs/roadmap/ROADMAP.md`
