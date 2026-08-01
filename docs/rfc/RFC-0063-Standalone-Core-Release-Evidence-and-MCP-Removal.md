# RFC-0063: Standalone Core Release Evidence and MCP Removal

Status: Implemented

## Purpose

Remove the temporary in-repository DocPilot MCP implementation and make Release Evidence bind
only to the Core Git candidate that the release module can independently verify.

## Decision

1. `tools/docpilot-mcp` is removed from the product repository.
2. Release Evidence Manifest format 2 replaces format 1.
3. `ReleaseCandidate` contains only `coreCommit`, `branch`, and `repositoryClean`.
4. `mcpMode`, `mcpCommit`, and `mcpVersion` are removed from collection, canonical JSON, gate
   evaluation, and Markdown output.
5. `MCP_IDENTITY_MISMATCH`, `MCP_EMBEDDED_IDENTITY`, and
   `CORE_HAS_NO_MCP_RUNTIME_DEPENDENCY` are removed from the active release policy.
6. Format 1 manifests fail closed. They are historical evidence and are not reinterpreted as
   format 2.

## Compatibility

This is an intentional Release Evidence contract version change. Existing format 1 bytes remain
historical records, but the current codec accepts only format 2. Core DIR, Snapshot, Review
Bundle, Evolution Report, CLI, provider, and documentation contracts are unchanged.

Historical RFCs and handoffs may continue to describe the MCP state that existed when they were
approved. Those records are not rewritten.

## Non-goals

- adding a replacement orchestration service;
- signing or externally attesting Release Evidence;
- changing release policy beyond removal of MCP-only identity requirements;
- changing Product Validation or PV-009 state;
- publishing, tagging, or releasing.

## Acceptance criteria

1. No tracked `tools/docpilot-mcp` implementation remains.
2. Release Evidence format 2 canonical bytes contain no MCP identity fields.
3. Collection, verification, gate evaluation, and Markdown rendering are Core-only.
4. Format 1 input is rejected with an unsupported-format failure.
5. Release module tests and the repository Gradle test suite pass.
6. README, Pipeline, Roadmap, Planning, Handoff, and validation evidence agree.
