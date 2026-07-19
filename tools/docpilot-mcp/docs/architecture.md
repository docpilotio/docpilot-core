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

`src/model/ProjectStatus.ts` defines the shared `ProjectStatus` data shape: `project`, `phase`, `currentRfc`, `release`, and `completedRfcs`. It is a TypeScript model rather than an active domain entity; business behavior remains in the service.

## Repository

`ProjectStateRepository` is the persistence boundary. It resolves `project-state.json` from the process working directory by default, reads and parses JSON, validates the complete persisted shape, and returns a defensive copy of `completedRfcs`.

On save, it validates the model, formats the JSON, writes `project-state.tmp.json`, and renames that file to `project-state.json`. The repository therefore owns persistence mechanics, persistence-boundary validation, and serialization. Callers do not read or write the state file directly.

## Service

`ProjectStatusService` coordinates all current application behavior. It loads and saves state through `ProjectStateRepository`, shapes query results, generates the Main Planning Markdown summary, and owns business validation and RFC workflow rules.

In particular, the service trims mutable inputs, rejects empty phase or release values, requires RFC identifiers to match `RFC-0000`, requires at least one field for status updates, prevents completion into the same RFC, and avoids adding a duplicate completed RFC.

## Tool

Tool modules register MCP operations and adapt MCP inputs and outputs to service calls. They own their published MCP schemas and error response formatting, but do not own business rules or persistence.

Current Tools are grouped as follows:

- Project Status: `getProjectStatus`, `getCurrentRfc`, `updateProjectStatus`, and `listCompletedRfcs`.
- RFC Workflow: `completeCurrentRfc`.
- Planning: `generateMainPlanningSync`.

All Tools call `ProjectStatusService`; none accesses `ProjectStateRepository` directly.

## Resource

`ProjectStatusResource` registers the existing `project-status` Resource at the stable URI `docpilot://project/status`. It reads current state through `ProjectStatusService` and returns formatted JSON with the `application/json` media type.

Resources may read through Services or a dedicated read abstraction. No project dashboard Resource exists yet.

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
  "completedRfcs": []
}
```

Its location depends on the working directory used to start the server. It is runtime state, is ignored by Git, and must not be committed. There is no default creation, locking, schema version, migration, backup, or multi-project persistence strategy.

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
- `ProjectStatusService` validates business inputs and workflow transitions.
- `ProjectStateRepository` validates persisted data before returning or saving it.

This overlap at external boundaries is intentional. Business rules must remain enforceable even when the service is called outside an MCP Tool callback.

## Current architectural boundaries

The package is a local, single-process control plane backed by one JSON document. It covers project status queries and updates, RFC completion, completed RFC reporting, Main Planning generation, one status Resource, and one planning Prompt.

The current boundary excludes a `ProjectDashboardResource`, Release Readiness implementation, dedicated documentation operations, additional transports, authentication, concurrent-writer coordination, persistence migrations, and remote storage. Those are follow-up product capabilities and should be introduced without bypassing the established layers or changing existing MCP contracts unintentionally.
