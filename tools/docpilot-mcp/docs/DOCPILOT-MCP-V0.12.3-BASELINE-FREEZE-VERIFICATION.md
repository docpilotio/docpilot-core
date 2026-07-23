# DocPilot MCP v0.12.3 Baseline Freeze and Verification

Verification time: 2026-07-23 22:57:55–23:00:48 KST  
Repository root: `C:/WorkSpace/docpilot-core-integration`  
MCP root: `C:/WorkSpace/docpilot-core-integration/tools/docpilot-mcp`

## 1. Executive Summary

**Baseline Decision: `BASELINE_VERIFIED_WITH_LIMITATIONS`**

The checked working-tree implementation identifies itself as DocPilot MCP `0.12.3`. Its official Build, separate Typecheck, and Full Test commands all completed with exit code 0 on Windows. The full Vitest run passed 25 test files and 183 tests with no failures or skips. The built stdio entry point started and exited normally when stdin closed. Ten read-only MCP Tools were invoked through an MCP client without changing the runtime state file.

The baseline is usable for the next Codex-integration verification phase, but it is not an immutable clean-commit baseline. The working tree was already dirty before verification, including staged MCP package, source, documentation, and test changes. Therefore the verified content is **HEAD plus the preserved working-tree changes**, not commit `b3434741c2a1f969fd1ad48c4e4fb1e3fd510298` alone. In addition, this run did not invoke a real Codex CLI implementation Work Order against the Core repository. Windows Worker behavior is supported by real Windows process/integration tests, but the complete operational Codex E2E remains pending for Phase 2.

No product source was modified. The only new file is this verification report.

## 2. Verification Scope

Included:

- Repository, Git, package, compiler, test, and runtime inspection
- Zero-write pre/post state capture
- Official Build, separate Typecheck, and Full Test execution
- Windows stdio server startup
- Actual MCP registration/schema inventory
- Read-only Tool execution and state-hash comparison
- Process Runner, Git controller, repository lock, recovery, persistence, and diagnostics review
- Issue classification, baseline decision, feature-freeze declaration, and Phase 2 handoff

Excluded:

- Feature implementation or refactoring
- Source fixes
- Git commit, push, branch change, merge, rebase, or tag
- Phase 2 implementation
- Real Codex CLI Work Order execution against Core
- Any mutating MCP Tool invocation against the runtime `project-state.json`

## 3. Repository Baseline

| Item | Actual value | Status |
| --- | --- | --- |
| Git root | `C:/WorkSpace/docpilot-core-integration` | PASS |
| Project/package | `docpilot-mcp` under `tools/docpilot-mcp` | PASS |
| Package version | `0.12.3` in `package.json` and `package-lock.json` | PASS |
| MCP server identity | `docpilot-project-control` version `0.12.3` | PASS |
| Branch | `fix/mcp-worker-prompt-stdin` | PASS |
| HEAD | `b3434741c2a1f969fd1ad48c4e4fb1e3fd510298` | PASS |
| Last commit | `b343474 feat(mcp): add production runtime for Codex integration` | PASS |
| Working tree | Dirty before verification; unchanged except for this report afterward | DIRTY |
| Baseline content identity | HEAD plus the pre-existing working-tree changes listed below | LIMITED |

Pre-existing changes:

```text
 M docs/planning/RFC-0043-MAIN-PLANNING-UPDATE.md
M  tools/docpilot-mcp/docs/architecture.md
M  tools/docpilot-mcp/package-lock.json
M  tools/docpilot-mcp/package.json
M  tools/docpilot-mcp/src/orchestration/CodexWorkerAdapter.ts
M  tools/docpilot-mcp/src/orchestration/ControlledProcessRunner.ts
M  tools/docpilot-mcp/src/server.ts
M  tools/docpilot-mcp/src/service/ImplementationOrchestrationService.ts
A  tools/docpilot-mcp/tests/orchestration/CodexWorkerAdapter.test.ts
A  tools/docpilot-mcp/tests/orchestration/CodexWorkerResultCapture.test.ts
M  tools/docpilot-mcp/tests/orchestration/ControlledProcessRunner.test.ts
```

All six requested Git commands completed with exit code 0 at `2026-07-23T22:57:55+09:00`.

## 4. Environment

| Component | Actual value |
| --- | --- |
| OS | Microsoft Windows `10.0.26200.8875`, x64 |
| Shell | Windows PowerShell Desktop `5.1.26100.8875` |
| Process architecture | X64 |
| Node.js | `v24.18.0` |
| npm | `11.16.0` |
| Package manager | npm; lockfile v3 |
| TypeScript | `7.0.2` |
| Vitest | `4.1.10` |
| MCP SDK | `1.29.0` |
| Zod | `4.4.3` |
| tsx | `4.23.1` |

PowerShell script execution policy blocked the `npm.ps1` and `npx.ps1` shims (exit code 1). Native Windows command shims `npm.cmd` and `npx.cmd` succeeded and were used for verification. This is an environment invocation constraint, not a package failure.

## 5. Official Commands

The commands were derived from `package.json`, `package-lock.json`, `tsconfig.json`, `tsconfig.build.json`, `vitest.config.ts`, `AGENTS.md`, and the MCP documentation.

| Purpose | Official command | Basis |
| --- | --- | --- |
| Dependency installation, if required | `npm.cmd ci` | npm lockfile v3; not run because dependencies were present and all commands executed |
| Build | `npm.cmd run build` | `clean` → `typecheck` → `tsc -p tsconfig.build.json` |
| Separate Typecheck | `npm.cmd run typecheck` | `tsc --noEmit` |
| Full Test | `npm.cmd test` | `vitest run`; `tests/**/*.test.ts`, serial files |
| Production MCP | `npm.cmd run start:mcp` / `node dist/index.js` | package script and main entry |
| Windows start check used | `cmd.exe /d /c "node dist\index.js < nul"` | built stdio entry point with closed stdin |

No dependency install was run, so neither package manifest nor lockfile was rewritten by installation.

## 6. Build Verification

| Item | Result |
| --- | --- |
| Command | `npm.cmd run build` |
| Working directory | `C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp` |
| Start | `2026-07-23T22:58:47.0066568+09:00` |
| End | `2026-07-23T22:58:48.6667188+09:00` |
| Exit code | `0` |
| Result | PASS |
| Warnings/errors | None in runner output |
| Artifacts | `dist/`, 94 files, 368,901 bytes after build |

The Build intentionally deletes and recreates `dist`. This is classified as `EXPECTED_BUILD_ARTIFACT`. A second build was not necessary to establish command reproducibility because the separate Typecheck and complete Test run also succeeded against the resulting build state.

## 7. Typecheck Verification

| Item | Result |
| --- | --- |
| Config | `tsconfig.json`; strict, `noUncheckedIndexedAccess`, `exactOptionalPropertyTypes`, `verbatimModuleSyntax`, `noEmit` |
| Build config | `tsconfig.build.json`; extends base config, emits `src` to `dist`, excludes tests |
| Separate command | `npm.cmd run typecheck` |
| Start/end | `2026-07-23T22:58:52.6413174+09:00` – `22:58:53.1410881+09:00` |
| Exit code | `0` |
| Errors | 0 |
| Warnings | 0 reported |
| Relationship to Build | Build also runs the same `typecheck` before emit |
| Result | PASS |

## 8. Full Test Verification

| Item | Result |
| --- | --- |
| Command | `npm.cmd test` |
| Framework | Vitest `4.1.10` |
| Start/end | `2026-07-23T22:58:56.6544096+09:00` – `22:59:29.6477684+09:00` |
| Runner duration | 26.98 s |
| Test files | 25 passed / 25 |
| Tests | 183 passed / 183 |
| Failed | 0 |
| Skipped | 0 |
| Exit code | 0 |
| Result | PASS |

Tests use isolated OS temporary directories and remove them during teardown. No coverage or test-results directory was created in the repository.

## 9. Windows Runtime Verification

| Verification item | Status | Evidence and boundary |
| --- | --- | --- |
| MCP server start | PASS | Built `dist/index.js` started on Windows and printed startup to stderr; closed stdin produced exit 0 |
| MCP registration/transport | PASS | `ServerRegistration.test.ts` uses linked MCP transports; actual inventory call returned 21 Tools |
| Windows executable/command resolution | PASS | Windows-only `.cmd` resolution and constrained wrapper tests passed |
| stdin Prompt transport | PASS | Multiline stdin Process Runner test and Codex adapter prompt-capture test passed |
| stdout capture | PASS | Real child-process test captured `ok` |
| stderr capture | PASS | Real child-process test captured `note` |
| exit code capture | PASS | Real child-process tests assert exit code 0 |
| Timeout | PASS | Real hanging child timed out |
| Cancellation | PASS | AbortSignal test returned `CANCELLED` and graceful termination diagnostic |
| Process-tree termination | PASS | Windows child-tree timeout test verified descendant did not create its delayed marker |
| Output masking/truncation | PASS | Tests verify credential masking and bounded output |
| Runtime path containment | PASS | Code canonicalizes paths; tests reject working-directory escape |
| Repository Lock create/exclude/release | PASS | Real cross-process Windows lock test and same-process atomic exclusion passed |
| stale Lock recovery | PASS | Demonstrably dead owner quarantined/recovered; malformed/ambiguous locks block |
| Restart state recovery | PARTIALLY_VERIFIED | Persisted orphan `RUNNING` is diagnosed/recovered in memory by Service tests/code; no crash/restart child-process E2E |
| Execution logs/Diagnostics preservation | PARTIALLY_VERIFIED | stdout/stderr and structured execution/recovery diagnostics persist in `project-state.json`; no independent log directory |
| Worker Git restrictions | PASS (contract/test) | Prompt prohibits add/commit/push; Git controller gates explicit commit. No real hostile Worker E2E |
| Real Codex CLI Work Order | NOT_VERIFIABLE | Intentionally not executed in this baseline task; Phase 2 prerequisite |

The stdio start command emitted `DocPilot MCP server started.` on stderr and returned exit code 0 when stdin was closed. The complete production process was not kept alive for mutating calls.

## 10. MCP Tool Contract Inventory

The actual registration order in `src/server.ts` and an MCP `listTools` call establish exactly 21 externally exposed Tools. Internal Services and Resources are not counted as Tools.

Common response/error contract:

- Success returns text content and, for all Tools, `structuredContent`; 15 Tools publish an MCP `outputSchema`, while six return structured content without a published output schema.
- Handler failures return MCP `isError: true` with a Tool-specific text prefix and the underlying error message.
- There is no structured, enumerated error-code object. Business validation errors are represented as MCP Tool errors.
- State mutations use `ProjectStatusService` or `ImplementationOrchestrationService`; Tools do not access repositories directly.

| Tool | Input contract | Purpose / success output | Side effect, Git, persistence, Lock | Evidence |
| --- | --- | --- | --- | --- |
| `getProjectStatus` | Empty | Project/status/lifecycle snapshot | Read-only; no Git/Lock; zero-write verified | `GetProjectStatusTool.ts`, query run |
| `getCurrentRfc` | Empty | Current RFC, phase, release | Read-only; zero-write verified | `GetCurrentRfcTool.ts` |
| `completeCurrentRfc` | Required `nextRfc: RFC-0000` | Completes and advances | Atomic state save; no Git/Lock | `CompleteCurrentRfcTool.ts`, service tests |
| `markCurrentRfcCompleted` | Strict empty | Mark-only completed state | Atomic state save; no Git/Lock | `MarkCurrentRfcCompletedTool.ts` |
| `rollbackCurrentRfc` | Strict empty | One-step lifecycle rollback | Atomic state save; no source/Git rollback | `RollbackCurrentRfcTool.ts` |
| `previewCurrentRfcRollback` | Strict empty | Eligibility/restored-state preview | Read-only; zero-write verified | `PreviewCurrentRfcRollbackTool.ts` |
| `getPlanningSynchronizationStatus` | Strict empty | Derived planning sync status | Read-only; zero-write verified | `GetPlanningSynchronizationStatusTool.ts` |
| `loadRfcContext` | Optional `rfcId: string` | `RfcExecutionContext` v1.0 | Read-only; zero-write verified; no published output schema | `LoadRfcContextTool.ts` |
| `submitRfcHandoff` | Required strict `handoff: RfcHandoff` | Persisted Pending Handoff result | Atomic state save; no Git/Lock | `SubmitRfcHandoffTool.ts`, `RfcHandoffSchemas.ts` |
| `getPendingRfcHandoff` | Strict empty | Found flag, RFC, Handoff/Markdown | Read-only; zero-write verified | `GetPendingRfcHandoffTool.ts` |
| `getDocPilotProjectControlContext` | Strict empty | Aggregate control context/capabilities | Read-only; zero-write verified; no published output schema | Tool/model/service |
| `evaluateRfcCompletionReadiness` | Optional `rfcId: string` | v1.0 readiness checks/blockers/warnings | Read-only; zero-write verified | Tool and `CompletionReadinessSchema.ts` |
| `prepareImplementationWorkOrder` | Required `repositoryRoot`, `approvedPlan[]`, `allowedPaths[]`; optional branch/commit/forbidden paths/verification/Git policy | v1.0 controlled Work Order | Reads Git HEAD/branch and atomically saves pending order; no process execution/Lock; no published output schema | Tool/schema/orchestration service |
| `getPendingImplementationWorkOrder` | Strict empty | Pending order or absence/recovery diagnostics | Read-only; zero-write verified; no published output schema | Tool/service |
| `executePendingImplementationWorkOrder` | Optional `dryRun: boolean` | Preflight or execution record | Non-dry run obtains repository Lock, runs Worker/verification/Git evidence, writes `.docpilot/results` and state; Worker may change authorized files; no commit/push; no published output schema | Tool/service/runner/lock |
| `createImplementationCommit` | Required non-empty `message` | Commit SHA and `PENDING_APPROVAL` push boundary | Lock not reacquired here; stages only authorized evidence files, creates one local commit, persists commit SHA; never pushes; no published output schema | Tool/service/Git controller |
| `generateMainPlanningSync` | Empty | Markdown plus structured lifecycle/planning snapshot | Appends `planningSynced` and atomically saves state | Tool/service |
| `listCompletedRfcs` | Empty | Completed RFC list/count | Read-only; zero-write verified | `ListCompletedRfcsTool.ts` |
| `updateProjectStatus` | Optional `phase`, `release`, `currentRfc`; at least one enforced by Service | Updated status | Atomic state save; no Git/Lock | `UpdateProjectStatusTool.ts` |
| `updateReleaseReadiness` | Required `updates` object; optional readiness fields within | Updated readiness/status | Atomic state save; no Git/Lock | `UpdateReleaseReadinessTool.ts` |
| `startNextRfc` | Required `nextRfc`; optional `phase`, `release` | New current RFC, readiness reset, lifecycle event | Atomic state save; no Git/Lock | `StartNextRfcTool.ts` |

Published input required/optional fields were taken from the actual `listTools` JSON Schema response, not inferred from documentation.

Resources and Prompt, separately:

- `docpilot://project/status`
- `docpilot://project/dashboard`
- Prompt `generateMainPlanningSync`

## 11. State Schema Inventory

### Persisted aggregate and runtime state

All aggregate fields below are stored in the single `project-state.json` selected from server `cwd`. Saves write `project-state.tmp.json` and rename it over the target.

| Schema | Required / optional fields | Version/load/migration | Creation/read/change and recovery | Lock/atomicity | Evidence |
| --- | --- | --- | --- | --- | --- |
| `ProjectStatus` | Required `project`, `phase`, `currentRfc`, `release`, `completedRfcs`; loaded/defaulted `releaseReadiness`, `lifecycleHistory`; optional pending Handoff, Work Order, execution record | No top-level version. Legacy missing readiness/history load with defaults; no migration write | Repository loads/validates; status and orchestration Services mutate | Atomic temp+rename; no general state-write lock | model/repository tests |
| `ReleaseReadiness` | Eight required states: coreBuild, coreTests, cli, incremental, reviewWorkflow, architectureSamplesValidation, documentationSync, releaseCandidate | No version; missing object/fields default `pending`; unknown fields rejected | Service reads/updates/resets | Aggregate atomic save | `ProjectStatus.ts`, repository |
| `RfcLifecycleEvent[]` | Sequential ID, type, RFC, phase, release, timestamp; `fromRfc` where applicable | No version; missing history → `[]`; event ordering/identity validated | Lifecycle Services append/read | Aggregate atomic save | model/repository/service tests |
| `RfcHandoff` (`pendingRfcHandoff`) | Identity, implementation, five verification areas, Alpha review, change lists, Git, planning update; optional Worker and Git identity fields | `schemaVersion: 1.0` only; no migration; strict nested unknown-field rejection | submit/execution creates; queries/readiness read; lifecycle completion consumes/clears per Service rules | Aggregate atomic save; execution-produced Handoff under execution Lock | model/schema/repository tests |
| `ImplementationWorkOrder` (`pendingImplementationWorkOrder`) | Identity, repository, objective, scope, execution, verification, Git policy, result contract, warnings | `schemaVersion: 1.0` only; v0.11 state without field loads; no migration | prepare creates; pending/preflight/execute read; execution changes related record | Aggregate atomic save; preparation no Lock, execution Lock later | model/repository/orchestration tests |
| `ImplementationExecutionRecord` | Identity, status, baseline, warnings/errors; optional result/evidence/diagnostics sections | `schemaVersion: 1.0` only; additive absence supported; no migration | execute creates/updates; pending query reads/recovery-diagnoses | Aggregate atomic save; non-dry execution under repository Lock | model/repository/orchestration tests |
| `ExecutionLockMetadata` | repository identity, Work Order/RFC, PID/process start/acquisition, hostname | `schemaVersion: 1.0`; unsupported/malformed → `RECOVERY_REQUIRED`; no migration | Lock manager creates/reads/releases/quarantines stale | Atomic directory acquisition plus exclusive metadata write | lock code/tests |
| `CodexImplementationResult` | Schema/RFC/order identity, implementation, files, verification, review, Git declaration | Expected `1.0`, identity-bound generated JSON Schema; no migration | Codex CLI final message creates `.docpilot/results/<order>.json`; adapter validates/Service reads | Result directory write; execution Lock active | adapter code/tests |

### Derived, returned, or embedded schemas

These are not independent repositories:

| Schema | Version | Persistence / producer / consumer |
| --- | --- | --- |
| `RfcExecutionContext` | `1.0` | Derived by `ProjectStatusService`; returned by `loadRfcContext`; not independently stored |
| `CompletionReadiness` | `1.0` | Derived from persisted Handoff/status; returned by evaluation/control context |
| `DocPilotProjectControlContext` | `1.0` | Derived aggregate response; not independently stored |
| `ProjectControlCapabilityManifest` | `1.0` | Static derived capability response; not stored |
| `ImplementationPreflightResult` | `1.0` | Derived; may be embedded in execution record |
| `CodexWorkerExecution` | `1.0` | Derived from process/result capture; may be embedded |
| `VerificationExecutionSummary` | `1.0` | Derived; embedded |
| `RepositoryEvidence` | `1.0` | Derived from Git read commands; embedded |
| `RepositoryDiffValidation` | `1.0` | Derived; embedded |
| `WorkerReviewResult` | `1.0` | Derived from Worker result/policy; embedded |
| `WorkerAlphaResult` | `1.0` | Derived; embedded |
| Planning synchronization, lifecycle guidance, rollback preview | No version | Derived responses; lifecycle event persists only when the mutating planning Tool runs |

Corrupt JSON, invalid fields, unsupported nested versions, invalid lifecycle sequences, and malformed lock metadata are rejected. There is no automatic repair of corrupt `project-state.json`, backup file, or data quarantine for the state aggregate. Stale lock directories alone are quarantined and removed when ownership is demonstrably dead.

## 12. Zero-write and Side-effect Review

### Pre-verification

- Dirty Git status: 11 pre-existing paths listed in section 3
- Runtime state: `project-state.json`, 1,860 bytes, modified `2026-07-20T12:11:39.5590213+09:00`
- Runtime state SHA-256: `C481A98C8E05CFCD3E4F64C3A82BFBAFFF0B3A0922C368D532B4F962476E37A5`
- `.docpilot`, coverage, test-results, logs, diagnostics, and results: absent
- `dist`: present
- Pending Handoff/Work Order/execution record: absent from the actual runtime state
- Current runtime project: DocPilot, current RFC `RFC-0044`; readiness/history load by backward-compatible defaults

### Actual query execution

The following Tools were called through an MCP client with empty inputs:

`getProjectStatus`, `getCurrentRfc`, `previewCurrentRfcRollback`, `getPlanningSynchronizationStatus`, `loadRfcContext`, `getPendingRfcHandoff`, `getDocPilotProjectControlContext`, `evaluateRfcCompletionReadiness`, `getPendingImplementationWorkOrder`, and `listCompletedRfcs`.

All returned `isError=false`. Runtime state hash before and after was identical:

```text
C481A98C8E05CFCD3E4F64C3A82BFBAFFF0B3A0922C368D532B4F962476E37A5
```

### Post-verification classification

| Change | Classification |
| --- | --- |
| Existing 11 dirty paths | `PRE_EXISTING_CHANGE` |
| Recreated `dist` | `EXPECTED_BUILD_ARTIFACT` |
| Test temp files outside repository, removed by teardown | `EXPECTED_TEST_ARTIFACT` |
| `.docpilot`, coverage, test-results, logs, diagnostics, results remain absent | No side effect |
| Package manifest/lock hashes unchanged during verification | No side effect |
| Planning/Core files unchanged by commands | No side effect |
| This report | Authorized documentation artifact |
| Unexpected runtime or Core changes | None observed |

## 13. Lock and Recovery Review

| Item | Implemented | Unit/integration tested | Windows operational boundary |
| --- | --- | --- | --- |
| Path `.docpilot/orchestration-lock/lock.json` | PASS | PASS | PASS |
| Atomic acquisition by directory creation | PASS | PASS, including race | PASS |
| Exclusive metadata creation (`wx`) | PASS | PASS indirectly | PASS |
| Owner identity and strict schema | PASS | PASS | PASS |
| Duplicate/live-owner blocking | PASS | PASS across real Node processes | PASS |
| Normal release | PASS | PASS | PASS |
| Exception/timeout/cancellation release | `finally` in execution Service | Full suite PASS | PARTIALLY_VERIFIED; no real Codex E2E |
| stale definition | Recorded PID is demonstrably not alive | PASS | PASS |
| stale quarantine/recovery | PASS | PASS | PASS |
| Malformed/indeterminate/identity mismatch | Blocks as `RECOVERY_REQUIRED`; does not overwrite | PASS | PASS |
| Restart diagnostics | Orphan `RUNNING` becomes in-memory blocked/recovery diagnostics | PASS at Service level | PARTIALLY_VERIFIED |
| Pending state recovery | Preserved for review; no retry/cleanup | PASS | PARTIALLY_VERIFIED |
| Corrupt project state | Rejects; no automatic repair/backup | PASS | LIMITED by design |
| Git index/worktree protection | Preflight, NUL porcelain, empty-index guard, explicit staging, cached diff, HEAD guard, attempted-stage restore | PASS | PASS in test scope |

The repository execution Lock protects controlled execution, not every `project-state.json` write. Concurrent ordinary lifecycle/status writes have no general writer lock; atomic rename prevents partial files but not lost-update races.

## 14. Known Limitations and Issues

### DP-BL-001

- Classification: `NON_BLOCKING_LIMITATION`
- Description: The verified working tree was dirty before verification, including staged v0.12.3 implementation changes.
- Evidence: Identical pre/post 11-path Git status; HEAD remains `b343474...`.
- Impact: The baseline cannot be reproduced from HEAD alone. Phase 2 must preserve the exact working tree or first establish a separately approved immutable commit.
- Baseline impact: Forces `BASELINE_VERIFIED_WITH_LIMITATIONS`.
- Recommendation: Do not modify MCP for convenience. Before cross-session Phase 2, record or approve an immutable content identity using normal project governance.
- Fixed now: No.

### DP-BL-002

- Classification: `NON_BLOCKING_LIMITATION`
- Description: No real Codex CLI Work Order was executed against Core in this task.
- Evidence: Full tests exercise actual Windows processes and adapter contracts, but use fake Codex runners for final-result capture.
- Impact: Authentication, installed CLI behavior, real prompt/result behavior, and end-to-end Core change containment remain Phase 2 evidence.
- Baseline impact: Windows Runtime is partially, not completely, operationally verified.
- Recommendation: Phase 2 should begin with a controlled, explicitly scoped dry-run/preflight and then approved Codex call using the frozen contract.
- Fixed now: No; Phase 2 is out of scope.

### DP-BL-003

- Classification: `DOCUMENTATION_MISMATCH`
- Description: `README.md` is a long captured work transcript whose opening/current claims identify v0.12.1 and state that a production runtime entry point is missing, while actual code/package are v0.12.3 with `dist/index.js` and `start:mcp`.
- Evidence: `README.md` lines 10, 80, 181, 230 versus `package.json`, `src/server.ts`, `src/index.ts`, successful Build/start.
- Impact: Operators following the beginning of README may reach obsolete conclusions; code/test evidence remains authoritative.
- Baseline impact: Non-blocking for execution, but prevents treating README as current contract evidence.
- Recommendation: No MCP code change. Documentation cleanup only if it directly supports Core implementation and is separately approved.
- Fixed now: No.

### DP-BL-004

- Classification: `TECHNICAL_DEBT`
- Description: Six Tools have structured success content but no published MCP `outputSchema`: `loadRfcContext`, `getDocPilotProjectControlContext`, `prepareImplementationWorkOrder`, `getPendingImplementationWorkOrder`, `executePendingImplementationWorkOrder`, `createImplementationCommit`.
- Evidence: Actual `listTools` output.
- Impact: Clients cannot discover those output contracts entirely from MCP Tool metadata; source/models remain authoritative.
- Baseline impact: None; tests pass and behavior is usable.
- Recommendation: Do not change under the freeze unless Phase 2 proves this directly blocks Core implementation.
- Fixed now: No.

### DP-BL-005

- Classification: `TECHNICAL_DEBT`
- Description: Handler errors are text-only MCP errors without stable structured error codes.
- Evidence: All Tool catch handlers return `isError: true` with text.
- Impact: Automation must interpret success/error at the MCP level and cannot branch on formal domain codes.
- Baseline impact: None for current controlled usage.
- Recommendation: No change unless a concrete Core blocker is demonstrated.
- Fixed now: No.

### DP-BL-006

- Classification: `NON_BLOCKING_LIMITATION`
- Description: `project-state.json` has atomic replacement but no top-level schema version, backup, quarantine, or general concurrent-writer lock.
- Evidence: `ProjectStateRepository.ts`; architecture documentation.
- Impact: Corrupt state requires manual recovery; simultaneous non-orchestration writes could lose updates.
- Baseline impact: Acceptable for the current temporary single-control-plane operating model; avoid concurrent mutating calls.
- Recommendation: Operational serialization. Do not build a general control plane unless Core work proves it necessary.
- Fixed now: No.

No `BASELINE_BLOCKER` or `CRITICAL_FIX_CANDIDATE` was found by this verification.

## 15. Evidence Index

| ID | Verification item | Command or file | Actual result | Status |
| --- | --- | --- | --- | --- |
| E-001 | Version | `package.json`, lockfile, `src/server.ts` | `0.12.3` in all three | PASS |
| E-002 | Branch | `git branch --show-current` | `fix/mcp-worker-prompt-stdin`, exit 0 | PASS |
| E-003 | HEAD | `git rev-parse HEAD` | `b3434741c2a1f969fd1ad48c4e4fb1e3fd510298`, exit 0 | PASS |
| E-004 | Working Tree | status short/porcelain | 11 pre-existing dirty paths | DIRTY |
| E-005 | Build | `npm.cmd run build` | exit 0; 94 dist files | PASS |
| E-006 | Typecheck | `npm.cmd run typecheck` | exit 0; 0 reported errors | PASS |
| E-007 | Full Test | `npm.cmd test` | 25/25 files, 183/183 tests, exit 0 | PASS |
| E-008 | Windows Runtime | built start plus Windows tests | start exit 0; process/Lock integration pass; real Codex E2E pending | PARTIAL |
| E-009 | Tool Contract | `src/server.ts`, MCP `listTools` | exactly 21 registered Tools | PASS |
| E-010 | State Schema | model/repository/service files and tests | aggregate, Handoff, Work Order, execution, result, Lock inventoried | PASS |
| E-011 | Zero-write | ten query calls and SHA-256 before/after | identical runtime hash | PASS |
| E-012 | Lock and Recovery | code, tests, Windows full run | atomic exclusion/stale recovery pass; restart E2E limited | LIMITED |

## 16. Baseline Decision

`BASELINE_VERIFIED_WITH_LIMITATIONS`

Rationale:

- Version, Git root/branch/HEAD, dirty working-tree content, environment, commands, Tool registration, and persistence structures were directly verified.
- Build, separate Typecheck, Full Test, Windows stdio start, and actual query zero-write checks passed.
- Repository Lock and process behavior have real Windows integration coverage.
- The dirty baseline is not identified by HEAD alone, and a real Codex CLI/Core E2E is still pending. These prevent `BASELINE_VERIFIED` but do not block use of the current MCP for Phase 2.

## 17. Feature Freeze Declaration

```text
Status: Feature Frozen
Purpose: Temporary Core Development Tool
Runtime Dependency from Core: None
Baseline Version: v0.12.3
```

Permitted future MCP changes are restricted to:

1. A defect that directly blocks Core implementation.
2. The minimum change strictly required to use the current MCP for actual Core implementation.
3. A security, data-corruption, or unrecoverable-state fix.

Convenience features, generalization, architecture improvement, scalability, long-term operations, and separate productization are prohibited under this freeze.

Decision test: **If a proposed MCP change does not clearly and directly accelerate completion of DocPilot Core, do not recommend or implement it.**

## 18. Phase 2 Handoff

Phase 2 was not executed. Use the following prompt in a separate Phase 2 chat:

```text
Task: Verify Codex invocation through the frozen DocPilot MCP v0.12.3 baseline for DocPilot Core RFC implementation.

Verified baseline:
- Repository: C:/WorkSpace/docpilot-core-integration
- MCP root: C:/WorkSpace/docpilot-core-integration/tools/docpilot-mcp
- Version: v0.12.3
- Branch: fix/mcp-worker-prompt-stdin
- HEAD: b3434741c2a1f969fd1ad48c4e4fb1e3fd510298
- Baseline identity limitation: HEAD plus preserved pre-existing working-tree changes; do not reset, clean, restore, or overwrite them.
- Official Build: npm.cmd run build
- Official Typecheck: npm.cmd run typecheck
- Official Full Test: npm.cmd test
- Windows MCP runtime: npm.cmd run start:mcp (equivalent built entry: node dist/index.js)
- Runtime cwd requirement: start from tools/docpilot-mcp so project-state.json resolves correctly.
- Runtime state: tools/docpilot-mcp/project-state.json
- Execution runtime: target repository .docpilot/orchestration-lock and .docpilot/results paths.

Registered Tools (exactly 21):
getProjectStatus, getCurrentRfc, completeCurrentRfc,
markCurrentRfcCompleted, rollbackCurrentRfc, previewCurrentRfcRollback,
getPlanningSynchronizationStatus, loadRfcContext, submitRfcHandoff,
getPendingRfcHandoff, getDocPilotProjectControlContext,
evaluateRfcCompletionReadiness, prepareImplementationWorkOrder,
getPendingImplementationWorkOrder, executePendingImplementationWorkOrder,
createImplementationCommit, generateMainPlanningSync, listCompletedRfcs,
updateProjectStatus, updateReleaseReadiness, startNextRfc.

State schemas:
ProjectStatus, ReleaseReadiness, RfcLifecycleEvent[],
RfcHandoff v1.0, ImplementationWorkOrder v1.0,
ImplementationExecutionRecord v1.0, ExecutionLockMetadata v1.0,
CodexImplementationResult v1.0, RfcExecutionContext v1.0,
CompletionReadiness v1.0, DocPilotProjectControlContext v1.0,
ProjectControlCapabilityManifest v1.0, and embedded v1.0 preflight,
worker execution, verification, repository evidence/diff, review, and Alpha results.

Known limitations:
1. The working tree is dirty and the verified content is not reproducible from HEAD alone.
2. Real Codex CLI/Core Work Order E2E is PENDING.
3. README opening sections are stale v0.12.1 transcript material.
4. Six Tools lack published outputSchema; errors are text-only.
5. Avoid concurrent mutating status/lifecycle calls; general state writes have no writer Lock.

Unresolved blockers: None classified as BASELINE_BLOCKER.

Allowed MCP modification scope:
- Only a Core-blocking defect, minimum change strictly required for actual Core use,
  or security/data-corruption/unrecoverable-state fix.
- Preserve original failure Evidence and obtain explicit user approval before source changes.
- No convenience, generalization, refactoring, scaling, long-term productization,
  auto push/PR/merge/release, or Core runtime MCP dependency.

Phase 2 start conditions:
1. Reconfirm version, branch, HEAD, and full dirty status without changing them.
2. Re-run npm.cmd run build, npm.cmd run typecheck, and npm.cmd test.
3. Confirm Codex CLI availability/authentication and approved RFC/scope.
4. Start with read-only status/context and Work Order preflight/dry-run.
5. Do not execute a mutating Work Order until repository scope, allowed paths,
   baseline commit, commands, result path, and Git policy are explicitly verified.
6. Preserve all before/after Git, state, Lock, result, diagnostics, and command Evidence.

Authoritative baseline report:
tools/docpilot-mcp/docs/DOCPILOT-MCP-V0.12.3-BASELINE-FREEZE-VERIFICATION.md
```

