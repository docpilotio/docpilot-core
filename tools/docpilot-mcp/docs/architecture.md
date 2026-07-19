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

`src/model/ProjectStatus.ts` defines the shared `ProjectStatus` data shape: `project`, `phase`, `currentRfc`, `release`, `completedRfcs`, and `releaseReadiness`. `ReleaseReadinessState` restricts values to `pending`, `passed`, or `failed`, while `ReleaseReadiness` defines the eight supported fields. The model also provides the deterministic all-pending default. It is a TypeScript model rather than an active domain entity; business behavior remains in the service.

## Repository

`ProjectStateRepository` is the persistence boundary. It resolves `project-state.json` from the process working directory by default, reads and parses JSON, validates the complete persisted shape, and returns a defensive copy of `completedRfcs`.

On load, the repository defaults a missing `releaseReadiness` object or any missing readiness fields to `pending`. It rejects unknown readiness fields and invalid stored values. This backward-compatible deserialization does not rewrite the file. On save, it validates the model, formats the complete additive schema as JSON, writes `project-state.tmp.json`, and renames that file to `project-state.json`. The repository therefore owns persistence mechanics, persistence-boundary validation, defaulting, and serialization. Callers do not read or write the state file directly.

## Service

`ProjectStatusService` coordinates all current application behavior. It loads and saves state through `ProjectStateRepository`, shapes query results, generates the Main Planning Markdown summary, and owns business validation and RFC workflow rules.

In particular, the service trims mutable inputs, rejects empty phase or release values, requires RFC identifiers to match `RFC-0000`, requires at least one field for status updates, prevents completion into the same RFC, and avoids adding a duplicate completed RFC. For Release Readiness updates, it rejects empty updates, unknown fields, and values outside `pending`, `passed`, and `failed` before loading or saving state, then preserves all omitted readiness fields.

## Tool

Tool modules register MCP operations and adapt MCP inputs and outputs to service calls. They own their published MCP schemas and error response formatting, but do not own business rules or persistence.

Current Tools are grouped as follows:

- Project Status: `getProjectStatus`, `getCurrentRfc`, `updateProjectStatus`, and `listCompletedRfcs`.
- Release Readiness: `updateReleaseReadiness`, which accepts `{ "updates": { ... } }` and persists one or more validated readiness fields.
- RFC Workflow: `completeCurrentRfc`.
- Planning: `generateMainPlanningSync`.

All Tools call `ProjectStatusService`; none accesses `ProjectStateRepository` directly.

## Resource

`ProjectStatusResource` registers the existing `project-status` Resource at the stable URI `docpilot://project/status`. It reads current state through `ProjectStatusService` and returns formatted JSON with the `application/json` media type.

`ProjectDashboardResource` registers the read-only `project-dashboard` Resource at `docpilot://project/dashboard`. It calls `ProjectStatusService.getProjectStatus()` and returns `project`, `phase`, `currentRfc`, `release`, the ordered `completedRfcs`, a derived `completedCount`, and persisted `releaseReadiness` as formatted JSON with the `application/json` media type. Its dependency path remains Resource → Service → Repository.

Dashboard reads never write state. The existing project-status Resource serializes the complete `ProjectStatus`, so `releaseReadiness` is an intentional backward-compatible additive field in that Resource's JSON response.

Resources may read through Services or a dedicated read abstraction.

## Prompt

`GenerateMainPlanningSyncPrompt` registers the `generateMainPlanningSync` Prompt. It accepts optional `completedWork` and `nextWork` arguments, reads current project state through the service, and constructs the existing synchronization message. Prompt behavior and argument schemas are part of the current external contract.

## Server registration

`src/index.ts` creates the server, connects `StdioServerTransport`, and reports startup or fatal startup errors on standard error. `src/server.ts` is the composition root: it creates one repository, injects it into one service, constructs the MCP server, and registers every Tool, Resource, and Prompt.

The MCP server identity is currently `docpilot-project-control` at version `0.1.0`. Package metadata and MCP server identity are separate concerns.

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
  }
}
```

Its location depends on the working directory used to start the server. It is runtime state, is ignored by Git, and must not be committed. `releaseReadiness` is an additive schema change: legacy files and missing individual readiness fields default to `pending` in memory without an automatic migration write. There is no default file creation, locking, schema version, migration, backup, or multi-project persistence strategy.

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

The package is a local, single-process control plane backed by one JSON document. It covers project status queries and updates, RFC completion, completed RFC reporting, Main Planning generation, project status and dashboard Resources, and one planning Prompt.

The current boundary includes manually managed persistent Release Readiness but excludes automated readiness integrations, dedicated documentation operations, additional transports, authentication, concurrent-writer coordination, persistence migrations, and remote storage. Those are follow-up product capabilities and should be introduced without bypassing the established layers or changing existing MCP contracts unintentionally.
