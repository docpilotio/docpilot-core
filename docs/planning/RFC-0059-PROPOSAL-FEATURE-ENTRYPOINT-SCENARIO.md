# RFC-0059 Proposal — Feature, Entry Point, and Scenario Specification Foundation

## Proposed status

`PROPOSED / NOT_STARTED`

## Motivation

RFC-0058 now defines deterministic document contracts and correctly defers Feature Catalog and Feature Specification documents because DIR 0.3 has no canonical Feature production model.

The next product gap is therefore not prose rendering. It is the absence of stable specification entities that can prove:

- which user-visible or system Feature exists;
- which Entry Points activate it;
- which Components participate;
- which ordered Scenario steps are supported by source Evidence;
- which branches, failures, and unknowns remain unresolved.

## Proposed objective

Introduce the smallest Evidence-first production foundation for:

```text
FeatureSpecification
EntryPointSpecification
ScenarioSpecification
ScenarioStepSpecification
```

The RFC should define Stable IDs, ownership links, Evidence requirements, deterministic ordering, validation, diff compatibility, and migration policy before Feature documents become renderable.

## Recommended boundary

```text
SourceIndex + Knowledge Model + existing DIR 0.3 entities
        ↓
Deterministic Feature/EntryPoint/Scenario projection
        ↓
Explicit DIR schema evolution proposal
        ↓
Profile Resolution re-evaluation
```

RFC-0059 should not implement runtime call-path extraction, AI Feature invention, Diagram IR, Mermaid, or full business behavior inference.

## Required design comparison

At least two candidates should be compared:

1. DIR 0.4 additive entities with Snapshot format 2 and retained format 1 readers.
2. Runtime-only Feature projection beside DIR 0.3, followed by later persistence.

The selected candidate must explain compatibility with RFC-0037 diff, RFC-0040 Snapshot, RFC-0045 relationship impact, RFC-0052 Artifact Plan, RFC-0056 Evolution, and RFC-0058 Profile Resolution.

## Proposed completion conditions

- Feature, Entry Point, Scenario, and Scenario Step contracts exist.
- Stable IDs do not depend on display names, timestamps, or AI narrative.
- every produced Feature and Scenario has bounded Evidence.
- ambiguous candidates remain unresolved instead of being merged arbitrarily.
- Profile Resolution can move Feature Catalog and Feature Specification from `DEFERRED` only when the new model is present and valid.
- DIR and Snapshot migration behavior is explicit and tested.
- existing DIR 0.3 readers remain available.
- public v1.0, PV-009, and v1.1 RC states remain unchanged.
