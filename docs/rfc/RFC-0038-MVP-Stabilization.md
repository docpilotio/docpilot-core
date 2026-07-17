# RFC-0038 — MVP Stabilization

## Status

Accepted and implemented for the v0.5 MVP release candidate.

Release-gate execution completed on July 17, 2026.

## Goal

Stabilize the existing MVP without adding new product features or changing renderer, builder, provider, CLI, or output-writer contracts.

## Changes

- Preserve both previous and current owning Type scopes when an API or Property moves while retaining its Stable ID.
- Preserve both previous and current Package scopes when a Type moves while retaining its Stable ID.
- Keep `SpecificationDiff`, `SpecificationChange`, and `IncrementalUpdatePlan` public APIs unchanged.
- Reject blank DIR entity IDs, invalid Component-to-Package references, cross-module Package references, and blank relationship endpoints during specification validation.
- Add deterministic and ownership-move incremental regression tests.
- Clarify the DIR 0.2 legacy baseline and DIR 0.3 builder-output policy.
- Record build, test, core CLI, Ollama Provider, and invalid-provider release evidence.

## Non-goals

No new analyzer, renderer, provider, CLI command, code generator, schema version, or architectural refactoring is introduced.

OpenAI real API invocation is outside the v0.5 release-validation scope.

## Release evidence

- `./gradlew clean build`: PASS
- `./gradlew test`: PASS
- `./gradlew :run --args="analyze C:\WorkSpace\architecture-samples"`: PASS
- Ollama `qwen3:8b` architecture generation: PASS, HTTP 200
- Invalid-provider handling: PASS, expected non-zero exit

Detailed evidence is stored under `snapshots/v0.5-mvp/`.

## Release decision

The required technical runtime gates passed. Artifact-version policy remains the only unresolved release metadata item; the build currently uses `0.1.0-SNAPSHOT`.
