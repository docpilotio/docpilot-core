# RFC-0051 Two-plan Sync Packet

Decision: Plan A selected for RFC-0051. Plan B remains deferred.

## Baseline

- Pre-integration baseline: `d674463c078125b3d113823a90a49c26cb77b139`
- Current completed capability: RFC-0050 Review Bundle Lifecycle and Apply Receipt
- Review Bundle: format 1 unchanged
- New RFC-0050 contracts: Lifecycle Metadata 1, Apply Receipt 1, Journal 1

## Plan A

- Title: Official Review Lifecycle Operations and Recovery CLI
- Type: PRODUCT_CAPABILITY
- Purpose: expose Core recovery, verification, supersession, and archive through
  stable thin-adapter CLI commands.
- Product value: makes RFC-0050 operational safety directly usable.
- Dependencies: RFC-0048 and RFC-0050 are satisfied.
- Risk: MEDIUM.
- Recommendation: RECOMMENDED.

## Plan B

- Title: Cross-process Review Leases and Audit-safe Retention
- Type: ARCHITECTURE_ENABLER
- Purpose: provide exclusive lifecycle mutation ownership and bounded,
  audit-preserving history management.
- Architecture value: strengthens long-running and concurrent operation.
- Dependencies: RFC-0050 is satisfied; real multi-process requirements should be
  confirmed.
- Risk: MEDIUM_HIGH.
- Recommendation: CONDITIONAL.

## Selected RFC

- Candidate: Plan A
- Proposed RFC: RFC-0051
- Why now: RFC-0050 Core capabilities are complete but recovery and lifecycle
  administration are not yet reachable through the official product interface.
- Expected scope: lifecycle status, recover, verify, supersede, archive; stable
  exit codes and JSON; Core-only rules.
- Expected non-goals: UI/TUI, MCP, remote sync, automatic recovery, retention,
  Bundle/Receipt format changes.

All mutation commands default to a Core-generated dry-run Plan. Actual mutation
requires explicit `--confirm`; automation may also bind confirmation to the
reported Plan SHA.

## Alternative

- Candidate: Plan B
- Reason not selected first: it improves scale and hardening but delivers less
  immediate product value than exposing already-complete recovery operations.

## Remaining implementation decisions

1. Final public Core operations type names.
2. Exact help text and stable JSON field ordering.
3. Whether `--plan-sha256` remains optional for manual `--confirm`.
4. Crash-injection fixture boundaries used for CLI smoke.
