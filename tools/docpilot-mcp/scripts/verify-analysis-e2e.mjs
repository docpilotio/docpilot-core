import { writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { LocalCodexWorkerAdapter } from "../dist/orchestration/CodexWorkerAdapter.js";
import { ControlledProcessRunner } from "../dist/orchestration/ControlledProcessRunner.js";
import { GitRepositoryController } from "../dist/orchestration/GitRepositoryController.js";
import { OrchestrationRuntime } from "../dist/orchestration/OrchestrationRuntime.js";
import { RepositoryExecutionLock } from "../dist/orchestration/RepositoryExecutionLock.js";
import { ProjectStateRepository } from "../dist/repository/ProjectStateRepository.js";
import { ImplementationOrchestrationService } from "../dist/service/ImplementationOrchestrationService.js";
import { ProjectStatusService } from "../dist/service/ProjectStatusService.js";

const [coreArgument, runtimeArgument] = process.argv.slice(2);
if (coreArgument === undefined || runtimeArgument === undefined) {
  throw new Error("Usage: node scripts/verify-analysis-e2e.mjs <core-repository-root> <runtime-root>");
}

const coreRoot = resolve(coreArgument);
const runtime = new OrchestrationRuntime(resolve(runtimeArgument));
const stateFile = await runtime.stateFilePath();
if (stateFile === undefined) throw new Error("An explicit runtime root is required.");

const baselineState = {
  project: "DocPilot",
  phase: "Phase 1 - MVP / POC",
  currentRfc: "RFC-0044",
  release: "v0.5 MVP",
  completedRfcs: Array.from({ length: 43 }, (_, index) => `RFC-${String(index + 1).padStart(4, "0")}`),
};
await writeFile(stateFile, `${JSON.stringify(baselineState, null, 2)}\n`, "utf8");

const repository = new ProjectStateRepository(stateFile);
const project = new ProjectStatusService(repository);
const runner = new ControlledProcessRunner();
const git = new GitRepositoryController(runner);
const worker = new LocalCodexWorkerAdapter(runner);
const service = new ImplementationOrchestrationService(
  repository,
  project,
  runner,
  git,
  worker,
  new RepositoryExecutionLock(),
  runtime,
);

const order = await service.prepareWorkOrder({
  mode: "ANALYSIS",
  repositoryRoot: coreRoot,
  approvedPlan: [
    "Inspect the top-level repository structure using read-only operations.",
    "Identify the latest Main Planning document and current RFC from tracked text.",
    "Summarize the principal Core modules.",
    "Identify official build and test commands from tracked configuration and documentation without running them.",
    "Separate facts that cannot be verified.",
    "Do not create, modify, rename, or delete any file or directory.",
    "Do not run Gradle, npm, build, test, smoke, or other commands that create caches or output.",
    "Do not run Git commands that change the index, HEAD, branch, or working tree.",
  ],
  allowedPaths: ["**"],
  forbiddenPaths: [],
});

const preflight = await service.preflight();
if (preflight.status !== "PASSED") {
  throw new Error(`Analysis preflight failed: ${JSON.stringify(preflight.blockers)}`);
}

const execution = await service.execute();
const lock = await new RepositoryExecutionLock().inspect(coreRoot, order.runtime?.lockDirectory);
const summary = {
  workOrderId: order.id,
  mode: order.mode,
  sandbox: order.execution.codexArguments,
  runtime: order.runtime,
  preflight: preflight.status,
  executionStatus: execution.execution?.status,
  analysis: execution.execution?.analysis,
  worker: {
    status: execution.execution?.workerExecution?.status,
    exitCode: execution.execution?.workerExecution?.exitCode,
    jsonlEventsSaved: execution.execution?.workerExecution?.jsonlEventsSaved,
    resultFileFound: execution.execution?.workerExecution?.resultFileFound,
  },
  lockAfterExecution: lock.state,
};
process.stdout.write(`${JSON.stringify(summary, null, 2)}\n`);
if (execution.execution?.status !== "SUCCEEDED" || lock.state !== "ABSENT") process.exitCode = 1;
