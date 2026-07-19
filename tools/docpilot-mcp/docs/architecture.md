# DocPilot MCP Architecture

## Overview

DocPilot MCP is a TypeScript MCP server using the standard input/output transport. Its current architectural flow is:

```text
MCP client
  -> Tool / Resource / Prompt registration callback
  -> ProjectStatusService
  -> ProjectStateRepository
  -> project-state.json
```

Dependencies point inward toward the service and persistence abstractions used by the current implementation. Tools never skip the service to reach the repository.

## Model

`RfcExecutionContext` is a deterministic, non-persistent query model with schema version `1.0`. `RfcHandoff` is the schema-versioned structured implementation result; verification and reporting states are closed unions. Markdown is renderer output only.

`src/model/ProjectStatus.ts` defines the shared `ProjectStatus` data shape: `project`, `phase`, `currentRfc`, `release`, `completedRfcs`, and `releaseReadiness`. `ReleaseReadinessState` restricts values to `pending`, `passed`, or `failed`, while `ReleaseReadiness` defines the eight supported fields. The model also provides the deterministic all-pending default. It is a TypeScript model rather than an active domain entity; business behavior remains in the service.

`src/model/RfcLifecycleGuidance.ts` defines protocol-independent fixed unions for lifecycle state and recommended action. Guidance is derived at read time and is not part of `ProjectStatus` or the persisted schema.

`src/model/RfcLifecycleEvent.ts` defines readonly lifecycle events with stable `started`, `completed`, `planningSynced`, and compensating `rollbackCompleted` types. Rollback events carry optional `fromRfc` audit evidence; older event types remain loadable without it. `ProjectStatus.lifecycleHistory` preserves append order.

`src/model/RfcRollbackPreview.ts` defines immutable derived rollback eligibility output. It contains no timestamp, ID, Repository dependency, or persistence behavior. Eligible results add the target RFC context and all-pending readiness prediction; ineligible results add one deterministic blocking reason.

`src/model/PlanningSynchronizationStatus.ts` defines immutable `neverSynced`, `current`, and `stale` results, advisory actions, event references, and Documentation Sync consistency fields. The model is derived and is not part of `ProjectStatus` or persistence.

## Repository

The optional additive `pendingRfcHandoff` is validated and serialized through the existing Repository and atomic temporary-file replacement. Missing v0.9 data means no Pending Handoff and causes no migration write. Unsupported schema versions and malformed nested values are rejected. No second file or Registry is introduced.

`ProjectStateRepository` is the persistence boundary. It resolves `project-state.json` from the process working directory by default, reads and parses JSON, validates the complete persisted shape, and returns a defensive copy of `completedRfcs`.

On load, the repository defaults a missing `releaseReadiness` object or any missing readiness fields to `pending`. It rejects unknown readiness fields and invalid stored values. This backward-compatible deserialization does not rewrite the file. On save, it validates the model, formats the complete additive schema as JSON, writes `project-state.tmp.json`, and renames that file to `project-state.json`. The repository therefore owns persistence mechanics, persistence-boundary validation, defaulting, and serialization. Callers do not read or write the state file directly.

The Repository also defaults a missing `lifecycleHistory` to `[]`, validates every event, exact sequential event ID, RFC field, optional rollback `fromRfc`, and ISO timestamp, reconstructs events in stable field order, and serializes the array without reordering it. A rollback event requires a distinct `fromRfc`; other event types reject that field. It owns no event-creation or workflow rules and never rewrites legacy state during a read.

## Service

`loadRfcContext` composes current status, lifecycle guidance, Planning Synchronization, Release Readiness, stable operating rules, and default alpha criteria without saving. Missing RFC-definition metadata is represented explicitly rather than inferred.

`submitRfcHandoff` owns schema/current-RFC validation, deterministic file-path normalization, reject-on-duplicate policy, and one complete Repository save. `getPendingRfcHandoff` is read-only and detects mismatched RFC ownership. Neither operation completes or advances an RFC, changes Planning Synchronization, or appends lifecycle events.

`ProjectStatusService` coordinates all current application behavior. It loads and saves state through `ProjectStateRepository`, shapes query results, generates the Main Planning Markdown summary, and owns business validation and RFC workflow rules.

In particular, the service trims mutable project-status inputs, rejects empty phase or release values, requires RFC identifiers to match `RFC-0000`, requires at least one field for status updates, prevents completion into the same RFC, and avoids adding a duplicate completed RFC. For Release Readiness updates, it rejects empty updates, unknown fields, and values outside `pending`, `passed`, and `failed` before loading or saving state, then preserves all omitted readiness fields.

`markCurrentRfcCompleted` is the mark-only Service transition. It validates the persisted current RFC, explicitly rejects duplicate completion, adds it to a numerically sorted and deduplicated completed history, preserves every other status field including Release Readiness, and issues one Repository save. The Tool exposes this method through a strict empty input schema.

`startNextRfc` is a focused Service-owned transition. It accepts an exact, untrimmed `RFC-[0-9]{4}` identifier plus optional non-empty phase and release updates. Before persistence, the Service verifies that the next RFC differs from the current RFC, is numerically greater, is absent from completed history, and follows a current RFC already present in completed history. It constructs one complete state, preserves completed ordering, resets Release Readiness with the model default, and performs one Repository save. Validation failures do not write state.

`getRfcLifecycleGuidance` derives deterministic guidance from a supplied or freshly loaded status. A valid current RFC absent from completed history is `in_progress` with `markCurrentRfcCompleted`; a current RFC present in completed history is `completed_waiting_next` with `startNextRfc`. Malformed identifiers or duplicate completed entries are `inconsistent` with `manualReview`. The method neither normalizes nor persists state.

The Service appends lifecycle events using deterministic sequence IDs and exposes full-history and latest-event queries. Marking appends `completed`, starting appends `started`, generating Main Planning appends `planningSynced`, and rollback appends `rollbackCompleted`. Append order is the canonical timeline order. Each workflow constructs one complete status and requests one Repository save.

`rollbackCurrentRfc` performs a one-step project-state rollback. Its resolver validates and replays lifecycle events in append order, tracking the active RFC context established by `started` and `rollbackCompleted` transitions while requiring `completed` and `planningSynced` events to refer to that active RFC. The replayed active RFC must match `ProjectStatus.currentRfc`, and the most recent transition must be the `started` event that activated it. The method restores the transition's prior RFC, phase, and release, preserves `completedRfcs` as historical evidence, resets Release Readiness, appends one compensating event, and saves exactly once. It rejects empty, ambiguous, inconsistent, or repeated-rollback histories rather than guessing or subtracting RFC numbers.

`previewCurrentRfcRollback` calls that same internal resolver and converts normal domain failures into a stable ineligible read model. It never saves, appends an event, allocates an ID, or mutates the supplied status. Repository parsing errors remain persistence-boundary errors. This shared resolution policy guarantees that an eligible target, phase, release, and readiness prediction match actual rollback execution.

`getPlanningSynchronizationStatus` validates lifecycle structure and evaluates event array order, never timestamps. It selects the latest `planningSynced` and latest planning-relevant event (`started`, `completed`, or `rollbackCompleted`). No sync is `neverSynced`; a relevant event later than the latest sync is `stale`; otherwise status is `current`. Rollback receives a specific stale reason. A sync matching persisted `currentRfc` may re-anchor evaluation after the legacy combined workflow, whose compatibility contract does not synthesize transition events; strict rollback replay is unchanged.

The evaluator predicts `documentationSync=passed` for `current` and `pending` for other states, compares that expectation with persisted readiness, and reports any mismatch without writing it. Repeated calls are deeply deterministic and perform no save, event append, ID allocation, or model mutation.

## Tool

RFC Context/Handoff Tools are read-only `loadRfcContext`, command `submitRfcHandoff`, and read-only `getPendingRfcHandoff`. Their strict schemas preserve the Command/Query boundary and all call the Service.

Tool modules register MCP operations and adapt MCP inputs and outputs to service calls. They own their published MCP schemas and error response formatting, but do not own business rules or persistence.

Current Tools are grouped as follows:

- Project Status: `getProjectStatus`, `getCurrentRfc`, `updateProjectStatus`, and `listCompletedRfcs`.
- Release Readiness: `updateReleaseReadiness`, which accepts `{ "updates": { ... } }` and persists one or more validated readiness fields.
- RFC Workflow: `markCurrentRfcCompleted`, `startNextRfc`, `rollbackCurrentRfc`, read-only `previewCurrentRfcRollback`, and the legacy combined `completeCurrentRfc` shortcut.
- Planning: writable `generateMainPlanningSync` and read-only `getPlanningSynchronizationStatus`.

All Tools call `ProjectStatusService`; none accesses `ProjectStateRepository` directly.

## Resource

`ProjectStatusResource` registers the existing `project-status` Resource at the stable URI `docpilot://project/status`. It reads current state through `ProjectStatusService` and returns formatted JSON with the `application/json` media type.

`ProjectDashboardResource` registers the read-only `project-dashboard` Resource at `docpilot://project/dashboard`. It calls `ProjectStatusService.getProjectStatus()` and returns `project`, `phase`, `currentRfc`, `release`, the ordered `completedRfcs`, a derived `completedCount`, and persisted `releaseReadiness` as formatted JSON with the `application/json` media type. Its dependency path remains Resource → Service → Repository.

Dashboard reads never write state. The existing project-status Resource serializes the complete `ProjectStatus`, so `releaseReadiness` is an intentional backward-compatible additive field in that Resource's JSON response.

The dashboard also obtains additive `lifecycleGuidance` through `ProjectStatusService`. The Resource contains no lifecycle rules and does not persist or execute the recommendation.

The dashboard returns additive `lifecycleHistory` in persisted order, providing the initial timeline read model without adding Resource-side persistence.

The dashboard also returns additive `rollbackPreview`, calculated from the already loaded status through the Service. This avoids a second state load and keeps every dashboard read side-effect free.

The additive `planningSynchronization` dashboard field is calculated from that same loaded state. Dashboard access never generates Planning or adds `planningSynced`.

Resources may read through Services or a dedicated read abstraction.

## Prompt

Main Planning Markdown additively embeds the deterministic Handoff renderer when a Pending Handoff exists. Structured Handoff remains available through its dedicated query Tool; Planning never parses Markdown or consumes the Handoff.

`GenerateMainPlanningSyncPrompt` registers the `generateMainPlanningSync` Prompt. It accepts optional `completedWork` and `nextWork` arguments, reads current project state through the service, and constructs the existing synchronization message. Prompt behavior and argument schemas are part of the current external contract.

Both the planning Tool and Prompt include an additive RFC Lifecycle section from the same Service-derived guidance. The Tool also exposes the guidance in structured content. Guidance derivation and Prompt generation remain deterministic and read-only.

The explicit planning Tool additionally appends `planningSynced` and renders an RFC Lifecycle Timeline from the resulting history. A rollback line includes both `fromRfc` and the restored `rfc`. Prompt generation remains read-only and does not claim synchronization occurred.

Both planning surfaces add a Rollback Preview section. The explicit Tool calculates Preview after its established `planningSynced` save but Preview itself performs no further save or event append. Prompt Preview remains wholly read-only. Lifecycle Guidance action semantics are unchanged because rollback is an exceptional operator action rather than the recommended forward transition.

Both surfaces also add Planning Synchronization status. Prompt evaluation is fully read-only. Explicit `generateMainPlanningSync` retains exactly one `planningSynced` append and one save; status calculation afterward creates no second event and therefore reports `current`. Completion, start, or actual rollback after that sync makes status stale, whereas rollback Preview and other reads do not.

## Server registration

`src/index.ts` creates the server, connects `StdioServerTransport`, and reports startup or fatal startup errors on standard error. `src/server.ts` is the composition root: it creates one repository, injects it into one service, constructs the MCP server, and registers every Tool, Resource, and Prompt.

The MCP server identity is currently `docpilot-project-control` at version `0.1.0`. Package metadata and MCP server identity are separate concerns.

The preferred lifecycle is `markCurrentRfcCompleted` → `startNextRfc` → `generateMainPlanningSync`. The existing `completeCurrentRfc` contract remains a combined completion-and-advancement operation with its required input and response shape for compatibility. Both completion methods use the same Service-owned numeric ordering and duplicate removal. The Repository remains unaware of workflow semantics, and Main Planning synchronization remains an explicit follow-up Tool or Prompt operation.

## `project-state.json` persistence

The runtime file must contain this logical shape:

```json
{
  "project": "DocPilot",
  "phase": "...",
  "currentRfc": "RFC-0000",
  "release": "...",
  "completedRfcs": [],
  "releaseReadiness": {
    "coreBuild": "pending",
    "coreTests": "pending",
    "cli": "pending",
    "incremental": "pending",
    "reviewWorkflow": "pending",
    "architectureSamplesValidation": "pending",
    "documentationSync": "pending",
    "releaseCandidate": "pending"
  },
  "lifecycleHistory": []
}
```

Its location depends on the working directory used to start the server. It is runtime state, is ignored by Git, and must not be committed. `releaseReadiness` and `lifecycleHistory` are additive schema changes: legacy readiness defaults to `pending`, and missing history defaults to `[]`, without an automatic migration write. There is no default file creation, locking, schema version, migration, backup, or multi-project persistence strategy.

## Dependency direction

- `index` depends on server creation and the MCP stdio transport.
- `server` depends on registrations, the service, and the repository to compose the application.
- Tools depend on the service and MCP/Zod types.
- Resources and Prompts depend on the service and MCP types.
- The service depends on the model and repository.
- The repository depends on the model and Node.js filesystem/path APIs.
- The model has no application-layer dependencies.

The intended request path is Tool/Resource/Prompt → Service → Repository. Direct Tool-to-Repository access is outside the boundary.

## Validation ownership

Validation is deliberately split by responsibility:

- MCP Tool schemas validate protocol-facing input and output shapes.
- `ProjectStatusService` validates business inputs, Release Readiness updates, and workflow transitions.
- `ProjectStateRepository` validates and defaults persisted data before returning or saving it, and owns its serialization.

This overlap at external boundaries is intentional. Business rules must remain enforceable even when the service is called outside an MCP Tool callback.

## Automated testing

Vitest tests are organized entirely under `tests/`: repository persistence compatibility, service business behavior, Resource responses, Tool protocol behavior, server registration, and shared support utilities are separated by directory. MCP-facing tests use the SDK's linked in-memory transports, so no network listener, Inspector, or external server is required.

Every persistence test uses a unique directory created beneath the operating system temporary directory and removes it during teardown. Tests never read or write the runtime `project-state.json`. Repository and service tests use real filesystem persistence where practical; Tool and Resource tests exercise the registered MCP handlers through a client. `npm.cmd run build` and `npm.cmd test` are the required verification commands.

The current foundation does not configure coverage reporting, launch the stdio entry point as a child process, or exhaustively test every legacy Tool error response and planning-output detail.

## Current architectural boundaries

The v0.10 boundary adds RFC execution Context and one restart-safe Pending Handoff. It excludes Handoff consumption/history, approvals, evidence registries, worker orchestration, Git/PR/CI automation, automatic lifecycle advancement, and DocPilot Core integration.

The package is a local, single-process control plane backed by one JSON document. It covers project status queries and updates, RFC completion, completed RFC reporting, Main Planning generation, project status and dashboard Resources, and one planning Prompt.

The current boundary includes mark-only completion, explicit next-RFC startup, one-step project-state rollback and read-only Preview, derived Planning Synchronization Status, the backward-compatible combined completion shortcut, derived lifecycle guidance, append-only lifecycle history, and manually managed persistent Release Readiness. Rollback is a compensating domain transition, not Git or source restoration. Completed RFC records are retained as historical evidence. The boundary excludes automatic Documentation Sync updates, release-gate enforcement, arbitrary or multi-step rollback/Preview, operator confirmation orchestration, generalized event replay, a workflow engine, automated readiness integrations, dedicated documentation operations, additional transports, authentication, concurrent-writer coordination, persistence migrations, and remote storage.
