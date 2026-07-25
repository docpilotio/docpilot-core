# RFC-0051 Candidate Plan A

# Official Review Lifecycle Operations and Recovery CLI

Status: SELECTED for RFC-0051. Detailed contract:
`docs/rfc/RFC-0051-Official-Review-Lifecycle-Operations-and-Recovery-CLI.md`.

## Type

PRODUCT_CAPABILITY

## Problem

RFC-0050 makes lifecycle recovery, supersession, archive, and offline
verification Core contracts, but operators cannot invoke those contracts through
the official CLI. A failed apply can therefore become safely recoverable in Core
while remaining operationally inaccessible without custom code.

## Proposed outcome

Expose the RFC-0050 services as official thin-adapter commands:

```text
review lifecycle status
review lifecycle recover
review lifecycle verify
review lifecycle supersede
review lifecycle archive
```

Every mutation delegates transition validation, integrity checks, recovery
classification, and Receipt handling to Core.

Mutation commands are dry-run by default. Durable mutation requires explicit
`--confirm`, and automation can bind confirmation to the Core-generated
`--plan-sha256`.

## Goals

- stable CLI commands and exit codes for all RFC-0050 operational states;
- human and `--json` output with proposal, bundle SHA, lifecycle generation,
  state, transaction ID, and Receipt ID;
- explicit recovery with dry inspection before mutation;
- offline verification that requires no provider or network;
- explicit supersede and archive operations;
- exact-path and project-relative control-directory support;
- CLI tests proving that no lifecycle rule is duplicated outside Core.

## Non-goals

- UI/TUI;
- MCP lifecycle tools;
- remote synchronization;
- changes to Review Bundle v1 or Receipt v1;
- automatic recovery without an explicit command;
- deletion or retention policy.

## Dependencies

- RFC-0050 Lifecycle Metadata, Receipt, Journal, repository, and Core services.
- RFC-0048 stable CLI envelope and exit-code policy.

## Expected change areas

- `docpilot-cli/.../command/review/**`
- CLI workflow tests and isolated filesystem smoke fixtures
- minimal Core result-shape additions only when presentation-neutral information
  is not currently exposed

## Risks

- accidentally reimplementing state-machine rules in CLI;
- ambiguous recovery UX;
- unstable exit-code mapping;
- exposing filesystem implementation details as permanent CLI semantics.

## Verification

- command contract and JSON golden tests;
- crash-boundary recovery matrix through CLI;
- exact idempotent apply/verify smoke;
- invalid/tampered lifecycle and Receipt cases;
- Core dependency-direction check;
- full regression build.

## Priority

RECOMMENDED

This is the most direct product completion step after RFC-0050 because it makes
already-implemented safety and recovery capabilities usable without expanding
the Core contracts.
