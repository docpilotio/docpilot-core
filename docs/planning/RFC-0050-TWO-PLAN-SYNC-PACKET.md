# RFC-0050 Two-plan Sync Packet

## Baseline

- RFC-0049: implemented and locally verified
- Release Evidence Manifest: format 1
- Full verification: 95 XML / 287 tests / 0 failures / 0 errors / 0 skipped
- Main integration and remote push: approved, final identities reported separately

## Plan A

- Title: Review Bundle Lifecycle and Apply Receipt
- Type: PRODUCT_CAPABILITY / ARCHITECTURE_ENABLER
- Purpose: close the durable audit chain from proposal and decisions through one
  exact applied result.
- Product value: high
- Architecture value: high
- Risk: medium-high
- Recommendation: STRONGLY_RECOMMENDED

## Plan B

- Title: Signed Release Evidence and External Attestation
- Type: QUALITY_RELEASE / ARCHITECTURE_ENABLER
- Purpose: add signer authenticity and portable attestation to RFC-0049 evidence.
- Product value: medium
- Architecture value: medium-high
- Risk: medium-high, security-sensitive
- Recommendation: RECOMMENDED AFTER PLAN A

## Comparison

| Criterion | Plan A | Plan B |
| --- | --- | --- |
| Immediate user workflow value | High | Medium |
| v1.0 audit path | Direct | Supporting |
| Builds on | RFC-0046/47/48 | RFC-0049 |
| New durable contract | Apply Receipt + lifecycle | Detached signature |
| Security sensitivity | Medium | High |
| Natural next priority | Strong | Moderate |

## Decision

- RFC-0050: Plan A selected and detailed specification approved.
- Plan B: retained as a later release-security candidate.
- Review Bundle format 1 remains unchanged.
- Lifecycle Metadata, Apply Receipt, and Apply Transaction Journal use independent
  format version 1 contracts.

Canonical RFC:

```text
docs/rfc/RFC-0050-Review-Bundle-Lifecycle-and-Apply-Receipt.md
```

## Decisions required

Resolved by the Canonical specification:

1. Plan A selected.
2. Lifecycle uses a closed Core-owned state machine.
3. Receipt and APPLIED lifecycle commit through one atomic control generation.
4. Review Bundle v1 remains unchanged; legacy adoption is explicit.
5. CLI and MCP remain thin adapters.
