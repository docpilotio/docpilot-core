# RFC-0049 Candidate Decision Sync Packet

## Baseline

- Core branch: `main`
- RFC-0048 feature commit: `1ac9bc9e4a67c846f724078dbe48e73b4cefdd37`
- RFC-0048 local merge: `ff31e942321f1636c431f839c8f13646027ef2d3`
- Verification: 89 XML / 273 tests / 0 failures / 0 errors / 0 skipped

## Plan A

- Title: v0.5 Release Provenance and Determinism Gate
- Type: QUALITY_RELEASE
- Value: one deterministic, machine-verifiable release decision record.
- Risk: MEDIUM.
- Recommendation: STRONGLY_RECOMMENDED.

## Plan B

- Title: Review Bundle Lifecycle and Apply Receipt
- Type: PRODUCT_CAPABILITY / ARCHITECTURE_ENABLER
- Value: durable apply proof, supersession, and archive lifecycle.
- Risk: MEDIUM-HIGH.
- Recommendation: RECOMMENDED after release provenance.

## Comparison

| Criterion | Plan A | Plan B |
| --- | --- | --- |
| Immediate release-risk reduction | High | Medium |
| New user capability | Medium | High |
| New long-term data format | Release manifest | Receipt/lifecycle |
| Runtime impact | None expected | Additive |
| Natural priority after RFC-0048 | Strong | Moderate |

## Suggested selection

Select Plan A to close v0.5 release readiness. Select Plan B only when durable
review history is more urgent than release evidence.

## Decision

- RFC-0049: Plan A selected and detailed specification approved.
- Proposed RFC-0050: Plan B retained as the preferred follow-up candidate.
- RFC-0050 number and detailed scope remain subject to separate approval.

Canonical specification:

```text
docs/rfc/RFC-0049-v0.5-Release-Provenance-and-Determinism-Gate.md
```
