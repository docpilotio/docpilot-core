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

## Recommendation

Select Plan A for RFC-0050. It follows the agreed roadmap from v0.5 release trust
to v1.0 review auditability and long-term operation.

Keep Plan B as a later release-security candidate. Neither candidate is formally
approved as RFC-0050 by this packet.

## Decisions required

1. RFC-0050 candidate selection
2. Lifecycle states and terminal-transition rules
3. Receipt atomicity boundary
4. Existing Bundle compatibility policy
5. CLI exposure inclusion or deferral
