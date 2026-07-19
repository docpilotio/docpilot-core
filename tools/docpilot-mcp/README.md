# DocPilot MCP

DocPilot MCP is the MCP control plane for DocPilot project status, RFC workflow, documentation, and release operations. This package establishes the initial product foundation while retaining the behavior of the existing implementation.

## Product scope

The current product reads and updates a compact DocPilot project status and persistent Release Readiness, supports the current RFC completion workflow, reports completed RFCs, provides a consolidated read-only project dashboard, and generates a Main Planning synchronization artifact. Documentation operations remain part of the intended control-plane scope but are not implemented yet.

## Current architecture

The TypeScript server uses MCP over standard input/output. `src/index.ts` starts the transport, and `src/server.ts` creates the repository and service before registering all Tools, Resources, and Prompts. The implementation is organized into model, repository, service, tool, resource, and prompt layers.

Tools call `ProjectStatusService`; they do not access persistence directly. The service owns business validation and workflow rules. `ProjectStateRepository` owns JSON loading, validation at the persistence boundary, serialization, and atomic replacement of the state file. The project status and dashboard Resources and the planning Prompt read through the service.

See [docs/architecture.md](docs/architecture.md) for the detailed architecture and dependency rules.

## Available Tools

### Project Status

- `getProjectStatus` returns the complete current project status.
- `getCurrentRfc` returns the current RFC with its phase and release context.
- `updateProjectStatus` updates one or more of `phase`, `release`, and `currentRfc` after service validation.
- `listCompletedRfcs` returns completed RFC identifiers, their count, and current project context.
- `updateReleaseReadiness` accepts an `updates` object containing one or more readiness fields and returns the updated project status. Each value must be `pending`, `passed`, or `failed`; omitted fields retain their prior values.

### RFC Workflow

- `markCurrentRfcCompleted` accepts strict empty input (`{}`), marks the current RFC completed, and leaves the current RFC, phase, release, and Release Readiness unchanged.
- `startNextRfc` starts an explicitly supplied later RFC, preserves completed history, optionally updates `phase` and `release`, and resets all Release Readiness fields to `pending`. Input is `{ "nextRfc": "RFC-0045", "phase"?: "...", "release"?: "..." }`; no other fields are accepted.
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

## Available Resources

- `project-status` at `docpilot://project/status` returns the current project status as `application/json`.
- `project-dashboard` at `docpilot://project/dashboard` returns a consolidated read-only dashboard as `application/json`. Its fields are `project`, `phase`, `currentRfc`, `release`, `completedCount`, `completedRfcs`, and `releaseReadiness`. Current values, including persisted readiness, come from `ProjectStatusService`; `completedCount` is derived from the ordered `completedRfcs` array.

The `releaseReadiness` object contains `coreBuild`, `coreTests`, `cli`, `incremental`, `reviewWorkflow`, `architectureSamplesValidation`, `documentationSync`, and `releaseCandidate`. Each field is persisted as `pending`, `passed`, or `failed`.

The dashboard also exposes the append-only `lifecycleHistory` array in persisted order.

## Available Prompts

- `generateMainPlanningSync` creates the existing Main Planning synchronization prompt. Optional `completedWork` and `nextWork` arguments add workflow context; current project data is loaded through the service.

## Persistence model

Runtime state is stored in `project-state.json`, resolved relative to the process working directory. The repository parses and validates the complete status shape on reads and writes. Saves serialize formatted JSON to `project-state.tmp.json` and rename it over `project-state.json`. The runtime state and temporary state files are not source artifacts and must not be committed.

The server expects `project-state.json` to exist and contain string values for `project`, `phase`, `currentRfc`, and `release`, plus a string array named `completedRfcs`. The additive `releaseReadiness` object stores all eight readiness fields. Legacy files without the object, and objects with missing individual fields, load with deterministic `pending` defaults in memory. Reads do not rewrite legacy files; the complete readiness object is serialized on the next normal save. Invalid readiness values are rejected.

## RFC Lifecycle History

`lifecycleHistory` is an additive, append-only array stored alongside project status. Legacy files without it load with an empty history and are not rewritten merely by reading. Each immutable event contains `id`, `type`, `rfc`, `phase`, `release`, and an ISO `timestamp`; event types are `started`, `completed`, and `planningSynced`.

The Service assigns deterministic sequence IDs such as `rfc-event-000001` and preserves array order as event order. `markCurrentRfcCompleted` appends `completed`, `startNextRfc` appends `started`, and the explicit `generateMainPlanningSync` Tool appends `planningSynced`. Each event is included in the same complete state save as its operation. The legacy `completeCurrentRfc` shortcut remains behaviorally unchanged and does not synthesize history events.

Main Planning Markdown includes an RFC Lifecycle Timeline derived from persisted events. The Service exposes full and latest-event queries, and the dashboard returns the full history. Rollback, timeline-specific Resources, and lifecycle automation are future work.

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

- Release Readiness is manually updated; automated build, test, and release-system integrations are not implemented yet.
- The legacy `completeCurrentRfc` operation remains supported, so clients can still bypass the preferred split lifecycle.
- Lifecycle guidance is derived only from current status; it does not track transition history or distinguish legacy advancement from ordinary in-progress work.
- Lifecycle history is audit data only; rollback and automatic workflow execution are not implemented.
- Documentation operations are not implemented yet.
- Persistence is a single local JSON file with no concurrency control, history, migrations, or remote backend.
- The state file is not initialized automatically and errors are returned when it is missing or invalid.
- The server currently exposes only the stdio transport and has no authentication or multi-project selection.
- Automated tests cover the core layers and MCP registrations but do not yet exercise the stdio entry point as a child process or provide coverage metrics.
