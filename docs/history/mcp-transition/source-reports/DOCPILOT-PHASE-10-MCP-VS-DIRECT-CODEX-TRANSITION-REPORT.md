# DocPilot Phase 10.0 MCP vs Direct Codex Transition Evaluation

## Decision

`PHASE_10_MCP_RETAIN_TEMPORARILY`

## Fair comparison

- RFC: RFC-0044
- Baseline: `c62965cda3aef7f2d69165c545c5e1f11696f242`
- MCP reference candidate: `C:\WorkSpace\docpilot-rfc-0044`
- Direct replay candidate: `C:\WorkSpace\docpilot-phase-10-direct-rfc-0044`
- Both candidates were kept uncommitted and isolated.

## MCP reference

The MCP path completed RFC-0044 implementation, independent Phase 8 verification, CLI smoke, and Completion Handoff with `READY_WITH_WARNINGS`. Core tests were 254/254 with zero failures.

## Direct replay

Direct Codex was invoked without MCP Server, MCP Tool, MCP State, or MCP Runtime. The Codex process timed out before returning a final response, although it produced a candidate diff. Independent validation then found:

- Build compilation progressed successfully.
- Full Gradle test execution failed: 234 tests completed, 2 failures.
- Failures: `JsonSpecificationSnapshotCodecTest.detects payload tampering` and `round trips full specification deterministically`.
- Clean build could not be reported as passed; the first clean invocation encountered a locked Gradle report directory, and the subsequent non-clean build failed in tests.
- Smoke was not run because the Direct candidate did not pass the required build/test gate.

The Direct candidate changed only RFC-allowed implementation/test/document paths (plus ignored Kotlin compiler error logs), was not committed, and did not touch the MCP candidate or Main Worktree.

## Comparison

Direct implementation did not meet the minimum equivalence gate (full test and smoke). Therefore there is insufficient evidence to recommend Direct Codex replacement or Hybrid transition yet. MCP remains temporarily retained for controlled RFC execution and completion evidence.

## Required follow-up

1. Preserve the Direct replay candidate and failure reports.
2. Return the Direct candidate to a correction cycle; do not modify the MCP candidate.
3. Fix or explain the snapshot compatibility regressions in a new Direct experiment.
4. Re-run targeted tests, clean build, full test, and isolated CLI smoke.
5. Re-evaluate transition only after Direct passes all gates.

No commit, merge, push, release, or MCP code change was performed in Phase 10.
