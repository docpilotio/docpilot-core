# RFC-0052 Two-plan Sync Packet

Status: SUPERSEDED by the Product Roadmap realignment.

Neither former plan is selected for RFC-0052:

- Lease/Retention moves to RFC-0056+/v1.1 Hardening.
- Signed Evidence/Attestation moves to RFC-0057+/v1.1 Hardening.

RFC-0052 now targets Selective Documentation Artifact Planning and Rendering.

## Baseline

- Main baseline before RFC-0051 integration:
  `60704a254f7d90a0ea9c00e9490d06bf6e917b26`
- RFC-0051: implemented and locally verified
- Tests: 97 XML / 301 tests / 0 failures
- Lifecycle operations: official Core-owned thin-adapter CLI

## Plan A

- Title: Cross-process Review Leases and Audit-safe Retention
- Type: ARCHITECTURE_ENABLER
- Purpose: prevent concurrent local lifecycle mutations and bound retained
  history without deleting authoritative audit/recovery evidence.
- Product value: safer automation and sustainable long-running repositories.
- Dependencies: RFC-0050 and RFC-0051 satisfied.
- Risk: MEDIUM_HIGH.
- Recommendation: RECOMMENDED.

## Plan B

- Title: Signed Release Evidence and External Attestation
- Type: QUALITY_RELEASE
- Purpose: add signer provenance and optional external attestations over the
  unchanged RFC-0049 manifest.
- Product value: enables external trust and supply-chain integration.
- Dependencies: RFC-0049 satisfied.
- Risk: MEDIUM_HIGH.
- Recommendation: CONDITIONAL.

## Recommended next RFC

- Candidate: Plan A
- Proposed RFC: RFC-0052
- Why now: lifecycle commands are automation-ready, but mutation ownership is
  still only protected at publication time and retained history is unbounded.
- Expected scope: Core lease/fencing contract, filesystem adapter, retention
  planning, dry-run/confirm, offline verification, multi-process tests.
- Expected non-goals: distributed consensus, remote locks, automatic deletion,
  MCP ownership, contract version changes, signing.

## Alternative

- Candidate: Plan B
- Reason not selected first: external trust is valuable, but it does not mitigate
  concurrent lifecycle mutation or unbounded local audit storage.

## Decisions required

1. RFC-0052 Plan A or Plan B selection.
2. Whether retention execution belongs in the same RFC as lease enforcement.
3. Minimum protected lifecycle history.
4. Whether lease operational commands are required or Core-only is sufficient.
5. If Plan B is selected, initial signing algorithm and trust-policy boundary.
