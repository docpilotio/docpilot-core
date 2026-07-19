# DocPilot MCP

DocPilot MCP is the MCP control plane for DocPilot project status, RFC workflow, documentation, and release operations. This package establishes the initial product foundation while retaining the behavior of the existing implementation.

## Product scope

The current product reads and updates a compact DocPilot project status and persistent Release Readiness, supports the current RFC completion workflow, reports completed RFCs, provides a consolidated read-only project dashboard, and generates a Main Planning synchronization artifact. Documentation operations remain part of the intended control-plane scope but are not implemented yet.

## Current architecture

The TypeScript server uses MCP over standard input/output. `src/index.ts` starts the transport, and `src/server.ts` creates the repository and service before registering all Tools, Resources, and Prompts. The implementation is organized into model, repository, service, tool, resource, and prompt layers.

Tools call `ProjectStatusService`; they do not access persistence directly. The service owns business validation and workflow rules. `ProjectStateRepository` owns JSON loading, validation at the persistence boundary, serialization, and atomic replacement of the state file. The project status and dashboard Resources and the planning Prompt read through the service.

See [docs/architecture.md](docs/architecture.md) for the detailed architecture and dependency rules.

## Available Tools

### Project Control Boundary

- `getDocPilotProjectControlContext` accepts strict empty input and composes the official current project, lifecycle, RFC Context, Pending Handoff summary, Completion Readiness, Capability Manifest, policies, Planning Synchronization, Release Readiness, and explicit evidence limitations. It is read-only.
- `evaluateRfcCompletionReadiness` optionally accepts the current `rfcId` and evaluates deterministic Alpha Gates without writing state or executing submitted commands.

Project Control Query Boundary consists of `loadRfcContext`, `getPendingRfcHandoff`, `getDocPilotProjectControlContext`, `evaluateRfcCompletionReadiness`, and `getPendingImplementationWorkOrder`. Commands are `submitRfcHandoff`, `prepareImplementationWorkOrder`, `executePendingImplementationWorkOrder`, and `createImplementationCommit`. Acknowledge, consume, archive, history, cloud workers, push/PR/release automation, and lifecycle advancement remain outside the boundary.

Completion Readiness uses fixed ordered checks for identity, Handoff presence/schema/RFC, implementation, build, tests, regression, smoke, scope, alpha review, known limitations, and Git push policy. Results are `NOT_READY`, `BLOCKED`, `READY_WITH_WARNINGS`, or `READY`. Submitted evidence is structurally validated but MCP does not independently execute commands or verify Git diffs. Missing allowed paths are disclosed on the Scope check.

The Capability Manifest reports deterministic Work Order generation, controlled local execution, Alpha-gated commit creation, and a push-approval boundary as supported. `git.pushApproval=true` describes the boundary only: no push implementation exists. Cloud execution, push, PR/release automation, and automatic lifecycle completion/advance remain false.

### Controlled Implementation Orchestration

- `prepareImplementationWorkOrder` fixes the current RFC, Git root/branch/HEAD baseline, approved plan, normalized scope, controlled verification commands, result contract, and conservative Git policy. One restart-safe Pending Work Order is allowed per current RFC.
- `getPendingImplementationWorkOrder` is a strict, deterministic, zero-write query.
- `executePendingImplementationWorkOrder` accepts optional `{ "dryRun": true }`. Dry-run returns ordered preflight checks plus the deterministic Codex prompt/command without executing or saving. A real run requires a clean working tree, fixed HEAD, valid in-repository paths, an available Codex executable, no Pending Handoff, and valid controlled commands. It records RUNNING before execution and a terminal result afterward.
- `createImplementationCommit` accepts `{ "message": "..." }` only after MCP Alpha passes and the Work Order permits commits. It stages explicit authorized evidence paths, runs cached diff checks, creates one non-amended commit, and returns `PENDING_APPROVAL`. It never pushes.

`ControlledCommand` separates executable and arguments, fixes an in-repository working directory, requires a timeout, runs without a shell, passes only allowlisted environment variables, limits output, and masks common secret forms. Verification order is targeted tests, module tests, build, regression, then smoke; a required failure skips subsequent commands. Git evidence uses porcelain status and records branch, baseline/HEAD, changed, created, deleted, renamed, staged, and untracked paths. Diff validation blocks forbidden/out-of-scope paths, unauthorized dependency/build configuration changes, unapproved public-API candidates, and disallowed untracked files.

The Worker JSON is treated as a claim. MCP independently validates its schema/RFC/Work Order identity, actual Git evidence, verification results, policy review, and twelve ordered Alpha Gates. Only `PASSED` or `PASSED_WITH_LIMITATIONS` creates the official Pending Handoff; failure remains in the Execution Record. Work Order preparation, execution, Handoff generation, and commit never complete or advance an RFC and never mark Planning synchronized.

### RFC Context and Handoff

- `loadRfcContext` optionally accepts the current `rfcId` and returns deterministic official project context, operating rules, alpha criteria, guidance, synchronization, readiness, and warnings for unavailable RFC metadata. It is read-only.
- `submitRfcHandoff` accepts `{ "handoff": RfcHandoff }`, validates schema version `1.0` and current-RFC ownership, normalizes file lists, and atomically stores one Pending Handoff. It never advances lifecycle, synchronizes Planning, commits, or pushes.
- `getPendingRfcHandoff` accepts strict empty input and returns the current Pending Handoff plus deterministic Markdown, or a normal `found: false` result.

Duplicate submission is rejected rather than silently replacing review evidence. No Handoff history, Approval Registry, or Evidence Registry is created.

### Project Status

- `getProjectStatus` returns the complete current project status.
- `getCurrentRfc` returns the current RFC with its phase and release context.
- `updateProjectStatus` updates one or more of `phase`, `release`, and `currentRfc` after service validation.
- `listCompletedRfcs` returns completed RFC identifiers, their count, and current project context.
- `updateReleaseReadiness` accepts an `updates` object containing one or more readiness fields and returns the updated project status. Each value must be `pending`, `passed`, or `failed`; omitted fields retain their prior values.

### RFC Workflow

- `markCurrentRfcCompleted` accepts strict empty input (`{}`), marks the current RFC completed, and leaves the current RFC, phase, release, and Release Readiness unchanged.
- `startNextRfc` starts an explicitly supplied later RFC, preserves completed history, optionally updates `phase` and `release`, and resets all Release Readiness fields to `pending`. Input is `{ "nextRfc": "RFC-0045", "phase"?: "...", "release"?: "..." }`; no other fields are accepted.
- `rollbackCurrentRfc` accepts strict empty input (`{}`) and restores the immediately previous active RFC from lifecycle-history evidence. It resets Release Readiness and appends an audit event; it does not roll back Git, source files, branches, or commits.
- `previewCurrentRfcRollback` accepts strict empty input (`{}`) and reports whether that same one-step rollback is eligible, including the restored RFC, phase, release, and all-pending readiness state. It is read-only and returns a stable blocking reason when ineligible.
- `completeCurrentRfc` is the legacy shortcut that records the current RFC and advances immediately to its required `nextRfc`.

The preferred lifecycle is `markCurrentRfcCompleted` → `startNextRfc` → `generateMainPlanningSync`. Marking validates the current RFC against exact `RFC-[0-9]{4}` syntax, explicitly rejects an already completed RFC, numerically orders and deduplicates completed history, and performs one Repository save. It does not reset readiness or invoke planning.

`startNextRfc` requires the exact `RFC-[0-9]{4}` format with no surrounding whitespace. The next RFC must differ from and be numerically greater than the current RFC, must not already be completed, and the current RFC must already appear in completed history. Optional phase and release values must be non-empty. The Service validates each transition and sends one complete state to the Repository for persistence.

For backward compatibility, `completeCurrentRfc` retains its existing input, response, and combined complete-and-advance behavior. Completed history produced by either completion method is canonicalized into numeric RFC order without duplicates. No workflow automatically generates or writes Main Planning sync output; planning remains a separate Tool or Prompt operation.

### RFC Lifecycle Guidance

Lifecycle guidance is derived from the current persisted project status and is never stored. It reports one of three stable states:

- `in_progress` recommends `markCurrentRfcCompleted` when the current RFC is absent from completed history.
- `completed_waiting_next` recommends `startNextRfc` when the current RFC is already completed.
- `inconsistent` recommends `manualReview` when the current RFC or completed history contains malformed identifiers or duplicate completed entries.

The Service owns these decisions and deterministic reason strings. The `generateMainPlanningSync` Tool exposes guidance in structured content and in its Markdown, the Prompt appends the same RFC Lifecycle section, and `docpilot://project/dashboard` includes an additive `lifecycleGuidance` object. Guidance derivation, dashboard reads, and Prompt generation do not persist state or execute a recommended Tool. The explicit planning Tool records its own lifecycle-history event as described below.

Because `completeCurrentRfc` advances directly to a new current RFC without storing transition metadata, its result is structurally identical to ordinary in-progress work. Guidance therefore recommends `markCurrentRfcCompleted` for that new current RFC and does not infer how it became active.

### Planning

- `generateMainPlanningSync` generates a Markdown Main Planning status summary and structured status data.
- `getPlanningSynchronizationStatus` accepts strict empty input (`{}`) and read-only evaluates whether lifecycle changes are covered by the latest `planningSynced` event.

### Planning Synchronization Status

Planning status is derived from validated lifecycle history in persisted append order; timestamps are never compared. `started`, `completed`, and `rollbackCompleted` are planning-relevant changes. A later `planningSynced` covers those changes without itself making Planning stale.

- `neverSynced`: no valid `planningSynced` event exists; the recommended action is `generateMainPlanningSync`.
- `current`: no relevant transition occurs after the latest sync; the recommended action is `none`.
- `stale`: a relevant transition occurs after the latest sync; the recommended action is `generateMainPlanningSync`. Rollback uses a dedicated stable reason.

Evaluation creates no timestamp, performs no save, appends no event, consumes no event ID, and does not mutate Project Status. The derived expected `documentationSync` is `passed` for `current` and `pending` otherwise. The result reports whether persisted readiness agrees and supplies a deterministic mismatch reason; it never corrects readiness automatically. Lifecycle Guidance adds synchronization state and required status separately while retaining its primary RFC workflow action.

The legacy combined completion workflow has no transition event. For compatibility, an explicit `planningSynced` event matching persisted `currentRfc` may re-anchor planning evaluation after that workflow; rollback resolution remains strict. Future work may automatically maintain Documentation Sync and use this status in release gates.

## Available Resources

- `project-status` at `docpilot://project/status` returns the current project status as `application/json`.
- `project-dashboard` at `docpilot://project/dashboard` returns a consolidated read-only dashboard as `application/json`. Its fields are `project`, `phase`, `currentRfc`, `release`, `completedCount`, `completedRfcs`, and `releaseReadiness`. Current values, including persisted readiness, come from `ProjectStatusService`; `completedCount` is derived from the ordered `completedRfcs` array.

The `releaseReadiness` object contains `coreBuild`, `coreTests`, `cli`, `incremental`, `reviewWorkflow`, `architectureSamplesValidation`, `documentationSync`, and `releaseCandidate`. Each field is persisted as `pending`, `passed`, or `failed`.

The dashboard also exposes the append-only `lifecycleHistory` array, derived `rollbackPreview`, and complete `planningSynchronization` status. Repeated dashboard reads do not save state or append events.

## Available Prompts

- `generateMainPlanningSync` creates the existing Main Planning synchronization prompt. Optional `completedWork` and `nextWork` arguments add workflow context; current project data is loaded through the service.

## Persistence model

`pendingRfcHandoff` is an optional additive field in the existing atomically replaced `project-state.json`. Legacy v0.9 files without it remain valid and are not rewritten by reads. When present, schema version `1.0` is required; unsupported future versions are rejected. Submission changes only this field and preserves project status, lifecycle, readiness, and planning state.

`pendingImplementationWorkOrder` and `implementationExecutionRecord` are optional v0.12 additive fields in the same atomic state document. Legacy v0.11 state loads without migration. A persisted orphaned `RUNNING` record is exposed as `BLOCKED` with a recovery warning and is never retried automatically. Work Order/result IDs derive from RFC plus baseline commit; no UUID or timestamp drives orchestration identity.

`RfcExecutionContext` is a non-persistent read model. Because Project State does not store RFC title, goal, detailed scope, acceptance criteria, next RFC, or repository baseline, Context returns conservative empty/optional values and a warning instead of inventing data. Default alpha criteria cover build, focused tests, regression, smoke, scope, and review in stable order.

`RfcHandoff` is the structured source for implementation, verification, alpha review, limitations, architecture/API changes, Git reporting, and planning updates. Markdown is rendered from it and never parsed back into official state. Main Planning Markdown includes a Pending Handoff when present.

Runtime state is stored in `project-state.json`, resolved relative to the process working directory. The repository parses and validates the complete status shape on reads and writes. Saves serialize formatted JSON to `project-state.tmp.json` and rename it over `project-state.json`. The runtime state and temporary state files are not source artifacts and must not be committed.

The server expects `project-state.json` to exist and contain string values for `project`, `phase`, `currentRfc`, and `release`, plus a string array named `completedRfcs`. The additive `releaseReadiness` object stores all eight readiness fields. Legacy files without the object, and objects with missing individual fields, load with deterministic `pending` defaults in memory. Reads do not rewrite legacy files; the complete readiness object is serialized on the next normal save. Invalid readiness values are rejected.

## RFC Lifecycle History

`lifecycleHistory` is an additive, append-only array stored alongside project status. Legacy files without it load with an empty history and are not rewritten merely by reading. Each immutable event contains `id`, `type`, `rfc`, `phase`, `release`, and an ISO `timestamp`; event types are `started`, `completed`, `planningSynced`, and `rollbackCompleted`. Rollback events additionally contain `fromRfc` so an audit reader can see both sides of the transition. Existing events remain valid without that optional field.

The Service assigns deterministic sequence IDs such as `rfc-event-000001` and preserves array order as event order. `markCurrentRfcCompleted` appends `completed`, `startNextRfc` appends `started`, and the explicit `generateMainPlanningSync` Tool appends `planningSynced`. Each event is included in the same complete state save as its operation. The legacy `completeCurrentRfc` shortcut remains behaviorally unchanged and does not synthesize history events.

Main Planning Markdown includes an RFC Lifecycle Timeline derived from persisted events. Rollback entries render as `Rolled back RFC-0048 → RFC-0047`. The Service exposes full and latest-event queries, and the dashboard returns the full history including compensating rollback events.

### Project-state rollback

`rollbackCurrentRfc` is deliberately limited to one project-management transition. The Service validates every event and replays the canonical append order to identify the active RFC immediately before the latest `started` transition. It never guesses by subtracting an RFC number. The current persisted RFC must agree with the replayed active RFC, the latest transition must have activated it, and ambiguous, missing, malformed, or repeated-rollback evidence is rejected before any save.

On success, the prior event context restores the RFC, phase, and release; completed RFCs remain historical completion records; all eight Release Readiness fields reset to `pending`; and one `rollbackCompleted` compensating event is appended in the same single save. Earlier history is never deleted, edited, reordered, or normalized. Lifecycle Guidance remains useful after rollback even when the restored RFC is in completed history. Arbitrary targets, multi-step rollback, generalized event replay, source restoration, and Git rollback are future work.

### Rollback eligibility and Preview

`previewCurrentRfcRollback` and `rollbackCurrentRfc` use the same internal Service resolver, so eligibility cannot be weaker than execution. Eligible output predicts the current and restored RFC, restored phase and release, and the deterministic all-`pending` Release Readiness state. Ineligible output contains the resolver's stable blocking reason. Normal business ineligibility is structured output; invalid persisted JSON may still be rejected by the Repository before domain preview calculation.

Preview data is derived each time and is never added to `project-state.json`. Preview performs no save, appends no lifecycle event, consumes no event ID, and does not mutate readiness or the loaded status. Dashboard, Main Planning structured output and Markdown, and the read-only planning Prompt expose it additively. The explicit Main Planning Tool retains its established single `planningSynced` event; Preview itself creates no additional event. Rollback remains exceptional corrective behavior and does not replace Lifecycle Guidance's forward-workflow recommendation. Multi-step Preview and an operator confirmation workflow remain future work.

## Commands

Run commands from `tools/docpilot-mcp`.

Build (TypeScript type-check, without emitted files):

```sh
npm run build
```

Development server:

```sh
npm run dev
```

MCP Inspector:

```sh
npm run inspector
```

Tests (single run):

```sh
npm.cmd test
```

Tests (watch mode):

```sh
npm.cmd run test:watch
```

## Automated tests

Vitest is the single test framework. Tests live under `tests/`, grouped into `repository`, `service`, `resource`, `tool`, `server`, and shared `support` directories. Persistence tests create isolated directories through the operating system temporary-directory APIs and remove them after each test; they never use the runtime `project-state.json`.

The current suites cover repository serialization and backward compatibility, service workflows and validation, dashboard Resource behavior, the Release Readiness Tool, server registration, and smoke checks for existing Tools, the status Resource, and the planning Prompt. Coverage reporting is not configured, and the planning output details, every error branch of older Tools, and stdio process startup are not exhaustively tested.

## Current limitations

- Completion Readiness still evaluates a submitted Handoff, while v0.12 execution independently collects process and Git evidence before generating that Handoff.
- Project Control does not acknowledge, consume, or archive Pending Handoffs.
- Local Codex execution depends on a compatible installed CLI. No cloud Worker, retry queue, push, PR, merge, tag, release, or CI/CD execution is implemented.

- Pending Handoff supports one current-RFC item with reject-on-duplicate behavior; consumption, archival history, approval, worker orchestration, Git automation, and automatic RFC advancement are not implemented.
- Detailed RFC definitions are not persisted, so Context warns about unavailable scope and acceptance metadata.

- Release Readiness is manually updated; automated build, test, and release-system integrations are not implemented yet.
- Planning status detects Documentation Sync mismatch but does not automatically update readiness or enforce a release gate.
- The legacy `completeCurrentRfc` operation remains supported, so clients can still bypass the preferred split lifecycle.
- Lifecycle guidance is derived only from current status; it does not track transition history or distinguish legacy advancement from ordinary in-progress work.
- Rollback supports only the immediately previous lifecycle transition; arbitrary targets, consecutive rollback, and generalized replay are not implemented.
- Documentation operations are not implemented yet.
- Persistence is a single local JSON file with no concurrency control, history, migrations, or remote backend.
- The state file is not initialized automatically and errors are returned when it is missing or invalid.
- The server currently exposes only the stdio transport and has no authentication or multi-project selection.
- Automated tests cover the core layers and MCP registrations but do not yet exercise the stdio entry point as a child process or provide coverage metrics.
