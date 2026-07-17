# RFC-0039 — Incremental Documentation Execution

## Status

Implemented

## Summary

RFC-0039 connects the deterministic `IncrementalUpdatePlan` produced by RFC-0037 to documentation rendering and output execution.

The implementation preserves the existing `SpecificationRenderer` contract. Diff calculation remains outside the executor, the renderer remains presentation-only, and the writer remains unaware of specification change impact.

## Architecture

```text
Previous ProjectSpecification
Current ProjectSpecification
IncrementalUpdatePlan
Existing Documentation State
            ↓
DefaultIncrementalDocumentationExecutor
            ├── SpecificationRenderer
            └── DocumentationArtifactWriter
                    ↓
IncrementalDocumentationExecutionResult
```

## Execution modes

- `NO_CHANGES`
- `INCREMENTAL_UPDATE`
- `FULL_REGENERATION`
- `FAILED`

## Artifact operations

- `CREATE`
- `UPDATE`
- `DELETE`
- `KEEP`

Artifact identity is the normalized renderer-owned `relativePath`. Duplicate or blank renderer paths are rejected instead of being guessed.

## No-change behavior

When the supplied update plan contains no actions and a previous specification exists:

- the renderer is not called;
- the writer is not called;
- existing documentation is preserved;
- the result mode is `NO_CHANGES`.

When rendering is required but generated content and media type are unchanged, the artifact operation is `KEEP` and no write occurs.

## Full regeneration fallback

Full regeneration is selected for explicit, testable conditions:

- previous specification is missing;
- DIR schema versions differ;
- an incremental update is requested but existing documentation state is absent.

Full regeneration reconciles rendered and existing artifacts using the same deterministic operation model.

## Error handling

Renderer, artifact mapping, and writer failures return `FAILED` with an error message. Exceptions are not converted to success.

## Output integration

`DocumentationArtifactWriter` is defined as a core output port. The CLI module provides `FileDocumentationArtifactWriter`, which reuses the existing UTF-8 `OutputWriter`, supports deletion, and rejects paths escaping the configured output root.

## Scope exclusions

RFC-0039 does not add:

- snapshot persistence or migration;
- a new CLI command or incremental CLI options;
- AI incremental invocation;
- dry-run or force-full options;
- atomic multi-artifact transactions;
- schema migration.

## Verification

Added unit coverage for:

- no-change renderer/writer skip;
- create, update, delete, and keep operations;
- missing previous specification fallback;
- schema mismatch fallback;
- missing existing documentation fallback;
- renderer failure;
- writer failure;
- unchanged artifact write suppression;
- deterministic action ordering;
- file writer path containment.

The new core source set and CLI adapter compile successfully with the locally available Kotlin compiler. A focused runtime execution harness also passed. The Gradle wrapper could not complete in the isolated environment because the Gradle 9.3.0 distribution was not cached and external network access was unavailable.
