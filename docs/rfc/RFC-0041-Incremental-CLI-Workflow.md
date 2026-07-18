# RFC-0041 — Incremental CLI Workflow

## Status

Implemented

## Purpose

Connect the RFC-0039 incremental documentation executor and RFC-0040 specification snapshot persistence to the distributable CLI without changing the existing AI architecture and ADR commands.

## CLI Contract

```text
docpilot generate specification --project <path> [--output <directory>]
```

When `--output` is omitted, generated documentation is written under the project root.

## Workflow

```text
CLI
  -> source analysis
  -> ProjectSpecification builder
  -> specification snapshot load and validation
  -> incremental planning
  -> incremental executor
  -> specification renderer
  -> file artifact writer
  -> snapshot save after successful execution
```

## Execution Modes

The CLI reports one of:

- `NO_CHANGES`
- `INCREMENTAL_UPDATE`
- `FULL_REGENERATION`
- `FAILED`

It also reports the snapshot validation state and fallback reason when present.

## Exit Codes

- `0`: successful execution
- `1`: generation or unexpected execution failure
- `2`: unsupported top-level CLI usage (existing behavior)
- `3`: snapshot load/format failure that prevents execution
- `4`: snapshot save failure after documentation execution

## Compatibility

No existing architecture or ADR command is changed. Core Builder, Renderer, Executor, and Snapshot public contracts remain unchanged.
