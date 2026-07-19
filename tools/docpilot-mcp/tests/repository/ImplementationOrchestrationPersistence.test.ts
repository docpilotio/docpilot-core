import { afterEach, describe, expect, it } from "vitest";
import type { ImplementationWorkOrder } from "../../src/model/ImplementationOrchestration.js";
import { createProjectStatus, createTemporaryState, type TemporaryState } from "../support/testState.js";

const workOrder: ImplementationWorkOrder = {
  schemaVersion: "1.0", id: "RFC-0039-abcdef123456", rfcId: "RFC-0039",
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

  it("recovers an orphaned RUNNING record as BLOCKED without rewriting on read", async () => {
    state = await createTemporaryState(createProjectStatus({ pendingImplementationWorkOrder: workOrder, implementationExecutionRecord: { schemaVersion: "1.0", rfcId: "RFC-0039", workOrderId: workOrder.id, status: "RUNNING", baselineCommit: workOrder.repository.baselineCommit, warnings: [], errors: [] } }));
    const loaded = await state.repository.load();
    expect(loaded.implementationExecutionRecord).toMatchObject({ status: "BLOCKED" });
    expect(loaded.implementationExecutionRecord?.warnings).toContain("Recovered an untracked RUNNING execution after restart; automatic retry is disabled.");
  });

  it("rejects unsupported Work Order schema versions", async () => {
    state = await createTemporaryState({ ...createProjectStatus(), pendingImplementationWorkOrder: { ...workOrder, schemaVersion: "2.0" } });
    await expect(state.repository.load()).rejects.toThrow("unsupported Work Order schemaVersion");
  });
});
