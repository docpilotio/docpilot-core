# RFC-0051 Two-plan Sync Packet

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

## Recommended next RFC

- Candidate: Plan A
- Proposed RFC: RFC-0051
- Why now: RFC-0050 Core capabilities are complete but recovery and lifecycle
  administration are not yet reachable through the official product interface.
- Expected scope: lifecycle status, recover, verify, supersede, archive; stable
  exit codes and JSON; Core-only rules.
- Expected non-goals: UI/TUI, MCP, remote sync, automatic recovery, retention,
  Bundle/Receipt format changes.

## Alternative

- Candidate: Plan B
- Reason not selected first: it improves scale and hardening but delivers less
  immediate product value than exposing already-complete recovery operations.

## Decisions required

1. RFC-0051 Plan A or Plan B selection.
2. CLI command naming and stable exit-code allocation if Plan A is selected.
3. Whether lifecycle recovery must require an explicit confirmation flag.
4. Whether cross-process automation is urgent enough to prioritize Plan B.
