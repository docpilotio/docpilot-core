# Main Planning Update — RFC-0038

## Project Dashboard

- Current Phase: Phase 1 — MVP
- Current RFC: RFC-0038 — Completed pending environment release-gate execution
- Next RFC: RFC-0039
- Completed RFC: RFC-0001 through RFC-0038
- Planned RFC: RFC-0039 through RFC-0064
- Next Release: v0.5 MVP

## RFC completion summary

1. Stabilized Specification Incremental owner propagation without changing public APIs.
2. Added previous/current Type propagation for moved APIs and Properties.
3. Added previous/current Package propagation for moved Types.
4. Strengthened DIR ID and Package-reference validation.
5. Added deterministic incremental regression coverage.
6. Clarified DIR 0.2 legacy and DIR 0.3 builder-output policy.
7. Preserved renderer-only presentation responsibility.
8. Added release checklist and handoff documentation.
9. Produced a clean source ZIP excluding local and generated build state.

## Phase progress

| Capability | Status |
|---|---|
| Source Scanner | Complete |
| Knowledge Builder | Complete |
| Specification Builder / DIR 0.3 | Complete |
| Markdown Renderer | Complete |
| Specification Incremental | Stabilized |
| Validation | MVP baseline strengthened |
| Full Regression Evidence | Pending target environment execution |
| v0.5 Release | Conditional |

## ADR candidates

- Incremental affected scopes include both previous and current owners when a Stable-ID entity changes ownership.
- DIR 0.2 remains a compatibility baseline while the canonical builder output is DIR 0.3.
- Stabilization validation should reject structural contradictions but remain permissive for potentially external symbolic references.

## Technical debt

- Resolve artifact version versus product milestone version policy.
- Record full Gradle and CLI smoke evidence in a network-enabled JDK 21 environment.
- Define Relationship and dependency endpoint semantics in a future RFC.

## v0.5 MVP Release Readiness

- Build Status: Not verified in restricted environment
- Test Status: New regression tests added; full suite execution pending
- Known Issue: Artifact version remains `0.1.0-SNAPSHOT`
- Blocking Issue: Full build/test and CLI smoke evidence missing
- Remaining Work: Execute release checklist and resolve version policy
- Release Possible: Yes, conditionally after release gates pass

## RFC-0039 start prompt

```text
# DocPilot RFC-0039

This conversation is dedicated to RFC-0039 only.

Baseline:
- Phase 1 — MVP
- RFC-0001 through RFC-0038 completed
- Source Scanner, Knowledge Builder, DIR 0.3 Specification Builder, Markdown Renderer, and Stable-ID Specification Incremental are implemented
- RFC-0038 stabilized previous/current ownership propagation and DIR structural validation
- Renderer consumes ProjectSpecification only
- Snapshot Incremental and Specification Incremental remain separate subdomains

Before implementation, verify that the v0.5 release checklist full Gradle build, complete test suite, and CLI smoke gate have passed in the target environment. Preserve Clean Architecture, Evidence First, Constitution principles, existing public contracts, project structure, and test style. Do not change earlier RFC designs or perform unrelated refactoring.
```
