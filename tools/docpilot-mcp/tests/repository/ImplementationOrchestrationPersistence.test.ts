import { afterEach, describe, expect, it } from "vitest";
import { readFile, writeFile } from "node:fs/promises";
import { join } from "node:path";
import type { ImplementationWorkOrder } from "../../src/model/ImplementationOrchestration.js";
import { createProjectStatus, createTemporaryState, type TemporaryState } from "../support/testState.js";

const workOrder: ImplementationWorkOrder = {
  schemaVersion: "1.0", id: "RFC-0039-abcdef123456", rfcId: "RFC-0039", mode: "IMPLEMENTATION",
  repository: { rootPath: "C:/repo", baselineBranch: "feature", baselineCommit: "abcdef1234567890", workingDirectory: "C:/repo" },
  objective: { goal: "Implement", approvedPlan: ["Step"], acceptanceCriteria: [], alphaCriteria: [] },
  scope: { allowedPaths: ["tools/docpilot-mcp/**"], forbiddenPaths: [], allowUntrackedFiles: true, allowDependencyChanges: false, allowBuildConfigurationChanges: false, allowPublicApiChanges: false },
  execution: { codexCommand: "codex.cmd", codexArguments: ["exec"], timeoutSeconds: 60, maxOutputCharacters: 1000, environmentAllowlist: ["PATH"] },
  verification: { targetedCommands: [], moduleCommands: [], buildCommands: [], regressionCommands: [], smokeCommands: [] },
  gitPolicy: { requireCleanWorkingTree: true, allowCommit: false, requireUserApprovalForPush: true, allowMainBranchPush: false, allowForcePush: false },
  resultContract: { resultFile: ".docpilot/results/result.json", expectedSchemaVersion: "1.0" }, warnings: [],
};

describe("Implementation orchestration persistence", () => {
  let state: TemporaryState | undefined;
  afterEach(async () => { await state?.cleanup(); state = undefined; });

  it("loads v0.11-compatible state without orchestration fields", async () => {
    state = await createTemporaryState(createProjectStatus());
    await expect(state.repository.load()).resolves.not.toHaveProperty("pendingImplementationWorkOrder");
  });

  it("round-trips Pending Work Order and Execution Record deterministically", async () => {
    state = await createTemporaryState();
    const status = createProjectStatus({ pendingImplementationWorkOrder: workOrder, implementationExecutionRecord: { schemaVersion: "1.0", rfcId: "RFC-0039", workOrderId: workOrder.id, status: "CREATED", baselineCommit: workOrder.repository.baselineCommit, warnings: [], errors: [] } });
    await state.repository.save(status);
    await expect(state.repository.load()).resolves.toEqual(status);
  });

  it("defaults a legacy Work Order without mode to IMPLEMENTATION", async () => {
    const { mode: _mode, ...legacyWorkOrder } = workOrder;
    state = await createTemporaryState(createProjectStatus({ pendingImplementationWorkOrder: legacyWorkOrder }));
    expect((await state.repository.load()).pendingImplementationWorkOrder?.mode).toBe("IMPLEMENTATION");
  });

  it("round-trips ANALYSIS mode and external runtime artifacts", async () => {
    const analysis: ImplementationWorkOrder = {
      ...workOrder,
      mode: "ANALYSIS",
      runtime: {
        rootPath: "C:/runtime",
        repositoryKey: "repository-key",
        lockDirectory: "C:/runtime/locks/repository-key/orchestration-lock",
        jsonlFile: "C:/runtime/logs/repository-key/work-order.jsonl",
        resultFile: "C:/runtime/results/repository-key/work-order.json",
        schemaFile: "C:/runtime/schemas/repository-key/work-order.json",
        diagnosticsFile: "C:/runtime/diagnostics/repository-key/work-order.json",
      },
    };
    state = await createTemporaryState(createProjectStatus({ pendingImplementationWorkOrder: analysis }));
    expect((await state.repository.load()).pendingImplementationWorkOrder).toEqual(analysis);
  });

  it("rejects an invalid Work Order mode", async () => {
    state = await createTemporaryState(createProjectStatus({ pendingImplementationWorkOrder: { ...workOrder, mode: "WRITE_ANYTHING" as never } }));
    await expect(state.repository.load()).rejects.toThrow("pendingImplementationWorkOrder.mode");
  });

  it("rejects external runtime artifact paths that escape the configured root", async () => {
    state = await createTemporaryState(createProjectStatus({
      pendingImplementationWorkOrder: {
        ...workOrder,
        mode: "ANALYSIS",
        runtime: {
          rootPath: "C:/runtime",
          repositoryKey: "repository-key",
          lockDirectory: "C:/runtime/locks/repository-key/orchestration-lock",
          jsonlFile: "C:/outside/events.jsonl",
          resultFile: "C:/runtime/results/repository-key/work-order.json",
          schemaFile: "C:/runtime/schemas/repository-key/work-order.json",
          diagnosticsFile: "C:/runtime/diagnostics/repository-key/work-order.json",
        },
      },
    }));
    await expect(state.repository.load()).rejects.toThrow("must remain below runtime.rootPath");
  });

  it("preserves an orphaned RUNNING record for evidence-based Service recovery", async () => {
    state = await createTemporaryState(createProjectStatus({ pendingImplementationWorkOrder: workOrder, implementationExecutionRecord: { schemaVersion: "1.0", rfcId: "RFC-0039", workOrderId: workOrder.id, status: "RUNNING", baselineCommit: workOrder.repository.baselineCommit, warnings: [], errors: [] } }));
    const loaded = await state.repository.load();
    expect(loaded.implementationExecutionRecord).toMatchObject({ status: "RUNNING" });
  });

  it("rejects unsupported Work Order schema versions", async () => {
    state = await createTemporaryState({ ...createProjectStatus(), pendingImplementationWorkOrder: { ...workOrder, schemaVersion: "2.0" } });
    await expect(state.repository.load()).rejects.toThrow("unsupported Work Order schemaVersion");
  });

  it("does not overwrite malformed or truncated JSON", async () => {
    state = await createTemporaryState("not-used"); await writeFile(state.stateFilePath, "{\"project\":");
    const before = await readFile(state.stateFilePath, "utf8"); await expect(state.repository.load()).rejects.toThrow(); expect(await readFile(state.stateFilePath, "utf8")).toBe(before);
  });

  it("ignores but does not delete a leftover atomic temporary file", async () => {
    state = await createTemporaryState(createProjectStatus()); const temporary = join(state.directoryPath, "project-state.tmp.json"); await writeFile(temporary, "{truncated");
    await expect(state.repository.load()).resolves.toMatchObject({ currentRfc: "RFC-0039" }); expect(await readFile(temporary, "utf8")).toBe("{truncated");
  });

  it("loads a v0.12.0 terminal record without new recovery diagnostics", async () => {
    state = await createTemporaryState(createProjectStatus({ pendingImplementationWorkOrder: workOrder, implementationExecutionRecord: { schemaVersion: "1.0", rfcId: "RFC-0039", workOrderId: workOrder.id, status: "FAILED", baselineCommit: workOrder.repository.baselineCommit, warnings: [], errors: ["legacy failure"] } }));
    const loaded = await state.repository.load(); expect(loaded.implementationExecutionRecord).toMatchObject({ status: "FAILED", errors: ["legacy failure"] }); expect(loaded.implementationExecutionRecord).not.toHaveProperty("recoveryDiagnostics");
  });
});
