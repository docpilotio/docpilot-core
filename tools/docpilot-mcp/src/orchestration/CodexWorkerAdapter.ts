import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import type { CodexImplementationResult, CodexWorkerExecution, ImplementationWorkOrder } from "../model/ImplementationOrchestration.js";
import { assertPathInside, type ProcessRunner } from "./ControlledProcessRunner.js";

export interface CodexWorkerAdapter {
  execute(workOrder: ImplementationWorkOrder, prompt: string, signal?: AbortSignal): Promise<CodexWorkerExecution>;
}

export class LocalCodexWorkerAdapter implements CodexWorkerAdapter {
  public constructor(private readonly runner: ProcessRunner) {}

  public async execute(workOrder: ImplementationWorkOrder, prompt: string, signal?: AbortSignal): Promise<CodexWorkerExecution> {
    const resultPath = resolve(workOrder.repository.rootPath, workOrder.resultContract.resultFile);
    await assertPathInside(workOrder.repository.rootPath, resultPath, "resultFile");
    const processResult = await this.runner.execute({
      executable: workOrder.execution.codexCommand,
      args: [...workOrder.execution.codexArguments, prompt],
      workingDirectory: workOrder.repository.workingDirectory,
      repositoryRoot: workOrder.repository.rootPath,
      timeoutSeconds: workOrder.execution.timeoutSeconds,
      maxOutputCharacters: workOrder.execution.maxOutputCharacters,
      environmentAllowlist: workOrder.execution.environmentAllowlist,
    }, signal);
    const resultFileFound = await readFile(resultPath, "utf8").then(() => true, () => false);
    return {
      schemaVersion: "1.0", rfcId: workOrder.rfcId, workOrderId: workOrder.id,
      status: processResult.status === "PASSED" ? "SUCCEEDED" : processResult.status === "TIMED_OUT" ? "TIMED_OUT" : processResult.status === "CANCELLED" ? "CANCELLED" : "FAILED",
      ...(processResult.exitCode === undefined ? {} : { exitCode: processResult.exitCode }),
      stdout: processResult.stdout, stderr: processResult.stderr, outputTruncated: processResult.outputTruncated,
      resultFileFound, ...(resultFileFound ? { resultFile: workOrder.resultContract.resultFile } : {}),
      warnings: resultFileFound ? [] : ["Codex did not produce the required result file."], errors: [],
    };
  }
}

export function validateCodexImplementationResult(value: unknown, workOrder: ImplementationWorkOrder): CodexImplementationResult {
  if (typeof value !== "object" || value === null) throw new Error("Codex result must be a JSON object.");
  const result = value as Partial<CodexImplementationResult>;
  if (result.schemaVersion !== workOrder.resultContract.expectedSchemaVersion) throw new Error("Codex result schemaVersion does not match the Work Order.");
  if (result.rfcId !== workOrder.rfcId) throw new Error("Codex result rfcId does not match the Work Order.");
  if (result.workOrderId !== workOrder.id) throw new Error("Codex result workOrderId does not match the Work Order.");
  if (result.implementation === undefined || result.reportedFiles === undefined || result.verification === undefined || result.review === undefined || result.git === undefined) throw new Error("Codex result is missing required sections.");
  if (!["FAILED", "BLOCKED", "PASSED_WITH_LIMITATIONS", "PASSED"].includes(result.implementation.status)) throw new Error("Codex result contains an invalid implementation status.");
  for (const list of [result.implementation.implemented, result.implementation.notImplemented, result.reportedFiles.changed, result.reportedFiles.created, result.reportedFiles.deleted, result.verification.commandsAttempted, result.verification.findings, result.review.findings, result.review.blockers, result.review.warnings, result.review.knownLimitations, result.review.unresolvedItems]) {
    if (!Array.isArray(list) || !list.every((item) => typeof item === "string")) throw new Error("Codex result contains an invalid string array.");
  }
  if (typeof result.implementation.summary !== "string" || typeof result.git.commitCreated !== "boolean" || typeof result.git.pushPerformed !== "boolean") throw new Error("Codex result contains invalid scalar fields.");
  return result as CodexImplementationResult;
}
