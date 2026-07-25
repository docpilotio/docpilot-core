# RFC-0054 Candidate Sync Packet

## Baseline

- Predecessor: RFC-0053 Semantic Relationship Expansion
- RFC-0053 implementation: COMPLETE_WITH_LIMITATIONS
- RFC-0053 verification: 100 XML / 312 tests / 0 failures
- RFC-0053 Feature Commit: `3411424`
- RFC-0053 Main integration: `a0c4a9c`
- DIR schema: `0.3`
- Snapshot format: `1`
- Next track: Product Capability

## Plan A

- Title: Documentation Quality Validation
- Purpose: versioned Core findings, policy, deterministic report, offline
  verification, and pass/fail gate.
- Uses RFC-0052: artifact catalog and inventory.
- Uses RFC-0053: projection counts, overflow, omission, unresolved Evidence.
- Product value: VERY HIGH
- Complexity: MEDIUM-HIGH
- Risk: premature or overly strict rules
- Recommendation: STRONGLY_RECOMMENDED

## Plan B

- Title: Scanner Relationship Observation Coverage
- Purpose: populate SourceCall and explicit supertype observations from normal
  Kotlin analysis.
- Uses RFC-0053: existing resolution, identity, aggregation, and threshold.
- Product value: HIGH
- Complexity: HIGH
- Risk: false positives and compiler-grade scope growth
- Recommendation: CONDITIONAL

## Recommended RFC-0054

- Candidate: Plan A
- Proposed title: Documentation Quality Validation
- Why now: RFC-0052 and RFC-0053 provide stable artifacts and structured
  semantic-loss facts; the next product need is a deterministic quality
  decision contract.
- Expected non-goals: AI truth inference, auto-fix, reconciliation, scanner
  compiler frontend, UI, and MCP.

## Alternative sequencing

```text
RFC-0054 Documentation Quality Validation
    ->
bounded Scanner Relationship Observation Coverage
    ->
RFC-0055 Existing Documentation Reconciliation
```

Plan A must represent unavailable scanner capabilities as NOT_EVALUATED rather
than incorrectly failing missing CALLS coverage.

## Decisions required

1. Plan A를 RFC-0054 상세 명세 대상으로 승인할지
2. 초기 정책을 report-only로 둘지
3. ERROR 기본 규칙 범위
4. scanner prerequisite의 NOT_EVALUATED 처리
5. Plan B를 별도 후속 RFC로 유지할지
