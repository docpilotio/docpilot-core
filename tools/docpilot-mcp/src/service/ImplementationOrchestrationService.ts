import { access, readFile, realpath } from "node:fs/promises";
import { basename, dirname, isAbsolute, relative, resolve } from "node:path";
import type { ProjectStatus } from "../model/ProjectStatus.js";
import { RFC_HANDOFF_SCHEMA_VERSION, type RfcHandoff, type VerificationStatus } from "../model/RfcHandoff.js";
import {
  IMPLEMENTATION_EXECUTION_SCHEMA_VERSION, IMPLEMENTATION_WORK_ORDER_SCHEMA_VERSION,
  type CodexImplementationResult, type CommandExecutionResult, type ControlledCommand,
  type ImplementationExecutionRecord, type ImplementationPreflightResult, type ImplementationWorkOrder,
  type RepositoryDiffValidation, type RepositoryEvidence, type VerificationExecutionSummary,
  type WorkerAlphaResult, type WorkerReviewResult,
} from "../model/ImplementationOrchestration.js";
import { ProjectStateRepository } from "../repository/ProjectStateRepository.js";
import type { ProjectStatusService } from "./ProjectStatusService.js";
import { assertPathInside, maskSensitiveOutput, type ProcessRunner } from "../orchestration/ControlledProcessRunner.js";
import { GitRepositoryController, normalizeGitPath } from "../orchestration/GitRepositoryController.js";
import { type CodexWorkerAdapter, validateCodexImplementationResult } from "../orchestration/CodexWorkerAdapter.js";
import { RepositoryExecutionLock } from "../orchestration/RepositoryExecutionLock.js";

export type ControlledCommandInput = Omit<ControlledCommand, "workingDirectory"> & { workingDirectory?: string };
export type PrepareImplementationWorkOrderInput = {
  repositoryRoot: string; baselineBranch?: string; baselineCommit?: string;
  approvedPlan: string[]; allowedPaths: string[]; forbiddenPaths?: string[];
  verification?: { targetedCommands?: ControlledCommandInput[]; moduleCommands?: ControlledCommandInput[]; buildCommands?: ControlledCommandInput[]; regressionCommands?: ControlledCommandInput[]; smokeCommands?: ControlledCommandInput[] };
  gitPolicy?: { allowCommit?: boolean };
};
export type PendingImplementationWorkOrderResult = { found: boolean; rfcId: string; workOrder?: ImplementationWorkOrder; recoveryDiagnostics?: ImplementationExecutionRecord["recoveryDiagnostics"] };
export type ExecuteImplementationResult = { dryRun: boolean; preflight: ImplementationPreflightResult; prompt: string; command: { executable: string; args: string[]; workingDirectory: string }; execution?: ImplementationExecutionRecord };

const PROCESS_ENVIRONMENT = ["ComSpec", "PATH", "PATHEXT", "Path", "SystemRoot", "TEMP", "TMP"];
const ALLOWED_COMMANDS = new Set(["node", "node.exe", "npm", "npm.cmd", "npx", "npx.cmd"]);
const PREFLIGHT_IDS = ["CURRENT_RFC_MATCH", "WORK_ORDER_PRESENT", "WORK_ORDER_SCHEMA_VALID", "REPOSITORY_EXISTS", "GIT_REPOSITORY", "BASELINE_COMMIT_EXISTS", "HEAD_MATCHES_BASELINE", "BRANCH_POLICY", "WORKING_TREE_POLICY", "ALLOWED_PATHS_PRESENT", "FORBIDDEN_PATHS_VALID", "WORKING_DIRECTORY_VALID", "CODEX_EXECUTABLE_AVAILABLE", "RESULT_PATH_VALID", "COMMAND_POLICY_VALID", "PENDING_HANDOFF_ABSENT"] as const;

export class ImplementationOrchestrationService {
  private running = false;
  public constructor(
    private readonly repository: ProjectStateRepository,
    private readonly projectService: ProjectStatusService,
    private readonly runner: ProcessRunner,
    private readonly git: GitRepositoryController,
    private readonly worker: CodexWorkerAdapter,
    private readonly executionLock: RepositoryExecutionLock = new RepositoryExecutionLock(),
  ) {}

  public async prepareWorkOrder(input: PrepareImplementationWorkOrderInput): Promise<ImplementationWorkOrder> {
    const status = await this.repository.load();
    if (status.pendingRfcHandoff !== undefined) throw new Error("A Pending RFC Handoff already exists; a new implementation run is not allowed.");
    if (status.pendingImplementationWorkOrder !== undefined) throw new Error("A Pending Implementation Work Order already exists for the current RFC.");
    if (status.implementationExecutionRecord !== undefined) throw new Error("An orphaned Implementation Execution Record requires recovery review before preparing a Work Order.");
    if (input.approvedPlan.length === 0) throw new Error("approvedPlan must contain at least one step.");
    if (input.allowedPaths.length === 0) throw new Error("allowedPaths must contain at least one repository-relative path.");
    const root = await realpath(input.repositoryRoot).catch(() => { throw new Error("repositoryRoot does not exist."); });
    if (!await this.git.isRepository(root)) throw new Error("repositoryRoot must be a Git repository.");
    const repositoryTop = await this.runGitTopLevel(root);
    if ((await realpath(repositoryTop)).toLowerCase() !== root.toLowerCase()) throw new Error("repositoryRoot must be the Git repository root.");
    const head = await this.git.resolveHead(root);
    const baselineCommit = input.baselineCommit ?? head;
    if (!await this.git.commitExists(root, baselineCommit)) throw new Error("baselineCommit does not identify an existing commit.");
    if (head !== baselineCommit) throw new Error("baselineCommit must match the current repository HEAD when the Work Order is prepared.");
    const actualBranch = await this.git.resolveBranch(root);
    if (input.baselineBranch !== undefined && input.baselineBranch !== actualBranch) throw new Error("baselineBranch does not match the current branch.");
    const context = await this.projectService.loadRfcContext(undefined, status);
    const codex = await resolveCodexInvocation();
    const allowedPaths = normalizeScopePaths(input.allowedPaths, "allowedPaths");
    const forbiddenPaths = normalizeScopePaths(input.forbiddenPaths ?? [], "forbiddenPaths");
    const id = `${status.currentRfc}-${baselineCommit.slice(0, 12)}`;
    const commandGroup = (commands: ControlledCommandInput[] | undefined, expected: ControlledCommand["category"]): ControlledCommand[] =>
      (commands ?? []).map((command) => this.normalizeCommand(command, expected, root));
    const order: ImplementationWorkOrder = {
      schemaVersion: IMPLEMENTATION_WORK_ORDER_SCHEMA_VERSION, id, rfcId: status.currentRfc,
      repository: { rootPath: root, baselineBranch: actualBranch, baselineCommit, workingDirectory: root },
      objective: {
        goal: context.rfc.goal ?? `Implement the approved scope for ${status.currentRfc}.`,
        approvedPlan: stable(input.approvedPlan), acceptanceCriteria: [...context.acceptanceCriteria], alphaCriteria: [...context.alphaCriteria],
      },
      scope: { allowedPaths, forbiddenPaths, allowUntrackedFiles: true, allowDependencyChanges: false, allowBuildConfigurationChanges: false, allowPublicApiChanges: false },
      execution: { codexCommand: codex.executable, codexArguments: [...codex.prefixArgs, "exec", "--sandbox", "workspace-write", "--cd", root], timeoutSeconds: 1800, maxOutputCharacters: 100_000, environmentAllowlist: PROCESS_ENVIRONMENT },
      verification: {
        targetedCommands: commandGroup(input.verification?.targetedCommands, "TARGETED_TEST"),
        moduleCommands: commandGroup(input.verification?.moduleCommands, "MODULE_TEST"),
        buildCommands: commandGroup(input.verification?.buildCommands, "BUILD"),
        regressionCommands: commandGroup(input.verification?.regressionCommands, "REGRESSION_TEST"),
        smokeCommands: commandGroup(input.verification?.smokeCommands, "SMOKE"),
      },
      gitPolicy: { requireCleanWorkingTree: true, allowCommit: input.gitPolicy?.allowCommit ?? false, requireUserApprovalForPush: true, allowMainBranchPush: false, allowForcePush: false },
      resultContract: { resultFile: `.docpilot/results/${id}.json`, expectedSchemaVersion: "1.0" },
      warnings: ["Push execution is not supported; user approval remains an external boundary."],
    };
    const updated: ProjectStatus = { ...status, pendingImplementationWorkOrder: order, implementationExecutionRecord: this.createdRecord(order) };
    await this.repository.save(updated);
    return order;
  }

  public async getPendingWorkOrder(): Promise<PendingImplementationWorkOrderResult> {
    const status = await this.repository.load();
    const workOrder = status.pendingImplementationWorkOrder;
    if (workOrder === undefined) {
      if (status.implementationExecutionRecord !== undefined) throw new Error("Implementation Execution Record exists without its Pending Work Order; manual recovery is required.");
      return { found: false, rfcId: status.currentRfc };
    }
    if (workOrder.rfcId !== status.currentRfc) throw new Error("Pending Implementation Work Order does not match the current RFC.");
    const resultPath = await assertPathInside(workOrder.repository.rootPath, resolve(workOrder.repository.rootPath, workOrder.resultContract.resultFile), "resultFile");
    const resultExists = await access(resultPath).then(() => true, () => false);
    const record = status.implementationExecutionRecord;
    if (record === undefined && resultExists) return { found: true, rfcId: status.currentRfc, workOrder, recoveryDiagnostics: { status: "RECOVERY_REQUIRED", reason: "Worker result exists without an Implementation Execution Record.", lockState: "ABSENT" } };
    if (record?.workerExecution?.resultFileFound === true && !resultExists) return { found: true, rfcId: status.currentRfc, workOrder, recoveryDiagnostics: { status: "RECOVERY_REQUIRED", reason: "Execution Record references a missing Worker result file.", lockState: "ABSENT" } };
    if (record !== undefined && record.workerExecution === undefined && resultExists) return { found: true, rfcId: status.currentRfc, workOrder, recoveryDiagnostics: { status: "RECOVERY_REQUIRED", reason: "Orphan Worker result requires manual recovery review.", lockState: "ABSENT" } };
    if (status.implementationExecutionRecord?.status !== "RUNNING") return { found: true, rfcId: status.currentRfc, workOrder };
    const inspection = await this.executionLock.inspect(workOrder.repository.rootPath);
    return { found: true, rfcId: status.currentRfc, workOrder, recoveryDiagnostics: { status: inspection.state === "ACTIVE" ? "NONE" : inspection.state === "STALE" || inspection.state === "ABSENT" ? "INTERRUPTED" : "RECOVERY_REQUIRED", reason: inspection.state === "ACTIVE" ? "The persisted RUNNING execution still has a live lock owner." : `The persisted RUNNING execution cannot be resumed automatically: ${inspection.reason}`, lockState: inspection.state } };
  }

  public async preflight(status?: ProjectStatus): Promise<ImplementationPreflightResult> {
    const project = status ?? await this.repository.load();
    const order = project.pendingImplementationWorkOrder;
    const outcomes = new Map<string, { passed: boolean; detail: string }>();
    outcomes.set("WORK_ORDER_PRESENT", { passed: order !== undefined, detail: order === undefined ? "No Pending Implementation Work Order exists." : "Pending Work Order is present." });
    if (order !== undefined) {
      outcomes.set("CURRENT_RFC_MATCH", { passed: order.rfcId === project.currentRfc, detail: "Work Order RFC must match current RFC." });
      outcomes.set("WORK_ORDER_SCHEMA_VALID", { passed: order.schemaVersion === IMPLEMENTATION_WORK_ORDER_SCHEMA_VERSION, detail: "Work Order schemaVersion must be supported." });
      outcomes.set("REPOSITORY_EXISTS", { passed: await access(order.repository.rootPath).then(() => true, () => false), detail: "Repository root must exist." });
      outcomes.set("GIT_REPOSITORY", { passed: await this.git.isRepository(order.repository.rootPath).catch(() => false), detail: "Repository root must be a Git repository." });
      outcomes.set("BASELINE_COMMIT_EXISTS", { passed: await this.git.commitExists(order.repository.rootPath, order.repository.baselineCommit).catch(() => false), detail: "Baseline commit must exist." });
      outcomes.set("HEAD_MATCHES_BASELINE", { passed: await this.git.resolveHead(order.repository.rootPath).then((head) => head === order.repository.baselineCommit, () => false), detail: "HEAD must match the fixed baseline commit." });
      const branch = await this.git.resolveBranch(order.repository.rootPath).catch(() => "");
      outcomes.set("BRANCH_POLICY", { passed: branch !== "", detail: "A named branch is required; push remains unsupported on every branch." });
      outcomes.set("WORKING_TREE_POLICY", { passed: !order.gitPolicy.requireCleanWorkingTree || await this.git.isClean(order.repository.rootPath).catch(() => false), detail: "Working tree must be clean before execution." });
      outcomes.set("ALLOWED_PATHS_PRESENT", { passed: order.scope.allowedPaths.length > 0, detail: "At least one allowed path is required." });
      outcomes.set("FORBIDDEN_PATHS_VALID", { passed: order.scope.forbiddenPaths.every(isSafeRelativePath), detail: "Forbidden paths must be repository-relative." });
      outcomes.set("WORKING_DIRECTORY_VALID", { passed: await assertPathInside(order.repository.rootPath, order.repository.workingDirectory, "workingDirectory").then(() => true, () => false), detail: "Working directory must remain inside the repository." });
      outcomes.set("CODEX_EXECUTABLE_AVAILABLE", { passed: await executableAvailable(order.execution.codexCommand), detail: "Configured Codex executable must be available on PATH." });
      outcomes.set("RESULT_PATH_VALID", { passed: await assertPathInside(order.repository.rootPath, resolve(order.repository.rootPath, order.resultContract.resultFile), "resultFile").then(() => true, () => false), detail: "Result file must remain inside the repository." });
      outcomes.set("COMMAND_POLICY_VALID", { passed: this.allCommands(order).every((command) => this.commandIsValid(command, order.repository.rootPath)), detail: "Verification commands must satisfy the controlled-command policy." });
      outcomes.set("PENDING_HANDOFF_ABSENT", { passed: project.pendingRfcHandoff === undefined, detail: "A Pending RFC Handoff blocks implementation execution." });
    }
    const checks = PREFLIGHT_IDS.map((id) => {
      const outcome = outcomes.get(id) ?? { passed: false, detail: "Check could not be evaluated without a valid Work Order." };
      return { id, required: true, status: outcome.passed ? "PASSED" as const : "FAILED" as const, details: [outcome.detail] };
    });
    const blockers = checks.filter((check) => check.status === "FAILED").map((check) => `${check.id}: ${check.details[0] ?? "failed"}`);
    return { schemaVersion: "1.0", rfcId: order?.rfcId ?? project.currentRfc, workOrderId: order?.id ?? "none", status: blockers.length === 0 ? "PASSED" : "FAILED", checks, blockers, warnings: [] };
  }

  public async execute(dryRun = false, signal?: AbortSignal): Promise<ExecuteImplementationResult> {
    if (this.running) throw new Error("An Implementation Work Order is already running in this server process.");
    const status = await this.repository.load();
    const order = status.pendingImplementationWorkOrder;
    if (order === undefined) throw new Error("No Pending Implementation Work Order exists.");
    if (status.implementationExecutionRecord?.status === "RUNNING") {
      const inspection = await this.executionLock.inspect(order.repository.rootPath);
      throw new Error(`The previous execution is still RUNNING and requires recovery review (${inspection.state}): ${inspection.reason}`);
    }
    if (status.implementationExecutionRecord !== undefined && !["CREATED", "PREFLIGHT_FAILED", "READY"].includes(status.implementationExecutionRecord.status)) throw new Error("The Pending Implementation Work Order has already been executed; automatic retry is disabled.");
    const preflight = await this.preflight(status);
    const prompt = renderCodexImplementationPrompt(order);
    const responseBase = { dryRun, preflight, prompt, command: { executable: order.execution.codexCommand, args: [...order.execution.codexArguments, "<deterministic-prompt>"], workingDirectory: order.repository.workingDirectory } };
    if (dryRun) return responseBase;
    if (preflight.status !== "PASSED") {
      const record = { ...this.createdRecord(order), status: "PREFLIGHT_FAILED" as const, preflight, errors: [...preflight.blockers] };
      await this.repository.save({ ...status, implementationExecutionRecord: record });
      return { ...responseBase, execution: record };
    }
    this.running = true;
    let acquiredLock: Awaited<ReturnType<RepositoryExecutionLock["acquire"]>> | undefined;
    let primaryError: Error | undefined;
    try {
      acquiredLock = await this.executionLock.acquire(order.repository.rootPath, order.id, order.rfcId);
      const repositoryBefore = await this.git.collectEvidence(order.repository.rootPath, order.repository.baselineCommit);
      const nonRuntimeBefore = allEvidenceFiles(repositoryBefore).filter((file) => !isRuntimeFile(file));
      if (repositoryBefore.headCommit !== order.repository.baselineCommit || repositoryBefore.stagedFiles.length > 0 || nonRuntimeBefore.length > 0) throw new Error("Repository changed after Preflight; Worker execution was not started.");
      const running: ImplementationExecutionRecord = { ...this.createdRecord(order), status: "RUNNING", preflight, repositoryBefore, warnings: acquiredLock.staleLockRecovered ? ["A stale execution lock was recovered before this run."] : [] };
      await this.repository.save({ ...status, implementationExecutionRecord: running });
      const workerExecution = await this.worker.execute(order, prompt, signal);
      const result = await this.readWorkerResult(order, workerExecution.resultFileFound);
      const verification = await this.runVerification(order);
      const evidence = await this.git.collectEvidence(order.repository.rootPath, order.repository.baselineCommit);
      const diffValidation = validateRepositoryDiff(order, evidence, result);
      const review = reviewImplementation(result, verification, diffValidation, evidence);
      const alpha = evaluateWorkerAlpha(order, preflight, workerExecution.status, result, verification, diffValidation, review, evidence);
      const executionStatus = alpha.status === "PASSED" || alpha.status === "PASSED_WITH_LIMITATIONS" ? "SUCCEEDED" : alpha.status === "BLOCKED" ? "BLOCKED" : "FAILED";
      const handoff = executionStatus === "SUCCEEDED" ? createHandoff(order, result, verification, diffValidation, review, alpha, evidence) : undefined;
      const record: ImplementationExecutionRecord = {
        schemaVersion: IMPLEMENTATION_EXECUTION_SCHEMA_VERSION, rfcId: order.rfcId, workOrderId: order.id, status: executionStatus,
        baselineCommit: order.repository.baselineCommit, resultingHead: evidence.headCommit, preflight, workerExecution, verification,
        diffValidation, review, alpha, repositoryBefore, ...(handoff === undefined ? {} : { generatedHandoff: handoff }), warnings: stable([...(acquiredLock.staleLockRecovered ? ["A stale execution lock was recovered before this run."] : []), ...alpha.warnings]), errors: [...alpha.blockers],
      };
      await this.repository.save({ ...status, implementationExecutionRecord: record, ...(handoff === undefined ? {} : { pendingRfcHandoff: handoff }) });
      return { ...responseBase, execution: record };
    } catch (error: unknown) {
      const message = maskSensitiveOutput(error instanceof Error ? error.message : "Implementation execution failed unexpectedly.");
      primaryError = error instanceof Error ? error : new Error(message);
      const failed: ImplementationExecutionRecord = { ...this.createdRecord(order), status: "FAILED", preflight, errors: [message] };
      await this.repository.save({ ...status, implementationExecutionRecord: failed });
      throw primaryError;
    } finally {
      try { await acquiredLock?.release(); }
      catch (releaseError: unknown) {
        const diagnostic = maskSensitiveOutput(releaseError instanceof Error ? releaseError.message : "unknown lock release failure");
        if (primaryError !== undefined) primaryError.message = `${primaryError.message} Lock release also failed: ${diagnostic}`;
        else throw new Error(`Implementation completed but lock release failed: ${diagnostic}`);
      }
      finally { this.running = false; }
    }
  }

  public async createCommit(message: string): Promise<{ commitSha: string; pushStatus: "PENDING_APPROVAL" }> {
    const status = await this.repository.load();
    const order = status.pendingImplementationWorkOrder;
    const record = status.implementationExecutionRecord;
    if (order === undefined || record === undefined) throw new Error("No completed Implementation Execution is available.");
    if (!order.gitPolicy.allowCommit) throw new Error("The Work Order does not allow commit creation.");
    if (record.alpha === undefined || !["PASSED", "PASSED_WITH_LIMITATIONS"].includes(record.alpha.status)) throw new Error("Implementation Alpha must pass before a commit can be created.");
    if (record.commitSha !== undefined) throw new Error("An Implementation Commit already exists for this Work Order.");
    if (record.diffValidation?.status === "FAILED") throw new Error("Diff Validation must pass before a commit can be created.");
    const evidence = await this.git.collectEvidence(order.repository.rootPath, order.repository.baselineCommit);
    if (evidence.headCommit !== order.repository.baselineCommit) throw new Error("Repository HEAD already changed; possible prior commit requires recovery review.");
    const files = allEvidenceFiles(evidence).filter((file) => isAllowed(file, order.scope.allowedPaths) && !isRuntimeFile(file));
    const commitSha = await this.git.createCommit(order.repository.rootPath, files, message, order.repository.baselineCommit);
    const updatedHandoff = status.pendingRfcHandoff === undefined ? undefined : { ...status.pendingRfcHandoff, git: { ...status.pendingRfcHandoff.git, resultingCommit: commitSha, commitStatus: "CREATED" as const, pushStatus: "PENDING_APPROVAL" as const } };
    await this.repository.save({ ...status, implementationExecutionRecord: { ...record, commitSha }, ...(updatedHandoff === undefined ? {} : { pendingRfcHandoff: updatedHandoff }) });
    return { commitSha, pushStatus: "PENDING_APPROVAL" };
  }

  private normalizeCommand(input: ControlledCommandInput, expected: ControlledCommand["category"], root: string): ControlledCommand {
    if (input.category !== expected) throw new Error(`Command ${input.id} must use category ${expected}.`);
    const command: ControlledCommand = { ...input, args: [...input.args], workingDirectory: input.workingDirectory === undefined ? root : resolve(root, input.workingDirectory) };
    if (!this.commandIsValid(command, root)) throw new Error(`Command ${input.id} violates the controlled-command policy.`);
    return command;
  }

  private commandIsValid(command: ControlledCommand, root: string): boolean {
    return command.id.trim() !== "" && ALLOWED_COMMANDS.has(basename(command.executable).toLowerCase()) && command.args.every((arg) => !arg.includes("\0")) && Number.isInteger(command.timeoutSeconds) && command.timeoutSeconds > 0 && pathInside(root, command.workingDirectory);
  }
  private allCommands(order: ImplementationWorkOrder): ControlledCommand[] { return [...order.verification.targetedCommands, ...order.verification.moduleCommands, ...order.verification.buildCommands, ...order.verification.regressionCommands, ...order.verification.smokeCommands]; }
  private createdRecord(order: ImplementationWorkOrder): ImplementationExecutionRecord { return { schemaVersion: IMPLEMENTATION_EXECUTION_SCHEMA_VERSION, rfcId: order.rfcId, workOrderId: order.id, status: "CREATED", baselineCommit: order.repository.baselineCommit, warnings: [], errors: [] }; }
  private async runGitTopLevel(root: string): Promise<string> {
    const result = await this.runner.execute({ executable: "git", args: ["rev-parse", "--show-toplevel"], workingDirectory: root, repositoryRoot: root, timeoutSeconds: 30, maxOutputCharacters: 10_000, environmentAllowlist: PROCESS_ENVIRONMENT });
    if (result.status !== "PASSED") throw new Error("repositoryRoot must be a Git repository.");
    return result.stdout.trim();
  }
  private async readWorkerResult(order: ImplementationWorkOrder, found: boolean): Promise<CodexImplementationResult> {
    if (!found) throw new Error("Codex Worker result file is missing.");
    const path = resolve(order.repository.rootPath, order.resultContract.resultFile);
    const value: unknown = JSON.parse(await readFile(path, "utf8"));
    return validateCodexImplementationResult(value, order);
  }
  private async runVerification(order: ImplementationWorkOrder): Promise<VerificationExecutionSummary> {
    let blocked = false;
    const runGroup = async (commands: ControlledCommand[]): Promise<CommandExecutionResult[]> => {
      const results: CommandExecutionResult[] = [];
      for (const command of commands) {
        if (blocked) { results.push({ ...command, status: "SKIPPED", stdout: "", stderr: "", outputTruncated: false }); continue; }
        const processResult = await this.runner.execute({ executable: command.executable, args: command.args, workingDirectory: command.workingDirectory, repositoryRoot: order.repository.rootPath, timeoutSeconds: command.timeoutSeconds, maxOutputCharacters: order.execution.maxOutputCharacters, environmentAllowlist: order.execution.environmentAllowlist });
        const status = processResult.status === "PASSED" ? "PASSED" : processResult.status === "TIMED_OUT" ? "TIMED_OUT" : processResult.status === "CANCELLED" ? "BLOCKED" : "FAILED";
        results.push({ ...command, status, ...(processResult.exitCode === undefined ? {} : { exitCode: processResult.exitCode }), stdout: processResult.stdout, stderr: processResult.stderr, outputTruncated: processResult.outputTruncated });
        if (command.required && status !== "PASSED") blocked = true;
      }
      return results;
    };
    const targeted = await runGroup(order.verification.targetedCommands);
    const module = await runGroup(order.verification.moduleCommands);
    const build = await runGroup(order.verification.buildCommands);
    const regression = await runGroup(order.verification.regressionCommands);
    const smoke = await runGroup(order.verification.smokeCommands);
    const all = [...targeted, ...module, ...build, ...regression, ...smoke];
    const blockers = all.filter((item) => item.required && item.status !== "PASSED").map((item) => `Required command ${item.id} did not pass.`);
    const warnings = all.filter((item) => !item.required && item.status !== "PASSED").map((item) => `Optional command ${item.id} did not pass.`);
    return { schemaVersion: "1.0", status: blockers.length > 0 ? "FAILED" : warnings.length > 0 ? "PASSED_WITH_LIMITATIONS" : "PASSED", targeted, module, build, regression, smoke, blockers, warnings };
  }
}

export function renderCodexImplementationPrompt(order: ImplementationWorkOrder): string {
  const commands = [...order.verification.targetedCommands, ...order.verification.moduleCommands, ...order.verification.buildCommands, ...order.verification.regressionCommands, ...order.verification.smokeCommands].map((command) => `${command.id}: ${command.executable} ${command.args.join(" ")}`);
  return [
    `Implement ${order.rfcId} under this approved Work Order.`, `Goal: ${order.objective.goal}`,
    "Approved plan:", ...order.objective.approvedPlan.map((item) => `- ${item}`),
    "Allowed paths:", ...order.scope.allowedPaths.map((item) => `- ${item}`),
    "Forbidden paths:", ...order.scope.forbiddenPaths.map((item) => `- ${item}`),
    "Acceptance criteria:", ...order.objective.acceptanceCriteria.map((item) => `- ${item}`),
    "Alpha criteria:", ...order.objective.alphaCriteria.map((item) => `- ${item.id}: ${item.description}`),
    "Verification commands:", ...commands.map((item) => `- ${item}`),
    `Baseline commit: ${order.repository.baselineCommit}`,
    `Result JSON: ${order.resultContract.resultFile} (schemaVersion ${order.resultContract.expectedSchemaVersion}, rfcId ${order.rfcId}, workOrderId ${order.id}).`,
    "Do not push, force-push, change branches, complete or advance the RFC, or modify paths outside scope. Implement, verify, review, and write the structured result without requesting confirmation.",
  ].join("\n");
}

export function validateRepositoryDiff(order: ImplementationWorkOrder, evidence: RepositoryEvidence, result: CodexImplementationResult): RepositoryDiffValidation {
  const files = allEvidenceFiles(evidence).filter((file) => !isRuntimeFile(file));
  const forbiddenFiles = files.filter((file) => order.scope.forbiddenPaths.some((scope) => pathMatches(file, scope)));
  const unexpectedFiles = files.filter((file) => !isAllowed(file, order.scope.allowedPaths));
  const dependencyChanges = files.filter((file) => /(^|\/)(package(-lock)?\.json|.*\.lock|pom\.xml)$/.test(file));
  const buildConfigurationChanges = files.filter((file) => /(^|\/)(tsconfig.*\.json|build\.gradle.*|settings\.gradle.*|vite\.config\..*)$/.test(file));
  const publicApiChangeCandidates = files.filter((file) => /(^|\/)(src\/index\.(ts|tsx)|src\/public-api\.(ts|tsx)|api\/.*\.(ts|tsx|java|kt))$/.test(file));
  const reported = stable([...result.reportedFiles.changed, ...result.reportedFiles.created, ...result.reportedFiles.deleted].map(normalizeGitPath));
  const warnings = equalArrays(files, reported) ? [] : ["Worker-reported files do not match actual Git evidence."];
  const blockers = [
    ...forbiddenFiles.map((file) => `Forbidden path changed: ${file}`), ...unexpectedFiles.map((file) => `Out-of-scope path changed: ${file}`),
    ...(!order.scope.allowUntrackedFiles ? evidence.untrackedFiles.filter((file) => !isRuntimeFile(file)).map((file) => `Untracked file is not allowed: ${file}`) : []),
    ...(!order.scope.allowDependencyChanges ? dependencyChanges.map((file) => `Dependency change is not allowed: ${file}`) : []),
    ...(!order.scope.allowBuildConfigurationChanges ? buildConfigurationChanges.map((file) => `Build configuration change is not allowed: ${file}`) : []),
    ...(!order.scope.allowPublicApiChanges ? publicApiChangeCandidates.map((file) => `Public API change candidate requires authorization: ${file}`) : []),
  ];
  return { schemaVersion: "1.0", status: blockers.length > 0 ? "FAILED" : warnings.length > 0 ? "PASSED_WITH_WARNINGS" : "PASSED", allowedFiles: files.filter((file) => isAllowed(file, order.scope.allowedPaths)), forbiddenFiles, unexpectedFiles, dependencyChanges, buildConfigurationChanges, publicApiChangeCandidates, blockers, warnings };
}

function reviewImplementation(result: CodexImplementationResult, verification: VerificationExecutionSummary, diff: RepositoryDiffValidation, evidence: RepositoryEvidence): WorkerReviewResult {
  const blockers = stable([...result.review.blockers, ...verification.blockers, ...diff.blockers, ...(result.git.pushPerformed ? ["Worker performed an unauthorized Git push."] : []), ...(result.git.commitCreated || evidence.headCommit !== evidence.baselineCommit ? ["Worker changed repository HEAD without MCP commit authorization."] : [])]);
  const warnings = stable([...result.review.warnings, ...verification.warnings, ...diff.warnings]);
  return { schemaVersion: "1.0", status: blockers.length > 0 ? "FAILED" : warnings.length > 0 || result.review.knownLimitations.length > 0 ? "PASSED_WITH_LIMITATIONS" : "PASSED", findings: stable(result.review.findings), blockers, warnings, knownLimitations: stable(result.review.knownLimitations), unresolvedItems: stable(result.review.unresolvedItems) };
}

function evaluateWorkerAlpha(order: ImplementationWorkOrder, preflight: ImplementationPreflightResult, workerStatus: string, result: CodexImplementationResult, verification: VerificationExecutionSummary, diff: RepositoryDiffValidation, review: WorkerReviewResult, evidence: RepositoryEvidence): WorkerAlphaResult {
  const conditions: [string, boolean, string][] = [
    ["A1", result.rfcId === order.rfcId && result.workOrderId === order.id, "Current RFC and Work Order identity"], ["A2", preflight.status === "PASSED", "Preflight passed"],
    ["A3", workerStatus === "SUCCEEDED", "Worker execution succeeded"], ["A4", result.schemaVersion === order.resultContract.expectedSchemaVersion, "Worker result schema valid"],
    ["A5", diff.status !== "FAILED", "Actual diff within scope"], ["A6", requiredCategoryPassed(verification.build), "Required build passed"],
    ["A7", requiredCategoryPassed([...verification.targeted, ...verification.module]), "Required tests passed"], ["A8", requiredCategoryPassed(verification.regression), "Regression passed"],
    ["A9", requiredCategoryPassed(verification.smoke), "Smoke passed"], ["A10", review.blockers.length === 0, "Review blockers absent"],
    ["A11", review.unresolvedItems.length === 0, "Known limitations recorded and unresolved items absent"], ["A12", !result.git.pushPerformed && !result.git.commitCreated && evidence.headCommit === evidence.baselineCommit, "Git policy respected"],
  ];
  const gates = conditions.map(([id, passed, evidenceText]) => ({ id, status: passed ? "PASSED" as const : "FAILED" as const, evidence: [evidenceText] }));
  const blockers = gates.filter((gate) => gate.status === "FAILED").map((gate) => `${gate.id}: ${gate.evidence[0] ?? "failed"}`);
  const warnings = stable([...review.warnings, ...review.knownLimitations]);
  return { schemaVersion: "1.0", status: blockers.length > 0 ? "FAILED" : warnings.length > 0 || result.implementation.status === "PASSED_WITH_LIMITATIONS" ? "PASSED_WITH_LIMITATIONS" : "PASSED", gates, blockers, warnings };
}

function createHandoff(order: ImplementationWorkOrder, result: CodexImplementationResult, verification: VerificationExecutionSummary, diff: RepositoryDiffValidation, review: WorkerReviewResult, alpha: WorkerAlphaResult, evidence: RepositoryEvidence): RfcHandoff {
  const verificationStatus = (commands: CommandExecutionResult[]): VerificationStatus => commands.length === 0 ? "NOT_RUN" : commands.every((item) => !item.required || item.status === "PASSED") ? "PASSED" : "FAILED";
  return {
    schemaVersion: RFC_HANDOFF_SCHEMA_VERSION, rfcId: order.rfcId, worker: { type: "Codex CLI", executionMode: "controlled-local" },
    implementation: { status: alpha.status, summary: result.implementation.summary, implemented: stable(result.implementation.implemented), notImplemented: stable(result.implementation.notImplemented), changedFiles: stable(evidence.changedFiles), createdFiles: stable([...evidence.createdFiles, ...evidence.untrackedFiles].filter((file) => !isRuntimeFile(file))), deletedFiles: stable(evidence.deletedFiles) },
    verification: { build: verificationStatus(verification.build), tests: verificationStatus([...verification.targeted, ...verification.module]), regression: verificationStatus(verification.regression), smoke: verificationStatus(verification.smoke), scope: diff.status === "FAILED" ? "FAILED" : "PASSED", commandsExecuted: [...verification.targeted, ...verification.module, ...verification.build, ...verification.regression, ...verification.smoke].filter((item) => item.status !== "SKIPPED").map((item) => item.id), details: stable([...verification.warnings, ...verification.blockers]) },
    alphaReview: { status: alpha.status, findings: review.findings, blockers: review.blockers, warnings: review.warnings, knownLimitations: review.knownLimitations, unresolvedItems: review.unresolvedItems },
    architectureChanges: [], apiChanges: diff.publicApiChangeCandidates, adrCandidates: [], technicalDebt: [],
    git: { branch: evidence.branch, baseCommit: evidence.baselineCommit, resultingCommit: evidence.headCommit, commitStatus: "NOT_CREATED", pushStatus: "NOT_REQUESTED" },
    planningUpdate: { summary: [result.implementation.summary], releaseReadinessChanges: [], warnings: alpha.warnings },
  };
}

function normalizeScopePaths(values: readonly string[], label: string): string[] { return stable(values.map((value) => { const normalized = normalizeGitPath(value.trim()).replace(/\/$/, ""); if (!isSafeRelativePath(normalized)) throw new Error(`${label} must contain only safe repository-relative paths.`); return normalized; })); }
function isSafeRelativePath(value: string): boolean { return value !== "" && !isAbsolute(value) && value !== ".." && !value.startsWith("../") && !value.includes("/../") && !value.includes("\0"); }
function pathInside(root: string, value: string): boolean { const relation = relative(resolve(root), resolve(value)); return relation !== ".." && !relation.startsWith(`..${process.platform === "win32" ? "\\" : "/"}`) && !isAbsolute(relation); }
function pathMatches(file: string, scope: string): boolean { const prefix = scope.replace(/\/\*\*$/, "").replace(/\/$/, ""); return file === prefix || file.startsWith(`${prefix}/`); }
function isAllowed(file: string, scopes: readonly string[]): boolean { return scopes.some((scope) => pathMatches(file, scope)); }
function isRuntimeFile(file: string): boolean { return file === "project-state.json" || file.startsWith(".docpilot/"); }
function allEvidenceFiles(evidence: RepositoryEvidence): string[] { return stable([...evidence.changedFiles, ...evidence.createdFiles, ...evidence.deletedFiles, ...evidence.renamedFiles, ...(evidence.typeChangedFiles ?? []), ...evidence.untrackedFiles]); }
function stable(values: readonly string[]): string[] { return [...new Set(values)].sort((left, right) => left < right ? -1 : left > right ? 1 : 0); }
function equalArrays(left: readonly string[], right: readonly string[]): boolean { return left.length === right.length && left.every((value, index) => value === right[index]); }
function requiredCategoryPassed(commands: readonly CommandExecutionResult[]): boolean { return commands.some((item) => item.required) && commands.every((item) => !item.required || item.status === "PASSED"); }
async function executableAvailable(executable: string): Promise<boolean> {
  if (isAbsolute(executable)) return access(executable).then(() => true, () => false);
  const extensions = process.platform === "win32" ? (process.env.PATHEXT ?? ".EXE;.CMD;.BAT").split(";") : [""];
  const names = process.platform === "win32" && /.[a-z0-9]+$/i.test(executable) ? [executable] : extensions.map((extension) => `${executable}${extension.toLowerCase()}`);
  for (const directory of (process.env.PATH ?? process.env.Path ?? "").split(process.platform === "win32" ? ";" : ":")) for (const name of names) if (await access(resolve(directory, name)).then(() => true, () => false)) return true;
  return false;
}

async function resolveCodexInvocation(): Promise<{ executable: string; prefixArgs: string[] }> {
  const located = await findExecutableOnPath(process.platform === "win32" ? "codex.cmd" : "codex");
  if (located === undefined) return { executable: process.platform === "win32" ? "codex.cmd" : "codex", prefixArgs: [] };
  if (process.platform !== "win32") return { executable: located, prefixArgs: [] };
  const script = resolve(dirname(located), "node_modules", "@openai", "codex", "bin", "codex.js");
  if (await access(script).then(() => true, () => false)) return { executable: process.execPath, prefixArgs: [script] };
  return { executable: located, prefixArgs: [] };
}

async function findExecutableOnPath(name: string): Promise<string | undefined> {
  for (const directory of (process.env.PATH ?? process.env.Path ?? "").split(process.platform === "win32" ? ";" : ":")) {
    if (directory === "") continue;
    const candidate = resolve(directory, name);
    if (await access(candidate).then(() => true, () => false)) return candidate;
  }
  return undefined;
}
