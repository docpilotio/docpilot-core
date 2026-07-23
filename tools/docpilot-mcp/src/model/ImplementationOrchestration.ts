import type { AlphaCriterion } from "./RfcExecutionContext.js";
import type { RfcHandoff } from "./RfcHandoff.js";

export const IMPLEMENTATION_WORK_ORDER_SCHEMA_VERSION = "1.0";
export const IMPLEMENTATION_EXECUTION_SCHEMA_VERSION = "1.0";
export type WorkOrderMode = "ANALYSIS" | "IMPLEMENTATION";

export type OrchestrationRuntimeArtifacts = {
  rootPath: string; repositoryKey: string; lockDirectory: string;
  jsonlFile: string; resultFile: string; schemaFile: string;
  diagnosticsFile: string;
};

export type ControlledCommandCategory =
  | "TARGETED_TEST" | "MODULE_TEST" | "BUILD" | "REGRESSION_TEST" | "SMOKE";
export type ControlledCommand = {
  id: string; executable: string; args: string[]; workingDirectory: string;
  timeoutSeconds: number; required: boolean; category: ControlledCommandCategory;
};
export type ImplementationWorkOrder = {
  schemaVersion: string; id: string; rfcId: string;
  mode?: WorkOrderMode;
  repository: { rootPath: string; baselineBranch?: string; baselineCommit: string; workingDirectory: string };
  objective: { goal: string; approvedPlan: string[]; acceptanceCriteria: string[]; alphaCriteria: AlphaCriterion[] };
  scope: {
    allowedPaths: string[]; forbiddenPaths: string[]; allowUntrackedFiles: boolean;
    allowDependencyChanges: boolean; allowBuildConfigurationChanges: boolean; allowPublicApiChanges: boolean;
  };
  execution: {
    codexCommand: string; codexArguments: string[]; timeoutSeconds: number;
    maxOutputCharacters: number; environmentAllowlist: string[];
  };
  verification: {
    targetedCommands: ControlledCommand[]; moduleCommands: ControlledCommand[];
    buildCommands: ControlledCommand[]; regressionCommands: ControlledCommand[]; smokeCommands: ControlledCommand[];
  };
  gitPolicy: {
    requireCleanWorkingTree: boolean; allowCommit: boolean;
    requireUserApprovalForPush: boolean; allowMainBranchPush: boolean; allowForcePush: boolean;
  };
  resultContract: { resultFile: string; expectedSchemaVersion: string };
  runtime?: OrchestrationRuntimeArtifacts;
  warnings: string[];
};
export type PreflightCheck = { id: string; required: boolean; status: "PASSED" | "FAILED" | "WARNING"; details: string[] };
export type ImplementationPreflightResult = {
  schemaVersion: string; rfcId: string; workOrderId: string; status: "PASSED" | "FAILED";
  checks: PreflightCheck[]; blockers: string[]; warnings: string[];
};
export type ProcessExecutionResult = {
  status: "PASSED" | "FAILED" | "TIMED_OUT" | "CANCELLED";
  exitCode?: number; signal?: string; stdout: string; stderr: string; outputTruncated: boolean;
  timedOut: boolean; cancelled: boolean; terminationSteps: string[];
};
export type CodexWorkerExecution = {
  schemaVersion: string; rfcId: string; workOrderId: string;
  status: "SUCCEEDED" | "FAILED" | "BLOCKED" | "TIMED_OUT" | "CANCELLED";
  exitCode?: number; stdout: string; stderr: string; outputTruncated: boolean;
  resultFileFound: boolean; resultFile?: string; warnings: string[]; errors: string[];
  jsonlEventsSaved?: boolean; jsonlFile?: string; schemaFile?: string; diagnosticsFile?: string;
};
export type CommandExecutionResult = ControlledCommand & {
  status: "PASSED" | "FAILED" | "BLOCKED" | "TIMED_OUT" | "SKIPPED";
  exitCode?: number; stdout: string; stderr: string; outputTruncated: boolean;
};
export type VerificationExecutionSummary = {
  schemaVersion: string; status: "PASSED" | "FAILED" | "BLOCKED" | "PASSED_WITH_LIMITATIONS";
  targeted: CommandExecutionResult[]; module: CommandExecutionResult[]; build: CommandExecutionResult[];
  regression: CommandExecutionResult[]; smoke: CommandExecutionResult[]; blockers: string[]; warnings: string[];
};
export type RepositoryEvidence = {
  schemaVersion: string; branch: string; baselineCommit: string; headCommit: string;
  changedFiles: string[]; createdFiles: string[]; deletedFiles: string[]; renamedFiles: string[];
  stagedFiles: string[]; untrackedFiles: string[]; typeChangedFiles?: string[]; warnings: string[];
};
export type RepositoryDiffValidation = {
  schemaVersion: string; status: "PASSED" | "FAILED" | "PASSED_WITH_WARNINGS";
  allowedFiles: string[]; forbiddenFiles: string[]; unexpectedFiles: string[];
  dependencyChanges: string[]; buildConfigurationChanges: string[]; publicApiChangeCandidates: string[];
  blockers: string[]; warnings: string[];
};
export type WorkerReviewResult = {
  schemaVersion: string; status: "PASSED" | "FAILED" | "PASSED_WITH_LIMITATIONS" | "BLOCKED";
  findings: string[]; blockers: string[]; warnings: string[]; knownLimitations: string[]; unresolvedItems: string[];
};
export type WorkerAlphaResult = {
  schemaVersion: string; status: "FAILED" | "BLOCKED" | "PASSED_WITH_LIMITATIONS" | "PASSED";
  gates: { id: string; status: "PASSED" | "FAILED" | "BLOCKED"; evidence: string[] }[];
  blockers: string[]; warnings: string[];
};
export type CodexImplementationResult = {
  schemaVersion: string; rfcId: string; workOrderId: string;
  implementation: { status: "FAILED" | "BLOCKED" | "PASSED_WITH_LIMITATIONS" | "PASSED"; summary: string; implemented: string[]; notImplemented: string[] };
  reportedFiles: { changed: string[]; created: string[]; deleted: string[] };
  verification: { commandsAttempted: string[]; findings: string[] };
  review: { findings: string[]; blockers: string[]; warnings: string[]; knownLimitations: string[]; unresolvedItems: string[] };
  git: { commitCreated: boolean; commit?: string; pushPerformed: boolean };
};
export type ImplementationExecutionRecord = {
  schemaVersion: string; rfcId: string; workOrderId: string;
  status: "CREATED" | "PREFLIGHT_FAILED" | "READY" | "RUNNING" | "SUCCEEDED" | "FAILED" | "BLOCKED" | "TIMED_OUT" | "CANCELLED";
  baselineCommit: string; resultingHead?: string; preflight?: ImplementationPreflightResult;
  workerExecution?: CodexWorkerExecution; verification?: VerificationExecutionSummary;
  diffValidation?: RepositoryDiffValidation; review?: WorkerReviewResult; alpha?: WorkerAlphaResult;
  generatedHandoff?: RfcHandoff; commitSha?: string; warnings: string[]; errors: string[];
  repositoryBefore?: RepositoryEvidence;
  analysis?: {
    status: "PASSED" | "FAILED"; filesystemUnchanged: boolean;
    gitUnchanged: boolean; jsonlSaved: boolean; resultSaved: boolean;
    blockers: string[];
  };
  recoveryDiagnostics?: { status: "NONE" | "INTERRUPTED" | "RECOVERY_REQUIRED"; reason: string; lockState: "ABSENT" | "ACTIVE" | "STALE" | "RECOVERY_REQUIRED" };
};
