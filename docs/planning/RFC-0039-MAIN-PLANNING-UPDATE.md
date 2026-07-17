# RFC-0039 Main Planning Update

## Project Status

```text
Current Phase
  Phase 2 — Post-MVP Evolution

Completed RFC
  RFC-0001 ~ RFC-0039

Current RFC
  RFC-0040 — Specification Snapshot Persistence

Current Release
  v0.5 MVP

Release Tag
  release/v0.5.0
```

## RFC-0039 Summary

RFC-0039 implemented the application execution layer that consumes RFC-0037's deterministic `IncrementalUpdatePlan` and connects it to the existing specification renderer and an output writer port.

### New core types

- `IncrementalDocumentationExecutor`
- `DefaultIncrementalDocumentationExecutor`
- `IncrementalDocumentationExecutionRequest`
- `IncrementalDocumentationExecutionResult`
- `IncrementalExecutionMode`
- `DocumentationArtifactOperation`
- `DocumentationArtifactAction`
- `ExistingDocumentationArtifact`
- `IncrementalFallbackReason`
- `DocumentationArtifactWriter`

### New infrastructure type

- `FileDocumentationArtifactWriter`

### Public API impact

Additive only. Existing Builder, DIR 0.3, `SpecificationRenderer`, and `ProjectSpecificationMarkdownRenderer` contracts were not changed.

## Architecture Update

```text
IncrementalUpdatePlan
        ↓
DefaultIncrementalDocumentationExecutor
        ├── decides NO_CHANGES / INCREMENTAL_UPDATE / FULL_REGENERATION
        ├── invokes SpecificationRenderer
        ├── calculates CREATE / UPDATE / DELETE / KEEP
        ├── skips unchanged writes
        └── invokes DocumentationArtifactWriter
```

Renderer responsibility remains limited to rendering `ProjectSpecification`. Writer responsibility remains limited to applying explicit artifact output operations.

## Major Decisions

- Renderer-owned relative paths are the current Artifact identity.
- No-change plans bypass rendering and writing.
- Unsafe incremental preconditions fall back to full regeneration.
- Existing artifacts with identical content and media type are retained without rewrite.
- Core defines the writer port; CLI owns the file-system adapter.

## ADR Candidates

- Full regeneration as the safe fallback for invalid incremental preconditions.
- Renderer relative path as Artifact identity until richer Artifact IDs are introduced.
- No-change and identical-content writer suppression.
- Core output port with infrastructure-owned file adapter.

## Verification Result

- New core execution sources: compile PASS
- CLI file adapter sources: compile PASS
- Focused runtime execution harness: PASS
- Gradle clean build/full regression: NOT RUN in the isolated environment because Gradle 9.3.0 was not cached and the distribution download was blocked by unavailable external network access

## Known Limitations / Technical Debt

- Existing documentation state is supplied by the caller; filesystem state discovery is not part of RFC-0039.
- Multi-artifact execution is deterministic but not transactionally atomic.
- Artifact identity currently uses relative path rather than a persisted independent Artifact ID.
- Schema migration is unsupported.
- Snapshot load/save orchestration remains for RFC-0040.
- CLI workflow remains for RFC-0041.
- AI incremental generation remains for RFC-0042.

## RFC-0040 Input

RFC-0040 should persist enough state to reconstruct the inputs needed by RFC-0039:

- previous `ProjectSpecification`;
- schema version;
- rendered Artifact identity, path, media type, and content fingerprint or content;
- snapshot format version;
- project identity;
- deterministic ordering metadata only where necessary.

A persistence port should remain independent of JSON and filesystem details. Migration and compatibility policy should be explicit rather than inferred.

## Commit Information

### Branch

```text
feature/rfc-0039-incremental-documentation-execution
```

### Commit title

```text
feat(incremental): execute incremental documentation updates
```

### Commit body

```text
Add an application executor for IncrementalUpdatePlan results.

- model execution modes and artifact operations
- skip rendering and writing when no changes exist
- reconcile create, update, delete, and keep operations
- fall back to full regeneration for unsafe preconditions
- add a core writer port and CLI filesystem adapter
- preserve existing builder and renderer contracts
- add execution and writer tests
```

### Tag

No new product release tag is required for RFC-0039. Keep `release/v0.5.0` as the current release baseline.
