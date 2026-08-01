# RFC-0068 — Official Documentation Generation CLI

## Decision

DocPilot exposes deterministic official documentation generation through:

```text
docpilot generate docs --project <path> --output <path>
```

Preview is the default and performs no output or Snapshot writes. `--confirm` applies the canonical plan; `--plan-sha256` binds apply to a prior preview. `--full`, repeatable `--artifact`, and repeatable `--document-type` select artifacts. `--json` emits one schema-versioned result on stdout.

## Architecture and safety

The CLI only parses options, renders the structured result and maps exit codes. `DefaultDocumentationGenerationWorkflow` owns orchestration: project analysis, DIR construction, exact Profile resolution, Artifact selection and dependency closure, rendering, safe-path and ownership checks, Plan hashing, application, ownership manifest persistence, and Snapshot-last persistence.

The analyzed project is read-only. Documents, ownership state, and the Specification Snapshot are stored below the explicit output root. Absolute paths, traversal, source-root overlap, symlink output roots, unknown ownership, and locally modified generated files are blocked. `NO_CHANGES` does not rewrite output.

## Compatibility and boundaries

`generate specification` remains available. Snapshot Format 3 and DIR 0.5 are unchanged. RFC-0068 does not implement Bundle/Manifest, AI enrichment, Diagram IR, claims, or a new Reconciliation workflow.
