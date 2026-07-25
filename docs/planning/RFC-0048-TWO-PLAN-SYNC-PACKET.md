# RFC-0048 Two-plan Sync Packet

## Baseline

- Core branch: `main`
- RFC-0047 feature commit: `d71ed979aff27c4a84bc5e462c7ee6f7384e463b`
- RFC-0047 local merge: `27ebe07fd4b5fa5484f6ab33a3b4462afc18c397`
- Verification: 88 XML / 270 tests / 0 failures / 0 errors / 0 skipped

## Plan A

- Title: Official CLI Review Bundle Prepare, Decide, Inspect, and Apply Workflow
- Type: PRODUCT_CAPABILITY
- Purpose: expose RFC-0047 as a restart-safe developer workflow.
- Dependencies: satisfied.
- Risk: MEDIUM.
- Recommendation: natural product continuation.

## Plan B

- Title: v0.5 Release Provenance and Determinism Gate
- Type: QUALITY_RELEASE
- Purpose: bind all release evidence to one deterministic manifest.
- Dependencies: product scope freeze preferred.
- Risk: MEDIUM.
- Recommendation: release-readiness continuation.

## Comparison

| Criterion | Plan A | Plan B |
| --- | --- | --- |
| Direct user value | High | Medium |
| Release-risk reduction | Medium | High |
| Core runtime change | Wiring only | None expected |
| CLI change | Material | Verification tooling only |
| Uses RFC-0047 directly | Yes | Records its format |
| Natural next product step | Strong | Moderate |

## Suggested selection

- Select Plan A if the priority is making durable review usable by developers.
- Select Plan B if the priority is freezing and shipping v0.5.

Neither plan is approved as RFC-0048 by this packet.

## Decisions required

1. Select Plan A or Plan B.
2. Approve scope and non-goals.
3. Approve detailed RFC-0048 specification drafting.
