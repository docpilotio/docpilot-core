# DocPilot MCP–Codex–Core Phase 2 Integration Verification Report

Verification date: 2026-07-23 KST
Decision: `PHASE_2_BLOCKED`

## 1. Executive Summary

The frozen DocPilot MCP v0.12.3 successfully created a real Work Order, passed all 16 Preflight checks, acquired and released its repository Lock, delivered its prompt to Codex CLI 0.144.6 through Node UTF-8 stdin, captured stdout, stderr, and exit code 0, and validated the final v1.0 structured result.

The requested Phase 2 contract was not fully achieved. The normal Work Order command does not use `--json`, so it neither emits nor saves JSONL events. It hard-codes `workspace-write`, writes Lock/schema/result artifacts inside the target Core repository, and applies implementation Alpha gates that require Build/Test/Regression/Smoke even for a read-only analysis order. During the real run, Codex invoked `gradlew tasks`, which created ignored `.gradle/` state and violated strict filesystem zero-write. MCP therefore correctly finalized the execution as `FAILED`.

The generated Core-local `.docpilot` and `.gradle` paths were inventoried, the result and execution-state Evidence were copied to the external runtime, and only those generated paths were removed. The Core worktree returned to its original clean Git and filesystem state. No MCP or Core product source was changed.

Meeting every Phase 2 criterion would require coordinated changes to the Work Order mode/schema, Codex arguments, runtime-path contract, and Alpha policy. That is larger than a single defect fix and conflicts with the feature freeze, so no code change, feature branch, commit, merge, or push was performed.

## 2. Phase 1 Baseline Confirmation

Phase 1 prompt baseline:

```text
branch: fix/mcp-worker-prompt-stdin
HEAD: b3434741c2a1f969fd1ad48c4e4fb1e3fd510298
plus 11 dirty paths
```

Actual Phase 2 start:

```text
branch: fix/mcp-worker-prompt-stdin
HEAD: c14b983bd7414f4961d09da7571d57ad54a9ab26
working tree: clean
main HEAD: 1da30f4e23e3e2a289020ddccd99521f558cea5b
```

Classification: `BASELINE_DRIFT`.

The 11 paths were not lost or reset. They were committed in `c14b983` and merged into `main` by `1da30f4` immediately before Phase 2. The Phase 2 executable content is therefore the committed successor of the Phase 1 working-tree baseline.

MCP revalidation against the actual start state:

- Build: PASS, exit 0
- Separate Typecheck: PASS, exit 0
- Full Test: 25/25 files and 183/183 tests passed, exit 0

## 3. Repository State

### MCP/Core verification worktree

| Item | Actual value |
| --- | --- |
| Path | `C:\WorkSpace\docpilot-core-integration` |
| Git repository | `docpilot-core` monorepo containing `tools/docpilot-mcp` |
| Branch | `fix/mcp-worker-prompt-stdin` |
| HEAD | `c14b983bd7414f4961d09da7571d57ad54a9ab26` |
| Initial status | Clean |
| Final status before this report | Clean |

This clean worktree was selected for the actual Work Order because the `main` worktree could not satisfy the MCP's mandatory clean-tree Preflight.

### Main worktree

| Item | Actual value |
| --- | --- |
| Path | `C:\WorkSpace\docpilot-core` |
| Branch | `main` |
| HEAD | `1da30f4e23e3e2a289020ddccd99521f558cea5b` |
| Existing change | Untracked `archive-project.bat` |

The untracked user file was not read as a Work Order input, modified, staged, deleted, or moved.

## 4. Dirty Working Tree Inventory

Phase 2 feature worktree:

- Tracked changes: 0
- Staged changes: 0
- Untracked changes: 0
- Phase 1 expected dirty paths: 11
- Match: DRIFTED because the paths are now committed

Main worktree:

- Tracked changes: 0
- Staged changes: 0
- Untracked: `archive-project.bat`

The following commands were executed before the Work Order:

```powershell
git branch --show-current
git rev-parse HEAD
git status --short
git diff --stat
git diff --name-status
git diff
git diff --cached
git ls-files --others --exclude-standard
```

## 5. Environment and Paths

| Item | Actual value |
| --- | --- |
| Windows | `10.0.26200.8875`, x64 |
| Shell | Windows PowerShell 5.1 |
| Node.js | `v24.18.0` |
| npm | `11.16.0` |
| MCP | `docpilot-mcp` v0.12.3 |
| Codex executable | `C:\Users\nk782\AppData\Roaming\npm\codex.cmd` |
| Codex package script used by MCP | `C:\Users\nk782\AppData\Roaming\npm\node_modules\@openai\codex\bin\codex.js` through `node.exe` |
| Core target | `C:\WorkSpace\docpilot-core-integration` |
| External runtime | `C:\WorkSpace\docpilot-mcp-runtime` |
| Runtime state | `C:\WorkSpace\docpilot-mcp-runtime\state\project-state.json` |
| Saved result | `C:\WorkSpace\docpilot-mcp-runtime\results\RFC-0044-c14b983bd741.json` |
| Saved execution Evidence | `C:\WorkSpace\docpilot-mcp-runtime\logs\phase2-e2e-project-state.json` |
| Latest planning sync | `docs/planning/RFC-0043-MAIN-PLANNING-UPDATE.md` |
| Main roadmap | `docs/roadmap/ROADMAP.md` |
| Current state RFC | `RFC-0044` |

No requested Phase 2 environment variables were defined. Current MCP configuration is based on server `cwd`, Work Order `repositoryRoot`, and hard-coded `.docpilot` paths.

`core.autocrlf` is `true` from the system Git config. No attributes were reported for the RFC-0043 planning document. Neither Git configuration nor `.gitattributes` was changed.

## 6. Codex CLI Verification

`codex.cmd --version`:

```text
codex-cli 0.144.6
```

Installed `codex exec --help` directly confirmed:

- stdin Prompt when `-` is used
- `--cd`
- `--sandbox` values `read-only`, `workspace-write`, `danger-full-access`
- `--json`
- `--output-schema`
- `--output-last-message`
- `--ephemeral`

Authentication and non-interactive execution were confirmed by two successful real turns.

## 7. stdin Smoke Test

The first PowerShell 5.1 pipeline used its default output encoding. Korean characters reached Codex as `?`, although Codex exited 0. Classification: `CONFIGURATION_ERROR`.

The retry set:

```powershell
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
```

It then invoked:

```powershell
$prompt | codex.cmd exec `
  --cd C:\WorkSpace\docpilot-core-integration `
  --sandbox read-only `
  --json `
  --ephemeral `
  -
```

Actual result:

- Korean and multiline prompt preserved
- JSONL `thread.started`, `turn.started`, command, message, and completion events emitted
- Read-only directory analysis completed
- Exit code 0
- Git status before/after: empty and identical

## 8. MCP Work Order Execution

Actual Tools:

1. `prepareImplementationWorkOrder`
2. `getPendingImplementationWorkOrder`
3. `executePendingImplementationWorkOrder`

Work Order:

```text
schemaVersion: 1.0
id: RFC-0044-c14b983bd741
rfcId: RFC-0044
repositoryRoot: C:\WorkSpace\docpilot-core-integration
branch: fix/mcp-worker-prompt-stdin
baselineCommit: c14b983bd7414f4961d09da7571d57ad54a9ab26
allowedPaths: .docpilot/**
allowCommit: false
requireCleanWorkingTree: true
Codex timeout: 1800 seconds
Codex sandbox: workspace-write
result: .docpilot/results/RFC-0044-c14b983bd741.json
```

All 16 Preflight checks passed.

Worker result:

```text
Codex process status: SUCCEEDED
exit code: 0
output truncated: false
result file found: true
structured result schema: valid v1.0
MCP execution status: FAILED
```

Codex identified the root structure, planning/roadmap state, RFC-0044 uncertainty, Core modules, and official commands. It reported no tracked implementation changes.

The MCP execution failed its Alpha gates because no Work Order verification commands were configured and the structured review correctly reported unresolved items and the `.gradle` side effect.

## 9. Process Runner Verification

Verified by the real E2E and dedicated actual-Codex component runs:

- Windows Codex resolution through `node.exe` plus installed `codex.js`
- UTF-8 stdin completion and close
- stdout capture
- stderr capture
- exit code capture
- bounded output
- timeout status
- cancellation status
- SIGTERM signal collection
- Windows process-tree force termination diagnostics

Regular Work Order stderr preserved the installed Codex version, workdir, model, provider, sandbox, Prompt, commands, command output, final result, and token usage inside the execution record.

## 10. Schema and JSONL Verification

Independent validation executed:

```text
state JSON parse: PASS
result JSON parse: PASS
validateCodexImplementationResult: PASS
schemaVersion: 1.0
rfcId: RFC-0044
workOrderId: RFC-0044-c14b983bd741
implementation status: FAILED
```

Result SHA-256:

```text
88668D20AA3388EC39966A325BD6ADF8C5C4C955270E36E98F1264D099E5127B
```

Execution state SHA-256:

```text
38EFD6BF6E81D5E149D088F85798DB0300822F3724CA36811405ED9DFEB886BF
```

JSONL status:

- Standalone Codex smoke: emitted to stdout, not saved by MCP
- Actual Codex timeout/cancellation through Process Runner: emitted to captured stdout
- Normal Work Order: no `--json`, therefore no JSONL events
- Persisted JSONL event file: absent

Result: `FAIL` for the requested Work Order JSONL storage contract.

The generated output-schema file was temporary and removed by the Adapter. The authoritative schema implementation remains `src/orchestration/CodexWorkerAdapter.ts`.

## 11. Zero-write Verification

### Tracked and Git state

Before and after:

- HEAD unchanged
- Branch unchanged
- tracked diff empty
- index diff empty
- no non-runtime untracked paths after cleanup

Result: PASS for tracked files, Git index, HEAD, branch, MCP source, Core source, and Core documentation.

### Complete filesystem

During execution:

- MCP created `.docpilot/orchestration-lock/lock.json`
- MCP created temporary output schema and final result under `.docpilot/results`
- Codex ran `gradlew tasks --all`, which created ignored `.gradle/`

Result: `FAIL` for strict read-only filesystem zero-write.

After Evidence preservation:

- generated result copied to the external runtime
- generated execution state retained externally
- generated Core-local `.docpilot` removed
- generated Core-local `.gradle` removed
- final target worktree clean

No pre-existing user file was removed.

## 12. Timeout and Cancellation

Actual Codex CLI was invoked through `ControlledProcessRunner` with `--json`, read-only sandbox, and stdin.

Timeout result:

```text
status: TIMED_OUT
signal: SIGTERM
timedOut: true
cancelled: false
JSONL captured: thread.started, turn.started
terminationSteps:
  GRACEFUL_REQUESTED
  WINDOWS_TREE_FORCE
  DIRECT_SIGTERM
  WINDOWS_TREE_FORCE
```

Cancellation result:

```text
status: CANCELLED
signal: SIGTERM
timedOut: false
cancelled: true
JSONL captured: thread.started, turn.started
terminationSteps:
  GRACEFUL_REQUESTED
  WINDOWS_TREE_FORCE
  DIRECT_SIGTERM
  WINDOWS_TREE_FORCE
```

Both component runs completed without a remaining Codex child tree.

## 13. Lock and Recovery

Normal Work Order:

- Lock created before Worker execution
- Lock metadata visible to Codex during analysis
- Lock directory absent after execution failure

Timeout and cancellation runs:

```text
before process: ACTIVE
after finally/release: ABSENT
```

Lock metadata included schema v1.0, canonical repository identity, Work Order, RFC, PID, process start, acquisition time, and hostname.

Stale/malformed recovery was not destructively reproduced against the real repository. Phase 1's full test run revalidated its isolated stale-lock and malformed-lock coverage.

## 14. Runtime Directory Isolation

External directories were created:

```text
C:\WorkSpace\docpilot-mcp-runtime\state
C:\WorkSpace\docpilot-mcp-runtime\locks
C:\WorkSpace\docpilot-mcp-runtime\logs
C:\WorkSpace\docpilot-mcp-runtime\results
C:\WorkSpace\docpilot-mcp-runtime\handoffs
```

The MCP state was successfully isolated by starting the server with the external `state` directory as `cwd`.

Lock and result isolation failed at the contract level. `RepositoryExecutionLock` and the result contract force `.docpilot` beneath the target repository. The external `locks` and `handoffs` directories were not used by current code.

## 15. Code Changes

Product code changes: none.

Changes required to meet all requested Phase 2 criteria would include:

1. A supported read-only Work Order mode and read-only Codex sandbox.
2. Work Order `--json` and durable JSONL event storage.
3. External runtime paths for Lock, output schema, result, and diagnostics.
4. Analysis-specific completion policy that does not require implementation Build/Test/Regression/Smoke gates.

These changes affect Tool input, Work Order persistence, runtime paths, Lock placement, process output, and Alpha semantics. They are not a single minimal compatibility correction and were not made under the freeze.

## 16. Feature Branch and Commit History

Phase 2 code feature branch: N/A
Phase 2 commits: N/A

The existing `fix/mcp-worker-prompt-stdin` branch was used as the clean verified worktree. No new branch was created because no code fix was implemented.

## 17. main Merge Result

N/A. No Phase 2 code was eligible for merge.

The pre-Phase-2 baseline was already merged to main as:

```text
1da30f4 merge: integrate DocPilot MCP v0.12.3 baseline
```

## 18. Post-merge Verification

N/A for Phase 2, because no Phase 2 code merge occurred.

## 19. Known Limitations

- Phase 1 textual baseline drifted to its committed successor before Phase 2.
- `main` has a pre-existing untracked `archive-project.bat`, so current clean-tree Work Orders cannot target that worktree.
- PowerShell 5.1 requires explicit UTF-8 output encoding for Korean stdin pipelines.
- Work Orders are implementation-oriented and cannot express a strict read-only analysis mode.
- Normal Work Orders do not request or persist JSONL.
- Runtime Lock/result artifacts are repository-local.
- Strict filesystem zero-write is not enforceable with `workspace-write`; Codex created Gradle cache state.
- RFC-0044 is present in state but no tracked RFC-0044 specification exists; current planning says it must be confirmed.

## 20. Problem Inventory

### P2-001 — `BASELINE_DRIFT`

- Reproduction: Git baseline commands
- Expected: `b343474` plus 11 dirty paths
- Actual: clean `c14b983`; changes committed and merged
- Impact: Prompt baseline identity is stale, but implementation content is preserved
- Code change: No
- Phase 3: Use current committed identities

### P2-002 — `CONFIGURATION_ERROR`

- Reproduction: Korean Prompt piped by default PowerShell 5.1 encoding
- Expected: UTF-8 Korean
- Actual: `?` replacement
- Resolution: Set `$OutputEncoding` and console output encoding to UTF-8
- Code change: No; Node MCP stdin was UTF-8
- Verification: Retry PASS

### P2-003 — `SCHEMA_COMPATIBILITY_ERROR`

- Reproduction: `prepareImplementationWorkOrder` with a read-only approved plan, then execute
- Expected: analysis Work Order can complete without implementation gates
- Actual: Alpha requires Build/Test/Regression/Smoke and execution is `FAILED`
- Impact: Core read-only analysis cannot be a successful Work Order outcome
- Code change: Required to meet requested contract, not applied
- Phase 3: Blocked until contract decision

### P2-004 — `ZERO_WRITE_VIOLATION`

- Reproduction: real Work Order; Codex ran `gradlew tasks --all`
- Expected: no Core filesystem changes
- Actual: ignored `.gradle/` created
- Impact: Strict read-only violated; tracked/index data remained safe
- Code change: Read-only sandbox/mode required; not applied
- Recovery: Evidence preserved, generated cache removed, final state clean

### P2-005 — `CONFIGURATION_ERROR`

- Reproduction: inspect prepared Work Order and runtime paths
- Expected: external runtime root
- Actual: Lock/schema/result forced into target `.docpilot`
- Impact: Runtime isolation criterion fails
- Code change: Runtime-path contract change required; not applied

### P2-006 — `CODEX_CLI_INCOMPATIBILITY`

- Reproduction: inspect normal Work Order command
- Expected: `--json` plus durable JSONL events
- Actual: no `--json`; only final structured message and textual diagnostics persisted
- Impact: JSONL storage criterion fails
- Code change: Process/output persistence change required; not applied

### P2-007 — `NON_BLOCKING_LIMITATION`

- Reproduction: target `C:\WorkSpace\docpilot-core` Preflight
- Expected: clean target
- Actual: pre-existing untracked `archive-project.bat`
- Impact: main worktree was not used; clean integration worktree used instead
- Code change: No

## 21. Evidence Table

| ID | Verification item | Command or file | Expected | Actual | Status |
| --- | --- | --- | --- | --- | --- |
| P2-E001 | MCP Branch | `git branch --show-current` | Phase 1 branch | `fix/mcp-worker-prompt-stdin` | PASS |
| P2-E002 | MCP HEAD | `git rev-parse HEAD` | Phase 1 identity | `c14b983...`, committed successor | DRIFT |
| P2-E003 | Dirty Tree | Git state commands | 11 paths | clean; paths committed | DRIFT |
| P2-E004 | Codex Version | `codex.cmd --version` | runnable | `0.144.6` | PASS |
| P2-E005 | Codex stdin | UTF-8 smoke | preserved | Korean/multiline preserved | PASS |
| P2-E006 | Work Order | MCP Tools | successful analysis order | created/preflight/Worker pass; final execution failed Alpha | FAIL |
| P2-E007 | Core read-only | Git/filesystem comparison | no changes | Git safe; transient `.gradle` and `.docpilot` | FAIL |
| P2-E008 | JSONL | normal Work Order | durable events | absent | FAIL |
| P2-E009 | Schema | independent validator | valid | valid v1.0 | PASS |
| P2-E010 | Timeout | actual Codex/Runner | clean timeout | `TIMED_OUT`, tree terminated | PASS |
| P2-E011 | Cancellation | actual Codex/Runner | clean cancel | `CANCELLED`, tree terminated | PASS |
| P2-E012 | Lock | Work Order/component runs | create/release | `ACTIVE` → `ABSENT` | PASS |
| P2-E013 | Build | `npm.cmd run build` | exit 0 | exit 0 | PASS |
| P2-E014 | Typecheck | `npm.cmd run typecheck` | exit 0 | exit 0 | PASS |
| P2-E015 | Full Test | `npm.cmd test` | 25/183 | 25/183, zero failures/skips | PASS |
| P2-E016 | Feature Branch | Git | only if code changed | no code change | N/A |
| P2-E017 | main Merge | Git | only if code changed | no Phase 2 merge | N/A |
| P2-E018 | Post-merge Test | official commands | only if merged | no Phase 2 merge | N/A |

## 22. Phase 2 Decision

`PHASE_2_BLOCKED`

The real connectivity path is proven through Codex completion and valid structured result capture, but the requested read-only Work Order cannot succeed under current Alpha policy, strict zero-write was violated, JSONL was not produced by the normal Work Order, and runtime artifacts are not isolated from Core. The repository was safely recovered, so `PHASE_2_RECOVERY_REQUIRED` does not apply.

## 23. Phase 3 Handoff

Confirmed baseline:

```text
MCP version: 0.12.3
feature worktree: fix/mcp-worker-prompt-stdin @ c14b983bd7414f4961d09da7571d57ad54a9ab26
main: 1da30f4e23e3e2a289020ddccd99521f558cea5b
Codex: C:\Users\nk782\AppData\Roaming\npm\codex.cmd, 0.144.6
Core target used: C:\WorkSpace\docpilot-core-integration
external runtime: C:\WorkSpace\docpilot-mcp-runtime
```

Official MCP commands:

```powershell
cd C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp
npm.cmd run build
npm.cmd run typecheck
npm.cmd test
```

Core commands confirmed by source/docs and real task discovery:

```powershell
.\gradlew.bat clean build
.\gradlew.bat test
.\docpilot.ps1 test
.\docpilot.ps1 ci
.\gradlew.bat :run --args="analyze C:\WorkSpace\architecture-samples"
```

MCP Work Order Tools:

```text
prepareImplementationWorkOrder
getPendingImplementationWorkOrder
executePendingImplementationWorkOrder
```

Result/schema:

```text
saved result:
C:\WorkSpace\docpilot-mcp-runtime\results\RFC-0044-c14b983bd741.json

execution record:
C:\WorkSpace\docpilot-mcp-runtime\state\project-state.json

schema implementation:
tools/docpilot-mcp/src/orchestration/CodexWorkerAdapter.ts
schema version: 1.0
```

Policies:

- Work Order timeout: 1800 seconds
- Cancellation: AbortSignal; distinguished as `CANCELLED`
- Timeout: distinguished as `TIMED_OUT`
- Windows termination: graceful request, task-tree force, direct SIGTERM diagnostics
- Lock: target `.docpilot/orchestration-lock`, strict owner metadata, release in `finally`

Phase 3 must not assume Phase 2 verification. Before reuse, decide one of:

1. Accept the current MCP as implementation-only and move read-only verification outside MCP, or
2. Explicitly authorize the minimum frozen-MCP contract changes for read-only mode, JSONL persistence, external runtime, and analysis completion policy.

Do not begin Core feature implementation until RFC-0044 is confirmed by Main Planning and the Phase 2 blocker disposition is explicit.
