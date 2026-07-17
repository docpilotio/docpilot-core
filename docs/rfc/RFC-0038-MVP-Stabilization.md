# RFC-0038 — MVP Stabilization

## Status

Implemented for v0.5 MVP release-candidate validation.

## Goal

Stabilize the existing MVP without adding new product features or changing renderer, builder, provider, CLI, or output-writer contracts.

## Changes

- Preserve both previous and current owning Type scopes when an API or Property moves while retaining its Stable ID.
- Preserve both previous and current Package scopes when a Type moves while retaining its Stable ID.
- Keep `SpecificationDiff`, `SpecificationChange`, and `IncrementalUpdatePlan` public APIs unchanged.
- Reject blank DIR entity IDs, invalid Component-to-Package references, cross-module Package references, and blank relationship endpoints during specification validation.
- Add deterministic and ownership-move incremental regression tests.
- Clarify the DIR 0.2 legacy baseline and DIR 0.3 builder-output policy.

## Non-goals

No new analyzer, renderer, provider, CLI command, code generator, schema version, or architectural refactoring is introduced.

## Release decision

Release readiness requires a successful `clean build`, all tests passing, and a CLI smoke test in a network-enabled environment with Gradle 9.3 available.
