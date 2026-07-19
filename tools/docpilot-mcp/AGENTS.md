# Implementation Rules

- Preserve the existing architecture.
- Do not perform unrelated refactoring.
- Tools must call Services and must not access Repositories directly.
- Repositories own persistence and serialization.
- The Service layer owns business validation and workflow rules.
- Resources may read through Services or dedicated read abstractions.
- Preserve the TypeScript `exactOptionalPropertyTypes` compiler option.
- Preserve existing behavior.
- Do not rename or move existing source files in this task.
- Do not commit generated files.
- Do not commit `node_modules`, `dist`, logs, caches, or runtime `project-state.json`.
- Run `npm run build` after every implementation change.
- Add or update automated tests for every product behavior change.
- Tests must use isolated temporary state and must never use the runtime `project-state.json`.
- Run both `npm run build` and `npm test` successfully before completing work.
