# RFC-0048 Candidate Plan A: Official CLI Review Workflow

## Status

PROPOSED - NOT APPROVED AS RFC-0048

## Proposed title

Official CLI Review Bundle Prepare, Decide, Inspect, and Apply Workflow

## Type

PRODUCT_CAPABILITY

## Problem

RFC-0047 provides durable Core review bundles and restart-safe apply, but users
cannot operate the workflow through the official CLI. Review remains accessible
only through programmatic Core APIs.

## Product outcome

A developer can prepare a review bundle, inspect its deterministic report, record
explicit decisions, and apply a complete non-stale review through separate CLI
processes with stable exit codes and no implicit approval.

## Proposed commands

```text
docpilot review prepare <project>
docpilot review inspect <project> --proposal <id>
docpilot review decide <project> --proposal <id> --target <id> --accept|--reject
docpilot review apply <project> --proposal <id>
```

Exact command spelling remains a specification decision.

## Goals

1. Add CLI adapters over RFC-0047 Core ports.
2. Prepare and persist one Review Bundle without applying it.
3. Render deterministic human-readable inspection output.
4. Record one or more explicit decisions using expected bundle integrity.
5. Apply only a complete, valid, non-stale bundle.
6. Expose proposal ID and current payload checksum after every mutation.
7. Provide stable machine-readable output and exit codes.
8. Preserve source documentation on pending, conflict, or failure.

## Non-goals

- Interactive TUI or full-screen UI.
- MCP commands or state.
- Remote review collaboration.
- Authentication or signatures.
- Automatic approval.
- File deletion outside RFC-0046 managed blocks.
- Review Bundle format change unless a proven adapter requirement demands v2.

## Dependencies

- RFC-0047 Review Bundle v1: satisfied locally.
- RFC-0046 explicit REMOVE and stale-base check: satisfied.
- Existing CLI module and project loading: satisfied.

## Architecture

```text
CLI argument parsing
        ->
Core PersistentDocumentationReviewWorkflow
        ->
ReviewBundleRepository
        ->
deterministic CLI result/exit code
```

CLI must not duplicate codec, identity, integrity, decision merge, or apply rules.

## Exit-code candidates

```text
0  success
2  invalid arguments
3  review pending/incomplete
4  bundle/document conflict
5  invalid/corrupt bundle
6  generation/provider failure
7  repository I/O failure
```

## Expected change areas

- `docpilot-cli` command parsing and orchestration
- Core composition/bootstrap only where dependency wiring is required
- CLI-focused fixtures and end-to-end tests
- CLI usage documentation

No MCP path is in scope.

## Verification

- prepare writes one valid bundle;
- inspect is deterministic and read-only;
- partial decisions persist;
- stale expected checksum returns conflict;
- restart between every command works;
- complete decisions apply;
- pending/incomplete apply leaves documentation unchanged;
- stale documentation blocks apply;
- corrupt bundle maps to stable exit code;
- accepted REMOVE operates through CLI;
- paths containing spaces work on Windows;
- full build/test and isolated CLI end-to-end smoke.

## Risks

- Command UX can accidentally imply approval.
- Shell quoting of target IDs/comments can be platform-sensitive.
- Applying documentation introduces an adapter-level write boundary.
- Exit codes become a compatibility contract.

## Complexity

MEDIUM

## Recommendation

STRONGLY_RECOMMENDED as the natural product continuation after RFC-0047.

## Decisions required

1. Approve Plan A as RFC-0048.
2. Approve command and exit-code contract design.
3. Decide whether decision comments are accepted by flag or file input.
4. Define the documentation artifact path used by apply.
5. Confirm non-interactive commands only.
