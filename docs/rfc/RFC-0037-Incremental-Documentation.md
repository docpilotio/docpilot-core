# RFC-0037 — Incremental Documentation

## Status

Implemented

## Summary

RFC-0037 adds a renderer-agnostic incremental documentation layer that compares a previous and current `ProjectSpecification` using stable entity identities. It produces a deterministic `SpecificationDiff` and an `IncrementalUpdatePlan` without changing the Source Scanner, Specification Builder, Markdown Renderer, CLI, or AI Provider pipelines.

## Scope

Included:

- Package, type, API, and property change detection
- Added, removed, and modified classification
- Stable identity validation
- Nested API/property ownership tracking
- Changed type and package propagation
- Deterministic update planning
- Unit and integration-style tests

Excluded:

- Selective Markdown rendering
- Output writing
- CLI integration
- Snapshot persistence integration
- AI prompt/provider integration
- Source Scanner and Specification Builder changes

## Architecture

```text
Previous ProjectSpecification
            │
Current ProjectSpecification
            │
            ▼
DefaultSpecificationDiffer
            │
            ▼
SpecificationDiff
            │
            ▼
DefaultIncrementalSpecificationPlanner
            │
            ▼
IncrementalUpdatePlan
```

The existing file-snapshot incremental pipeline remains unchanged. RFC-0037 is isolated under `io.docpilot.core.incremental.specification` to avoid responsibility overlap.

## Stable identity rules

- Package identity: `PackageSpecification.id`
- Type identity: `ComponentSpecification.id`
- API identity: `ApiSpecification.id`
- Property identity: `PropertySpecification.id`
- IDs must be non-blank and unique within each compared entity category.
- Matching IDs represent the same logical entity.
- Missing old/new IDs represent added or removed entities.
- Matching IDs with different comparable content represent modified entities.

Type comparison excludes nested API and property collections. This prevents an API-only or property-only change from also being reported as a type-body modification. The planner still propagates nested changes to their owning type and package.

## Renderer integration

The existing `SpecificationRenderer.render(ProjectSpecification)` contract is unchanged. RFC-0037 only produces update targets. Selective rendering and output replacement remain future integration work.

## Public API

New public types are contained in `io.docpilot.core.incremental.specification`:

- `SpecificationDiffer`
- `DefaultSpecificationDiffer`
- `SpecificationDiff`
- `SpecificationChange`
- `ChangeKind`
- `IncrementalSpecificationPlanner`
- `DefaultIncrementalSpecificationPlanner`
- `IncrementalUpdatePlan`
- `IncrementalUpdateAction`
- `IncrementalUpdateTarget`
- `IncrementalDocumentationEngine`
- `IncrementalDocumentationResult`

No existing public API was removed or changed.
