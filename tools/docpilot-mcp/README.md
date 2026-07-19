# DocPilot MCP

DocPilot MCP is the MCP control plane for DocPilot project status, RFC workflow, documentation, and release operations. This package establishes the initial product foundation while retaining the behavior of the existing implementation.

## Product scope

The current product reads and updates a compact DocPilot project status, supports the current RFC completion workflow, reports completed RFCs, provides a consolidated read-only project dashboard, and generates a Main Planning synchronization artifact. Documentation and release operations are part of the intended control-plane scope, but dedicated documentation operations and persistent Release Readiness are not implemented yet.

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

### RFC Workflow

- `completeCurrentRfc` records the current RFC as completed and advances to a supplied next RFC.

### Planning

- `generateMainPlanningSync` generates a Markdown Main Planning status summary and structured status data.

## Available Resources

- `project-status` at `docpilot://project/status` returns the current project status as `application/json`.
- `project-dashboard` at `docpilot://project/dashboard` returns a consolidated read-only dashboard as `application/json`. Its fields are `project`, `phase`, `currentRfc`, `release`, `completedCount`, `completedRfcs`, and `releaseReadiness`. Current project and RFC values come from `ProjectStatusService`; `completedCount` is derived from the ordered `completedRfcs` array.

The dashboard's `releaseReadiness` object currently reports `pending` for core build, core tests, CLI, incremental processing, review workflow, architecture-samples validation, documentation sync, and release candidate readiness. These values are a non-persistent read-model placeholder and are not added to `project-state.json`.

## Available Prompts

- `generateMainPlanningSync` creates the existing Main Planning synchronization prompt. Optional `completedWork` and `nextWork` arguments add workflow context; current project data is loaded through the service.

## Persistence model

Runtime state is stored in `project-state.json`, resolved relative to the process working directory. The repository parses and validates the complete status shape on reads and writes. Saves serialize formatted JSON to `project-state.tmp.json` and rename it over `project-state.json`. The runtime state and temporary state files are not source artifacts and must not be committed.

The server expects `project-state.json` to exist and contain string values for `project`, `phase`, `currentRfc`, and `release`, plus a string array named `completedRfcs`. There is currently no automatic initialization or migration.

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

## Current limitations

- Release Readiness is not yet persisted or managed as a workflow. The dashboard exposes fixed `pending` placeholders, and persistent readiness state is future work.
- Documentation operations are not implemented yet.
- Persistence is a single local JSON file with no concurrency control, history, migrations, or remote backend.
- The state file is not initialized automatically and errors are returned when it is missing or invalid.
- The server currently exposes only the stdio transport and has no authentication or multi-project selection.
- Automated tests are not currently configured; `npm run build` is the available verification command.
