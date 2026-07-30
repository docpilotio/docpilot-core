# RFC-0059 — Feature, Entry Point, and Scenario Specification Foundation

Status: Implemented under the approved DIR 0.4 additive production design.

RFC-0059 adds Evidence-first `FeatureSpecification`,
`EntryPointSpecification`, `ScenarioSpecification`, and nested ordered
`ScenarioStepSpecification` entities to `ProjectSpecification`. Producers must
supply deterministic Stable IDs and explicit owner, participant, API, Entry
Point, Scenario, Evidence, and UnresolvedItem references. Core never invents a
missing feature, flow, owner, or ambiguous target.

The manual `ProjectSpecification` default remains DIR 0.2 and
`DefaultSpecificationBuilder` remains DIR 0.3 with empty additive collections.
Feature discovery is RFC-0060 scope.

DIR 0.4 collections use stable-ID canonical order. Scenario Step identity is
independent of numeric `order`; canonical ordering is `order` then Stable ID.
Validation rejects unsupported DIR versions and kinds, duplicate IDs and order
values, dangling references, missing Evidence, and inconsistent Feature and
Scenario ownership.

Snapshot format 2 is the strict DIR 0.4 representation. Format 1 remains the
DIR 0.3 representation and reader. Explicit migration preserves every existing
entity and Stable ID, changes the schema/envelope versions, and initializes
Features, Entry Points, and Scenarios empty without AI or inference.

Stable-ID diff and incremental planning cover add, remove, modify, and reorder
of all four new entity kinds. RFC-0056 Evolution Report format 1 and RFC-0052
artifact contracts remain unchanged. Feature Markdown is not rendered.

Profile Resolution keeps an empty Feature model `DEFERRED`. Valid DIR 0.4
Features allow Feature Catalog and per-Feature Specification contracts to
resolve `READY` or `PARTIAL` without changing Profile identity or semantic hash
rules.
