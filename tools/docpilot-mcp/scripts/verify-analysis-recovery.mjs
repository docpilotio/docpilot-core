import { readFile, stat, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { LocalCodexWorkerAdapter } from "../dist/orchestration/CodexWorkerAdapter.js";
import { ControlledProcessRunner } from "../dist/orchestration/ControlledProcessRunner.js";
import { GitRepositoryController } from "../dist/orchestration/GitRepositoryController.js";
import { OrchestrationRuntime } from "../dist/orchestration/OrchestrationRuntime.js";
import { RepositoryExecutionLock } from "../dist/orchestration/RepositoryExecutionLock.js";
import { ProjectStateRepository } from "../dist/repository/ProjectStateRepository.js";
import { ImplementationOrchestrationService } from "../dist/service/ImplementationOrchestrationService.js";
import { ProjectStatusService } from "../dist/service/ProjectStatusService.js";

const [kind, coreArgument, runtimeArgument] = process.argv.slice(2);
if (!["timeout", "cancellation"].includes(kind) || coreArgument === undefined || runtimeArgument === undefined) {
  throw new Error("Usage: node scripts/verify-analysis-recovery.mjs <timeout|cancellation> <core-root> <runtime-root>");
}

const coreRoot = resolve(coreArgument);
const runtime = new OrchestrationRuntime(resolve(runtimeArgument));
const stateFile = await runtime.stateFilePath();
if (stateFile === undefined) throw new Error("An explicit runtime root is required.");
const baselineState = JSON.parse(await readFile(resolve("project-state.json"), "utf8"));
delete baselineState.pendingImplementationWorkOrder;
delete baselineState.implementationExecutionRecord;
delete baselineState.pendingRfcHandoff;
await writeFile(stateFile, `${JSON.stringify(baselineState, null, 2)}\n`, "utf8");

const repository = new ProjectStateRepository(stateFile);
const project = new ProjectStatusService(repository);
const runner = new ControlledProcessRunner();
const git = new GitRepositoryController(runner);
const service = new ImplementationOrchestrationService(
  repository,
  project,
  runner,
  git,
  new LocalCodexWorkerAdapter(runner),
  new RepositoryExecutionLock(),
  runtime,
);

let order = await service.prepareWorkOrder({
  mode: "ANALYSIS",
  repositoryRoot: coreRoot,
  approvedPlan: [
    "Inspect tracked repository documentation without changing filesystem or Git state.",
    "Do not run Gradle, npm, build, test, or any state-changing command.",
  ],
  allowedPaths: ["**"],
});
if (kind === "timeout") {
  order = { ...order, execution: { ...order.execution, timeoutSeconds: 1 } };
  const current = await repository.load();
  await repository.save({ ...current, pendingImplementationWorkOrder: order });
}

const controller = new AbortController();
const cancellationTimer = kind === "cancellation"
  ? setTimeout(() => controller.abort(), 1_000)
  : undefined;
let error;
try {
  await service.execute(false, controller.signal);
} catch (caught) {
  error = caught instanceof Error ? caught.message : String(caught);
} finally {
  if (cancellationTimer !== undefined) clearTimeout(cancellationTimer);
}

const finalState = await repository.load();
const lock = await new RepositoryExecutionLock().inspect(coreRoot, order.runtime?.lockDirectory);
const diagnostics = order.runtime === undefined
  ? undefined
  : JSON.parse(await readFile(order.runtime.diagnosticsFile, "utf8"));
const jsonlSize = order.runtime === undefined
  ? 0
  : await stat(order.runtime.jsonlFile).then((value) => value.size, () => 0);
const expectedWorkerStatus = kind === "timeout" ? "TIMED_OUT" : "CANCELLED";
const summary = {
  kind,
  error,
  executionStatus: finalState.implementationExecutionRecord?.status,
  workerStatus: finalState.implementationExecutionRecord?.workerExecution?.status,
  diagnostics,
  jsonlSize,
  lockAfterExecution: lock.state,
};
process.stdout.write(`${JSON.stringify(summary, null, 2)}\n`);
if (
  finalState.implementationExecutionRecord?.workerExecution?.status !== expectedWorkerStatus ||
  diagnostics?.timedOut !== (kind === "timeout") ||
  diagnostics?.cancelled !== (kind === "cancellation") ||
  jsonlSize === 0 ||
  lock.state !== "ABSENT"
) process.exitCode = 1;
