import { execFile } from "node:child_process";
import { mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { promisify } from "node:util";
import { afterEach, describe, expect, it } from "vitest";
import { ControlledProcessRunner } from "../../src/orchestration/ControlledProcessRunner.js";
import { GitRepositoryController } from "../../src/orchestration/GitRepositoryController.js";
import type { CodexWorkerAdapter } from "../../src/orchestration/CodexWorkerAdapter.js";
import type { ImplementationWorkOrder } from "../../src/model/ImplementationOrchestration.js";
import { ProjectStateRepository } from "../../src/repository/ProjectStateRepository.js";
import { ImplementationOrchestrationService, renderCodexImplementationPrompt, validateRepositoryDiff } from "../../src/service/ImplementationOrchestrationService.js";
import { ProjectStatusService } from "../../src/service/ProjectStatusService.js";
import { RepositoryExecutionLock } from "../../src/orchestration/RepositoryExecutionLock.js";
import { OrchestrationRuntime } from "../../src/orchestration/OrchestrationRuntime.js";
import { createProjectStatus } from "../support/testState.js";

const exec = promisify(execFile);

describe("ImplementationOrchestrationService", () => {
  const directories: string[] = [];
  afterEach(async () => { await Promise.all(directories.splice(0).map((path) => rm(path, { recursive: true, force: true }))); });

  async function fixture(worker?: CodexWorkerAdapter, useExternalRuntime = false) {
    const directory = await mkdtemp(join(tmpdir(), "docpilot-orchestration-")); directories.push(directory);
    const gitRoot = join(directory, "repository"); await mkdir(gitRoot);
    await exec("git", ["init", "-b", "feature/rfc", gitRoot]);
    await exec("git", ["-C", gitRoot, "config", "user.email", "test@example.com"]);
    await exec("git", ["-C", gitRoot, "config", "user.name", "Test"]);
    await writeFile(join(gitRoot, "README.md"), "baseline\n");
    await exec("git", ["-C", gitRoot, "add", "README.md"]); await exec("git", ["-C", gitRoot, "commit", "-m", "baseline"]);
    const statePath = join(directory, "state.json");
    const repository = new ProjectStateRepository(statePath); await repository.save(createProjectStatus());
    const project = new ProjectStatusService(repository); const runner = new ControlledProcessRunner(); const git = new GitRepositoryController(runner);
    const fallback: CodexWorkerAdapter = { execute: async (order) => ({ schemaVersion: "1.0", rfcId: order.rfcId, workOrderId: order.id, status: "FAILED", stdout: "", stderr: "", outputTruncated: false, resultFileFound: false, warnings: [], errors: [] }) };
    const runtime = useExternalRuntime ? new OrchestrationRuntime(join(directory, "runtime")) : new OrchestrationRuntime();
    return { directory, gitRoot, repository, project, runtime, service: new ImplementationOrchestrationService(repository, project, runner, git, worker ?? fallback, undefined, runtime) };
  }

  it("creates a deterministic persisted Work Order and restores it after restart", async () => {
    const state = await fixture();
    const input = { repositoryRoot: state.gitRoot, approvedPlan: ["Second", "First", "First"], allowedPaths: ["docs/**", "docs/**"], forbiddenPaths: ["secrets/**"] };
    const order = await state.service.prepareWorkOrder(input);
    expect(order.id).toMatch(/^RFC-0039-[0-9a-f]{12}$/);
    expect(order.objective.approvedPlan).toEqual(["First", "Second"]);
    expect(order.mode).toBe("IMPLEMENTATION");
    expect(order.execution.codexArguments).toContain("workspace-write");
    expect(order.scope.allowedPaths).toEqual(["docs/**"]);
    expect(order.gitPolicy).toMatchObject({ requireCleanWorkingTree: true, allowMainBranchPush: false, allowForcePush: false, requireUserApprovalForPush: true });
    await expect(state.service.prepareWorkOrder(input)).rejects.toThrow("already exists");
    const restored = new ProjectStatusService(new ProjectStateRepository(join(state.directory, "state.json")));
    expect((await restored.getProjectStatus()).pendingImplementationWorkOrder).toEqual(order);
    expect(renderCodexImplementationPrompt(order)).toBe(renderCodexImplementationPrompt(order));
  });

  it("completes an analysis work order without implementation gates or repository writes", async () => {
    const fake: CodexWorkerAdapter = { execute: async (order) => {
      if (order.runtime === undefined) throw new Error("Expected external runtime artifacts.");
      await mkdir(dirname(order.runtime.resultFile), { recursive: true });
      await writeFile(order.runtime.resultFile, JSON.stringify({
        schemaVersion: "1.0",
        rfcId: order.rfcId,
        workOrderId: order.id,
        implementation: { status: "PASSED", summary: "Read-only repository analysis completed.", implemented: [], notImplemented: [] },
        reportedFiles: { changed: [], created: [], deleted: [] },
        verification: { commandsAttempted: [], findings: ["Repository content inspected without execution."] },
        review: { findings: [], blockers: [], warnings: [], knownLimitations: [], unresolvedItems: [] },
        git: { commitCreated: false, pushPerformed: false },
      }));
      return {
        schemaVersion: "1.0", rfcId: order.rfcId, workOrderId: order.id, status: "SUCCEEDED", exitCode: 0,
        stdout: '{"type":"turn.completed"}\n', stderr: "", outputTruncated: false, resultFileFound: true,
        resultFile: order.runtime.resultFile, jsonlEventsSaved: true, jsonlFile: order.runtime.jsonlFile,
        schemaFile: order.runtime.schemaFile, diagnosticsFile: order.runtime.diagnosticsFile, warnings: [], errors: [],
      };
    } };
    const state = await fixture(fake, true);
    const order = await state.service.prepareWorkOrder({
      mode: "ANALYSIS", repositoryRoot: state.gitRoot, approvedPlan: ["Inspect tracked repository content."], allowedPaths: ["**"],
      verification: { buildCommands: [{ id: "must-not-run", executable: process.execPath, args: ["-e", "process.exit(99)"], timeoutSeconds: 5, required: true, category: "BUILD" }] },
      gitPolicy: { allowCommit: true },
    });
    expect(order.mode).toBe("ANALYSIS");
    expect(order.gitPolicy.allowCommit).toBe(false);
    expect(order.execution.codexArguments).toContain("read-only");
    expect(order.runtime?.rootPath).toBe(join(state.directory, "runtime"));
    const before = await exec("git", ["-C", state.gitRoot, "status", "--porcelain=v1", "--ignored"]);
    const result = await state.service.execute();
    const after = await exec("git", ["-C", state.gitRoot, "status", "--porcelain=v1", "--ignored"]);
    expect(result.execution?.analysis).toMatchObject({ status: "PASSED", filesystemUnchanged: true, gitUnchanged: true, jsonlSaved: true, resultSaved: true });
    expect(result.execution?.verification).toMatchObject({ targeted: [], module: [], build: [], regression: [], smoke: [] });
    expect(result.execution?.alpha?.status).toBe("PASSED");
    expect(after.stdout).toBe(before.stdout);
    expect((await state.repository.load()).pendingRfcHandoff).toBeUndefined();
    expect((await new RepositoryExecutionLock().inspect(state.gitRoot, order.runtime?.lockDirectory)).state).toBe("ABSENT");
    expect(renderCodexImplementationPrompt(order)).toContain("Implementation acceptance, Alpha, build, test, regression, and smoke gates do not apply");
  });

  it("fails analysis completion when the repository filesystem changes", async () => {
    const fake: CodexWorkerAdapter = { execute: async (order) => {
      if (order.runtime === undefined) throw new Error("Expected external runtime artifacts.");
      await writeFile(join(order.repository.rootPath, "unexpected.txt"), "violation");
      await mkdir(dirname(order.runtime.resultFile), { recursive: true });
      await writeFile(order.runtime.resultFile, JSON.stringify({
        schemaVersion: "1.0", rfcId: order.rfcId, workOrderId: order.id,
        implementation: { status: "PASSED", summary: "Analysis.", implemented: [], notImplemented: [] },
        reportedFiles: { changed: [], created: [], deleted: [] }, verification: { commandsAttempted: [], findings: [] },
        review: { findings: [], blockers: [], warnings: [], knownLimitations: [], unresolvedItems: [] },
        git: { commitCreated: false, pushPerformed: false },
      }));
      return { schemaVersion: "1.0", rfcId: order.rfcId, workOrderId: order.id, status: "SUCCEEDED", exitCode: 0, stdout: "{}\n", stderr: "", outputTruncated: false, resultFileFound: true, resultFile: order.runtime.resultFile, jsonlEventsSaved: true, warnings: [], errors: [] };
    } };
    const state = await fixture(fake, true);
    const order = await state.service.prepareWorkOrder({ mode: "ANALYSIS", repositoryRoot: state.gitRoot, approvedPlan: ["Inspect."], allowedPaths: ["**"] });
    const result = await state.service.execute();
    expect(result.execution?.analysis).toMatchObject({ status: "FAILED", filesystemUnchanged: false, gitUnchanged: false });
    expect(result.execution?.errors).toContain("Repository filesystem changed during analysis.");
    expect((await new RepositoryExecutionLock().inspect(state.gitRoot, order.runtime?.lockDirectory)).state).toBe("ABSENT");
    expect((await state.repository.load()).implementationExecutionRecord?.status).toBe("FAILED");
  });

  it("rejects missing plans, unsafe scope, dirty trees, and baseline mismatch", async () => {
    const noPlan = await fixture();
    await expect(noPlan.service.prepareWorkOrder({ repositoryRoot: noPlan.gitRoot, approvedPlan: [], allowedPaths: ["docs"] })).rejects.toThrow("approvedPlan");
    await expect(noPlan.service.prepareWorkOrder({ repositoryRoot: noPlan.gitRoot, approvedPlan: ["x"], allowedPaths: ["../outside"] })).rejects.toThrow("safe repository-relative");
    const state = await fixture();
    const order = await state.service.prepareWorkOrder({ repositoryRoot: state.gitRoot, approvedPlan: ["Implement"], allowedPaths: ["docs/**"] });
    await writeFile(join(state.gitRoot, "dirty.txt"), "dirty");
    const preflight = await state.service.preflight();
    expect(preflight.status).toBe("FAILED");
    expect(preflight.checks.find(({ id }) => id === "WORKING_TREE_POLICY")?.status).toBe("FAILED");
    await rm(join(state.gitRoot, "dirty.txt"));
    await exec("git", ["-C", state.gitRoot, "commit", "--allow-empty", "-m", "advance"]);
    const mismatch = await state.service.preflight();
    expect(mismatch.checks.find(({ id }) => id === "HEAD_MATCHES_BASELINE")?.status).toBe("FAILED");
    expect(order.repository.baselineCommit).not.toBe((await exec("git", ["-C", state.gitRoot, "rev-parse", "HEAD"])).stdout.trim());
  });

  it("dry-run is deterministic and side-effect free", async () => {
    const state = await fixture();
    const order = await state.service.prepareWorkOrder({ repositoryRoot: state.gitRoot, approvedPlan: ["Implement"], allowedPaths: ["docs/**"] });
    await state.repository.save({ ...(await state.repository.load()), pendingImplementationWorkOrder: { ...order, execution: { ...order.execution, codexCommand: process.execPath } } });
    const before = await readFile(join(state.directory, "state.json"), "utf8");
    const first = await state.service.execute(true); const second = await state.service.execute(true);
    expect(first).toEqual(second); expect(first.preflight.status).toBe("PASSED");
    expect(await readFile(join(state.directory, "state.json"), "utf8")).toBe(before);
  });

  it("runs a fake Worker, verifies evidence, and creates a Pending Handoff without lifecycle advance", async () => {
    let root = "";
    const fake: CodexWorkerAdapter = { execute: async (order) => {
      root = order.repository.rootPath; await mkdir(join(root, ".docpilot", "results"), { recursive: true }); await mkdir(join(root, "docs"), { recursive: true }); await writeFile(join(root, "docs", "result.md"), "implemented\n");
      await writeFile(join(root, order.resultContract.resultFile), JSON.stringify({ schemaVersion: "1.0", rfcId: order.rfcId, workOrderId: order.id, implementation: { status: "PASSED", summary: "Implemented controlled scope.", implemented: ["Feature"], notImplemented: [] }, reportedFiles: { changed: [], created: ["docs/result.md"], deleted: [] }, verification: { commandsAttempted: [], findings: [] }, review: { findings: [], blockers: [], warnings: [], knownLimitations: [], unresolvedItems: [] }, git: { commitCreated: false, pushPerformed: false } }));
      return { schemaVersion: "1.0", rfcId: order.rfcId, workOrderId: order.id, status: "SUCCEEDED", exitCode: 0, stdout: "ok", stderr: "", outputTruncated: false, resultFileFound: true, resultFile: order.resultContract.resultFile, warnings: [], errors: [] };
    } };
    const state = await fixture(fake);
    const command = (id: string, category: "TARGETED_TEST" | "MODULE_TEST" | "BUILD" | "REGRESSION_TEST" | "SMOKE") => ({ id, executable: process.execPath, args: ["-e", "process.exit(0)"], timeoutSeconds: 5, required: true, category });
    const order = await state.service.prepareWorkOrder({ repositoryRoot: state.gitRoot, approvedPlan: ["Implement"], allowedPaths: ["docs/**"], gitPolicy: { allowCommit: true }, verification: { targetedCommands: [command("targeted", "TARGETED_TEST")], buildCommands: [command("build", "BUILD")], regressionCommands: [command("regression", "REGRESSION_TEST")], smokeCommands: [command("smoke", "SMOKE")] } });
    await state.repository.save({ ...(await state.repository.load()), pendingImplementationWorkOrder: { ...order, execution: { ...order.execution, codexCommand: process.execPath } } });
    const before = await state.project.getProjectStatus();
    const result = await state.service.execute();
    expect(result.execution?.alpha?.status).toBe("PASSED");
    const after = await state.project.getProjectStatus();
    expect(after.pendingRfcHandoff?.rfcId).toBe("RFC-0039");
    expect((await state.project.evaluateRfcCompletionReadiness()).status).toBe("READY_WITH_WARNINGS");
    expect(after.currentRfc).toBe(before.currentRfc); expect(after.completedRfcs).toEqual(before.completedRfcs); expect(after.lifecycleHistory).toEqual(before.lifecycleHistory);
    expect(root).toBe(state.gitRoot);
    const commit = await state.service.createCommit("Implement controlled result");
    expect(commit.pushStatus).toBe("PENDING_APPROVAL");
    expect((await exec("git", ["-C", state.gitRoot, "show", "--pretty=", "--name-only", "HEAD"])).stdout.trim()).toBe("docs/result.md");
    expect((await exec("git", ["-C", state.gitRoot, "status", "--porcelain"])).stdout).toContain(".docpilot/");
    expect((await state.repository.load()).pendingRfcHandoff?.git).toMatchObject({ commitStatus: "CREATED", pushStatus: "PENDING_APPROVAL", resultingCommit: commit.commitSha });
  });

  it("blocks out-of-scope evidence and Worker report mismatches deterministically", async () => {
    const order = { scope: { allowedPaths: ["docs/**"], forbiddenPaths: ["docs/secret/**"], allowUntrackedFiles: true, allowDependencyChanges: false, allowBuildConfigurationChanges: false, allowPublicApiChanges: false } } as ImplementationWorkOrder;
    const evidence = { schemaVersion: "1.0", branch: "feature", baselineCommit: "a", headCommit: "a", changedFiles: [], createdFiles: [], deletedFiles: [], renamedFiles: [], stagedFiles: [], untrackedFiles: ["src/out.ts", "docs/secret/key.md"], warnings: [] };
    const result = { reportedFiles: { changed: [], created: [], deleted: [] } } as never;
    const validation = validateRepositoryDiff(order, evidence, result);
    expect(validation.status).toBe("FAILED"); expect(validation.forbiddenFiles).toEqual(["docs/secret/key.md"]); expect(validation.unexpectedFiles).toEqual(["src/out.ts"]); expect(validation.warnings).toHaveLength(1);
  });

  it("releases the repository lock after Worker failure and timeout", async () => {
    for (const workerStatus of ["throw", "timeout"] as const) {
      const fake: CodexWorkerAdapter = { execute: async (order) => {
        if (workerStatus === "throw") throw new Error("fake worker failure");
        return { schemaVersion: "1.0", rfcId: order.rfcId, workOrderId: order.id, status: "TIMED_OUT", stdout: "", stderr: "", outputTruncated: false, resultFileFound: false, warnings: [], errors: [] };
      } };
      const state = await fixture(fake);
      const order = await state.service.prepareWorkOrder({ repositoryRoot: state.gitRoot, approvedPlan: ["Implement"], allowedPaths: ["docs/**"] });
      await state.repository.save({ ...(await state.repository.load()), pendingImplementationWorkOrder: { ...order, execution: { ...order.execution, codexCommand: process.execPath } } });
      await expect(state.service.execute()).rejects.toThrow();
      expect((await new RepositoryExecutionLock().inspect(state.gitRoot)).state).toBe("ABSENT");
      expect((await state.repository.load()).implementationExecutionRecord?.status).toBe("FAILED");
    }
  });

  it("releases the repository lock after cancellation", async () => {
    const fake: CodexWorkerAdapter = { execute: async (_order, _prompt, signal) => new Promise((_resolve, reject) => {
      if (signal?.aborted === true) { reject(new Error("fake cancellation")); return; }
      signal?.addEventListener("abort", () => reject(new Error("fake cancellation")), { once: true });
    }) };
    const state = await fixture(fake); const order = await state.service.prepareWorkOrder({ repositoryRoot: state.gitRoot, approvedPlan: ["Implement"], allowedPaths: ["docs/**"] });
    await state.repository.save({ ...(await state.repository.load()), pendingImplementationWorkOrder: { ...order, execution: { ...order.execution, codexCommand: process.execPath } } });
    const controller = new AbortController(); const execution = state.service.execute(false, controller.signal); setTimeout(() => controller.abort(), 200);
    await expect(execution).rejects.toThrow("fake cancellation"); expect((await new RepositoryExecutionLock().inspect(state.gitRoot)).state).toBe("ABSENT");
  });

  it("diagnoses persisted RUNNING state without writing or automatic retry", async () => {
    const state = await fixture(); const order = await state.service.prepareWorkOrder({ repositoryRoot: state.gitRoot, approvedPlan: ["Implement"], allowedPaths: ["docs/**"] });
    await state.repository.save({ ...(await state.repository.load()), implementationExecutionRecord: { schemaVersion: "1.0", rfcId: order.rfcId, workOrderId: order.id, status: "RUNNING", baselineCommit: order.repository.baselineCommit, warnings: [], errors: [] } });
    const before = await readFile(join(state.directory, "state.json"), "utf8");
    const query = await state.service.getPendingWorkOrder();
    expect(query.recoveryDiagnostics).toMatchObject({ status: "INTERRUPTED", lockState: "ABSENT" });
    expect(await readFile(join(state.directory, "state.json"), "utf8")).toBe(before);
    await expect(state.service.execute()).rejects.toThrow("requires recovery review");
    expect((await state.project.getProjectStatus()).currentRfc).toBe("RFC-0039");
    expect((await state.project.getProjectStatus()).pendingRfcHandoff).toBeUndefined();
  });

  it("reports a live lock for persisted RUNNING state and rejects orphan records", async () => {
    const state = await fixture(); const order = await state.service.prepareWorkOrder({ repositoryRoot: state.gitRoot, approvedPlan: ["Implement"], allowedPaths: ["docs/**"] });
    await state.repository.save({ ...(await state.repository.load()), implementationExecutionRecord: { schemaVersion: "1.0", rfcId: order.rfcId, workOrderId: order.id, status: "RUNNING", baselineCommit: order.repository.baselineCommit, warnings: [], errors: [] } });
    const lock = await new RepositoryExecutionLock().acquire(state.gitRoot, order.id, order.rfcId);
    expect((await state.service.getPendingWorkOrder()).recoveryDiagnostics).toMatchObject({ status: "NONE", lockState: "ACTIVE" }); await lock.release();
    await state.repository.save({ ...createProjectStatus(), implementationExecutionRecord: { schemaVersion: "1.0", rfcId: order.rfcId, workOrderId: order.id, status: "FAILED", baselineCommit: order.repository.baselineCommit, warnings: [], errors: [] } });
    await expect(state.service.getPendingWorkOrder()).rejects.toThrow("without its Pending Work Order");
  });

  it("reports orphan Worker result files without changing state", async () => {
    const state = await fixture(); const order = await state.service.prepareWorkOrder({ repositoryRoot: state.gitRoot, approvedPlan: ["Implement"], allowedPaths: ["docs/**"] });
    const loaded = await state.repository.load(); const { implementationExecutionRecord: _record, ...withoutRecord } = loaded; await state.repository.save(withoutRecord);
    const resultPath = join(state.gitRoot, order.resultContract.resultFile); await mkdir(dirname(resultPath), { recursive: true }); await writeFile(resultPath, "{}");
    const before = await readFile(join(state.directory, "state.json"), "utf8"); expect((await state.service.getPendingWorkOrder()).recoveryDiagnostics).toMatchObject({ status: "RECOVERY_REQUIRED" }); expect(await readFile(join(state.directory, "state.json"), "utf8")).toBe(before);
  });
});
