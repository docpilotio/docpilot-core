# DocPilot MCP–Codex–Core Phase 2.1 Integration Hardening Report

## Executive Summary

Phase 2 blockers P2-003–P2-006 were resolved with additive changes: ANALYSIS Work Orders, Codex read-only execution, streamed JSONL persistence, external Runtime Root and Lock paths, and an analysis-specific completion policy.

Decision: `PHASE_2_1_VERIFIED`

## Starting Baseline

- MCP package: `0.12.3`
- Starting local main: `5d7c08ad3dc6152ced762dab629e1969abe1382e`
- Feature branch: `feature/phase-2-1-mcp-integration-hardening`
- Codex: `C:\Users\nk782\AppData\Roaming\npm\codex.cmd`, `codex-cli 0.144.6`
- Pre-existing user file preserved and never staged: `C:\WorkSpace\docpilot-core\archive-project.bat`

## Implemented Scope

- Added `ANALYSIS`/`IMPLEMENTATION` mode; absent mode defaults to `IMPLEMENTATION`.
- ANALYSIS uses `--sandbox read-only`, `--json`, `--ephemeral`, stdin prompt, and excludes implementation gates.
- Codex stdout is streamed to external JSONL while bounded stdout remains diagnostics output.
- Final result, schema, diagnostics, logs, state, and locks are external-runtime artifacts.
- External lock metadata has an owner token and preserves legacy metadata loading/recovery.
- ANALYSIS completion requires process/result/JSONL success plus strict Git/filesystem zero-write.
- Existing IMPLEMENTATION behavior and 21 Tool registrations remain intact.

## Verification Evidence

| ID | Check | Actual result | Status |
|---|---|---|---|
| P21-E001 | Feature starting commit | `5d7c08a` | PASS |
| P21-E002 | Implementation commit | `79892ab` | PASS |
| P21-E003 | Harness follow-up commit | `f5f2000` | PASS |
| P21-E004 | MCP Build | `npm.cmd run build`, exit 0 | PASS |
| P21-E005 | MCP Typecheck | `npm.cmd run typecheck`, exit 0 | PASS |
| P21-E006 | Full Test | 26 files, 194 tests, 0 failures | PASS |
| P21-E007 | Codex CLI | `codex-cli 0.144.6` | PASS |
| P21-E008 | Feature ANALYSIS E2E | SUCCEEDED, exit 0, result/schema/JSONL saved | PASS |
| P21-E009 | Main ANALYSIS E2E | SUCCEEDED, exit 0, result/schema/JSONL saved | PASS |
| P21-E010 | Core zero-write | 704 entries; before/after SHA-256 `7DF885A8FE1BC4E4812009C75F5D34CE099D21D07EF651D7BE19CA87A77BA1FE` | PASS |
| P21-E011 | Runtime isolation | External Runtime; no Core `.docpilot`, `.gradle`, `build`, `out`, or `node_modules` | PASS |
| P21-E012 | Lock | External lock created and final state `ABSENT` | PASS |
| P21-E013 | Timeout | `TIMED_OUT`, diagnostics true, JSONL 101 bytes, Lock ABSENT | PASS |
| P21-E014 | Cancellation | `CANCELLED`, diagnostics true, JSONL 101 bytes, Lock ABSENT | PASS |
| P21-E015 | Regression | Included in 194 passing tests | PASS |
| P21-E016 | Tool inventory | 21 externally registered Tools | PASS |

## Runtime and Recovery Evidence

Final main E2E Runtime Root: `C:\WorkSpace\docpilot-mcp-runtime\phase-2-1-main-final`.

Repository key: `e183bae63bf00332`. JSONL, structured result, schema, diagnostics, state, and lock artifacts are outside the Core repository. The lock directory was absent after successful completion. Actual Windows timeout and cancellation runs retained partial JSONL evidence, recorded Windows process-tree termination diagnostics, and released the external lock.

## Backward Compatibility and Limitations

- Existing IMPLEMENTATION Work Orders, Alpha gates, timeout/cancellation contracts, and legacy lock metadata remain supported.
- Bounded display stdout may set `outputTruncated`; full JSONL evidence is streamed separately.
- ANALYSIS intentionally does not run Core Build/Test; it verifies repository facts and strict zero-write.
- Phase 3 must independently verify standalone server startup and persistence recovery.
- Historical README version mismatch remains `DOCUMENTATION_MISMATCH` and is out of scope.

## Git and Merge

- `79892ab feat(mcp): support read-only analysis work orders`
- `f5f2000 test(mcp): make analysis verification harness self-contained`
- First implementation merge: `55c8ace`
- Final local main after harness merge: `97f70a25170f3cc1b846dabe8f3dbe7eb4f80b54`
- Push performed: `NO`
- Main working tree: only pre-existing untracked `archive-project.bat`

## Phase 2.1 Decision

`PHASE_2_1_VERIFIED`

All required blocker resolutions, Build, Typecheck, Full Test, actual Codex ANALYSIS E2E, Core strict zero-write, external Runtime isolation, JSONL persistence, Timeout, Cancellation, Lock release, and main post-merge verification passed.

## Phase 3 Handoff

Start from local main `97f70a25170f3cc1b846dabe8f3dbe7eb4f80b54`, MCP `v0.12.3`, and the `OrchestrationRuntime` external Runtime Root contract:

```powershell
cd C:\WorkSpace\docpilot-core\tools\docpilot-mcp
npm.cmd run build
npm.cmd run typecheck
npm.cmd test
$env:DOCPILOT_MCP_RUNTIME_ROOT='C:\WorkSpace\docpilot-mcp-runtime\phase-3'
npm.cmd run start:mcp
```

Phase 3 is MCP-only: server start/stop, 21 Tool registration, query zero-write, atomic state persistence/load, external Repository Lock, stale/malformed recovery, diagnostics retention, timeout/cancellation/process-failure recovery. Do not implement Core RFC functionality, add MCP features, push, or release.
