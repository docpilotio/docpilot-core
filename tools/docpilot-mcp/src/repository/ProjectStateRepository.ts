import {
  readFile,
  rename,
  writeFile,
} from "node:fs/promises";
import { dirname, resolve } from "node:path";

import {
  createDefaultReleaseReadiness,
  type ProjectStatus,
  type ReleaseReadiness,
  type ReleaseReadinessState,
} from "../model/ProjectStatus.js";
import type {
  RfcLifecycleEvent,
  RfcLifecycleEventType,
} from "../model/RfcLifecycleEvent.js";
import type {
  AlphaReviewStatus,
  CommitStatus,
  ImplementationStatus,
  PushStatus,
  RfcHandoff,
  VerificationStatus,
} from "../model/RfcHandoff.js";
import { RFC_HANDOFF_SCHEMA_VERSION } from "../model/RfcHandoff.js";
import {
  IMPLEMENTATION_EXECUTION_SCHEMA_VERSION,
  IMPLEMENTATION_WORK_ORDER_SCHEMA_VERSION,
  type ImplementationExecutionRecord,
  type ImplementationWorkOrder,
} from "../model/ImplementationOrchestration.js";

const RELEASE_READINESS_FIELDS = [
  "coreBuild",
  "coreTests",
  "cli",
  "incremental",
  "reviewWorkflow",
  "architectureSamplesValidation",
  "documentationSync",
  "releaseCandidate",
] as const;

export class ProjectStateRepository {
  public constructor(
    private readonly stateFilePath: string = resolve(
      process.cwd(),
      "project-state.json",
    ),
  ) {}

  public async load(): Promise<ProjectStatus> {
    const content = await readFile(this.stateFilePath, "utf-8");
    const parsedValue: unknown = JSON.parse(content);

    return this.validate(parsedValue);
  }

  public async save(status: ProjectStatus): Promise<void> {
    const validatedStatus = this.validate(status);

    const temporaryFilePath = resolve(
      dirname(this.stateFilePath),
      "project-state.tmp.json",
    );

    const content = `${JSON.stringify(validatedStatus, null, 2)}\n`;

    await writeFile(temporaryFilePath, content, "utf-8");
    await rename(temporaryFilePath, this.stateFilePath);
  }

  private validate(value: unknown): ProjectStatus {
    if (
      typeof value !== "object" ||
      value === null ||
      !("project" in value) ||
      !("phase" in value) ||
      !("currentRfc" in value) ||
      !("release" in value) ||
      !("completedRfcs" in value) ||
      typeof value.project !== "string" ||
      typeof value.phase !== "string" ||
      typeof value.currentRfc !== "string" ||
      typeof value.release !== "string" ||
      !Array.isArray(value.completedRfcs) ||
      !value.completedRfcs.every(
        (completedRfc) => typeof completedRfc === "string",
      )
    ) {
      throw new Error(
        "project-state.json does not contain a valid project status.",
      );
    }

    return {
      project: value.project,
      phase: value.phase,
      currentRfc: value.currentRfc,
      release: value.release,
      completedRfcs: [...value.completedRfcs],
      releaseReadiness: this.validateReleaseReadiness(
        "releaseReadiness" in value
          ? value.releaseReadiness
          : undefined,
      ),
      lifecycleHistory: this.validateLifecycleHistory(
        "lifecycleHistory" in value
          ? value.lifecycleHistory
          : undefined,
      ),
      ...("pendingRfcHandoff" in value && value.pendingRfcHandoff !== undefined
        ? { pendingRfcHandoff: this.validateRfcHandoff(value.pendingRfcHandoff) }
        : {}),
      ...("pendingImplementationWorkOrder" in value && value.pendingImplementationWorkOrder !== undefined
        ? { pendingImplementationWorkOrder: this.validateImplementationWorkOrder(value.pendingImplementationWorkOrder) }
        : {}),
      ...("implementationExecutionRecord" in value && value.implementationExecutionRecord !== undefined
        ? { implementationExecutionRecord: this.validateExecutionRecord(value.implementationExecutionRecord) }
        : {}),
    };
  }

  private validateImplementationWorkOrder(value: unknown): ImplementationWorkOrder {
    const order = this.requireObject(value, "pendingImplementationWorkOrder");
    const repository = this.requireObject(order.repository, "pendingImplementationWorkOrder.repository");
    const objective = this.requireObject(order.objective, "pendingImplementationWorkOrder.objective");
    const scope = this.requireObject(order.scope, "pendingImplementationWorkOrder.scope");
    const execution = this.requireObject(order.execution, "pendingImplementationWorkOrder.execution");
    const verification = this.requireObject(order.verification, "pendingImplementationWorkOrder.verification");
    const gitPolicy = this.requireObject(order.gitPolicy, "pendingImplementationWorkOrder.gitPolicy");
    const resultContract = this.requireObject(order.resultContract, "pendingImplementationWorkOrder.resultContract");
    if (order.schemaVersion !== IMPLEMENTATION_WORK_ORDER_SCHEMA_VERSION) throw new Error(`project-state.json contains an unsupported Work Order schemaVersion: ${String(order.schemaVersion)}.`);
    if (typeof order.id !== "string" || typeof order.rfcId !== "string" || !/^RFC-[0-9]{4}$/.test(order.rfcId)) throw new Error("project-state.json contains an invalid Pending Work Order identity.");
    const commands = (input: unknown, field: string) => {
      if (!Array.isArray(input)) throw new Error(`project-state.json contains an invalid ${field}.`);
      return input.map((item) => {
        const command = this.requireObject(item, field);
        if (typeof command.id !== "string" || typeof command.executable !== "string" || !Array.isArray(command.args) || !command.args.every((arg) => typeof arg === "string") || typeof command.workingDirectory !== "string" || typeof command.timeoutSeconds !== "number" || typeof command.required !== "boolean" || !["TARGETED_TEST", "MODULE_TEST", "BUILD", "REGRESSION_TEST", "SMOKE"].includes(String(command.category))) throw new Error(`project-state.json contains an invalid ${field} command.`);
        return command as ImplementationWorkOrder["verification"]["targetedCommands"][number];
      });
    };
    if (!Array.isArray(objective.approvedPlan) || !Array.isArray(objective.acceptanceCriteria) || !Array.isArray(objective.alphaCriteria) || !Array.isArray(scope.allowedPaths) || !Array.isArray(scope.forbiddenPaths) || !Array.isArray(execution.codexArguments) || !Array.isArray(execution.environmentAllowlist) || !Array.isArray(order.warnings)) throw new Error("project-state.json contains an invalid Pending Work Order array.");
    return {
      schemaVersion: IMPLEMENTATION_WORK_ORDER_SCHEMA_VERSION,
      id: order.id,
      rfcId: order.rfcId,
      repository: repository as ImplementationWorkOrder["repository"],
      objective: objective as ImplementationWorkOrder["objective"],
      scope: scope as ImplementationWorkOrder["scope"],
      execution: execution as ImplementationWorkOrder["execution"],
      verification: {
        targetedCommands: commands(verification.targetedCommands, "targetedCommands"),
        moduleCommands: commands(verification.moduleCommands, "moduleCommands"),
        buildCommands: commands(verification.buildCommands, "buildCommands"),
        regressionCommands: commands(verification.regressionCommands, "regressionCommands"),
        smokeCommands: commands(verification.smokeCommands, "smokeCommands"),
      },
      gitPolicy: gitPolicy as ImplementationWorkOrder["gitPolicy"],
      resultContract: resultContract as ImplementationWorkOrder["resultContract"],
      warnings: [...order.warnings] as string[],
    };
  }

  private validateExecutionRecord(value: unknown): ImplementationExecutionRecord {
    const record = this.requireObject(value, "implementationExecutionRecord");
    if (record.schemaVersion !== IMPLEMENTATION_EXECUTION_SCHEMA_VERSION || typeof record.rfcId !== "string" || typeof record.workOrderId !== "string" || typeof record.baselineCommit !== "string" || !["CREATED", "PREFLIGHT_FAILED", "READY", "RUNNING", "SUCCEEDED", "FAILED", "BLOCKED", "TIMED_OUT", "CANCELLED"].includes(String(record.status)) || !Array.isArray(record.warnings) || !Array.isArray(record.errors)) throw new Error("project-state.json contains an invalid Implementation Execution Record.");
    return record as unknown as ImplementationExecutionRecord;
  }

  private validateRfcHandoff(value: unknown): RfcHandoff {
    const handoff = this.requireObject(value, "pendingRfcHandoff");
    this.rejectUnknown(handoff, [
      "schemaVersion", "rfcId", "worker", "implementation", "verification",
      "alphaReview", "architectureChanges", "apiChanges", "adrCandidates",
      "technicalDebt", "git", "planningUpdate",
    ], "pendingRfcHandoff");
    const implementation = this.requireObject(handoff.implementation, "pendingRfcHandoff.implementation");
    const verification = this.requireObject(handoff.verification, "pendingRfcHandoff.verification");
    const alphaReview = this.requireObject(handoff.alphaReview, "pendingRfcHandoff.alphaReview");
    const git = this.requireObject(handoff.git, "pendingRfcHandoff.git");
    const planningUpdate = this.requireObject(handoff.planningUpdate, "pendingRfcHandoff.planningUpdate");
    this.rejectUnknown(implementation, ["status", "summary", "implemented", "notImplemented", "changedFiles", "createdFiles", "deletedFiles"], "pendingRfcHandoff.implementation");
    this.rejectUnknown(verification, ["build", "tests", "regression", "smoke", "scope", "commandsExecuted", "details"], "pendingRfcHandoff.verification");
    this.rejectUnknown(alphaReview, ["status", "findings", "blockers", "warnings", "knownLimitations", "unresolvedItems"], "pendingRfcHandoff.alphaReview");
    this.rejectUnknown(git, ["branch", "baseCommit", "resultingCommit", "commitStatus", "pushStatus"], "pendingRfcHandoff.git");
    this.rejectUnknown(planningUpdate, ["summary", "releaseReadinessChanges", "warnings"], "pendingRfcHandoff.planningUpdate");
    const worker = handoff.worker === undefined ? undefined : this.requireObject(handoff.worker, "pendingRfcHandoff.worker");
    if (worker !== undefined) this.rejectUnknown(worker, ["type", "executionMode", "version"], "pendingRfcHandoff.worker");

    const schemaVersion = this.requireString(handoff.schemaVersion, "schemaVersion");
    const rfcId = this.requireString(handoff.rfcId, "rfcId");
    if (schemaVersion !== RFC_HANDOFF_SCHEMA_VERSION) throw new Error(`project-state.json contains an unsupported pendingRfcHandoff schemaVersion: ${schemaVersion}.`);
    if (!/^RFC-[0-9]{4}$/.test(rfcId)) throw new Error("project-state.json contains an invalid pendingRfcHandoff rfcId.");

    return {
      schemaVersion,
      rfcId,
      ...(worker === undefined ? {} : { worker: {
        type: this.requireString(worker.type, "worker.type"),
        ...this.optionalString(worker.executionMode, "worker.executionMode"),
        ...this.optionalString(worker.version, "worker.version"),
      } }),
      implementation: {
        status: this.requireEnum(implementation.status, ["NOT_STARTED", "IN_PROGRESS", "BLOCKED", "FAILED", "PASSED_WITH_LIMITATIONS", "PASSED"] as const, "implementation.status") as ImplementationStatus,
        summary: this.requireString(implementation.summary, "implementation.summary"),
        implemented: this.requireStringArray(implementation.implemented, "implementation.implemented"),
        notImplemented: this.requireStringArray(implementation.notImplemented, "implementation.notImplemented"),
        changedFiles: this.requireStringArray(implementation.changedFiles, "implementation.changedFiles"),
        createdFiles: this.requireStringArray(implementation.createdFiles, "implementation.createdFiles"),
        deletedFiles: this.requireStringArray(implementation.deletedFiles, "implementation.deletedFiles"),
      },
      verification: {
        build: this.verificationStatus(verification.build, "verification.build"),
        tests: this.verificationStatus(verification.tests, "verification.tests"),
        regression: this.verificationStatus(verification.regression, "verification.regression"),
        smoke: this.verificationStatus(verification.smoke, "verification.smoke"),
        scope: this.verificationStatus(verification.scope, "verification.scope"),
        commandsExecuted: this.requireStringArray(verification.commandsExecuted, "verification.commandsExecuted"),
        details: this.requireStringArray(verification.details, "verification.details"),
      },
      alphaReview: {
        status: this.requireEnum(alphaReview.status, ["NOT_STARTED", "BLOCKED", "FAILED", "PASSED_WITH_LIMITATIONS", "PASSED"] as const, "alphaReview.status") as AlphaReviewStatus,
        findings: this.requireStringArray(alphaReview.findings, "alphaReview.findings"),
        blockers: this.requireStringArray(alphaReview.blockers, "alphaReview.blockers"),
        warnings: this.requireStringArray(alphaReview.warnings, "alphaReview.warnings"),
        knownLimitations: this.requireStringArray(alphaReview.knownLimitations, "alphaReview.knownLimitations"),
        unresolvedItems: this.requireStringArray(alphaReview.unresolvedItems, "alphaReview.unresolvedItems"),
      },
      architectureChanges: this.requireStringArray(handoff.architectureChanges, "architectureChanges"),
      apiChanges: this.requireStringArray(handoff.apiChanges, "apiChanges"),
      adrCandidates: this.requireStringArray(handoff.adrCandidates, "adrCandidates"),
      technicalDebt: this.requireStringArray(handoff.technicalDebt, "technicalDebt"),
      git: {
        ...this.optionalString(git.branch, "git.branch"),
        ...this.optionalString(git.baseCommit, "git.baseCommit"),
        ...this.optionalString(git.resultingCommit, "git.resultingCommit"),
        commitStatus: this.requireEnum(git.commitStatus, ["NOT_CREATED", "CREATED", "UNKNOWN"] as const, "git.commitStatus") as CommitStatus,
        pushStatus: this.requireEnum(git.pushStatus, ["NOT_REQUESTED", "PENDING_APPROVAL", "PUSHED", "FAILED", "UNKNOWN"] as const, "git.pushStatus") as PushStatus,
      },
      planningUpdate: {
        summary: this.requireStringArray(planningUpdate.summary, "planningUpdate.summary"),
        releaseReadinessChanges: this.requireStringArray(planningUpdate.releaseReadinessChanges, "planningUpdate.releaseReadinessChanges"),
        warnings: this.requireStringArray(planningUpdate.warnings, "planningUpdate.warnings"),
      },
    };
  }

  private verificationStatus(value: unknown, field: string): VerificationStatus {
    return this.requireEnum(value, ["NOT_RUN", "PASSED", "FAILED", "BLOCKED"] as const, field) as VerificationStatus;
  }

  private requireObject(value: unknown, field: string): Record<string, unknown> {
    if (typeof value !== "object" || value === null || Array.isArray(value)) throw new Error(`project-state.json contains an invalid ${field}.`);
    return value as Record<string, unknown>;
  }

  private rejectUnknown(value: Record<string, unknown>, fields: readonly string[], name: string): void {
    const unknown = Object.keys(value).find((field) => !fields.includes(field));
    if (unknown !== undefined) throw new Error(`project-state.json contains an unknown ${name} field: ${unknown}.`);
  }

  private requireString(value: unknown, field: string): string {
    if (typeof value !== "string" || value.length === 0) throw new Error(`project-state.json contains an invalid ${field}.`);
    return value;
  }

  private optionalString(value: unknown, field: string): Record<string, string> {
    if (value === undefined) return {};
    const key = field.split(".").at(-1)!;
    return { [key]: this.requireString(value, field) };
  }

  private requireStringArray(value: unknown, field: string): string[] {
    if (!Array.isArray(value) || !value.every((item) => typeof item === "string")) throw new Error(`project-state.json contains an invalid ${field}.`);
    return [...value];
  }

  private requireEnum<T extends string>(value: unknown, allowed: readonly T[], field: string): T {
    if (typeof value !== "string" || !allowed.includes(value as T)) throw new Error(`project-state.json contains an invalid ${field}.`);
    return value as T;
  }

  private validateLifecycleHistory(value: unknown): RfcLifecycleEvent[] {
    if (value === undefined) {
      return [];
    }

    if (!Array.isArray(value)) {
      throw new Error(
        "project-state.json contains an invalid lifecycleHistory array.",
      );
    }

    const events = value.map((event, index) =>
      this.validateLifecycleEvent(event, index),
    );

    for (const [index, event] of events.entries()) {
      const expectedId = `rfc-event-${(index + 1).toString().padStart(6, "0")}`;

      if (event.id !== expectedId) {
        throw new Error(
          `project-state.json contains an invalid lifecycleHistory event ID at index ${index}; expected ${expectedId}.`,
        );
      }
    }

    return events;
  }

  private validateLifecycleEvent(
    value: unknown,
    index: number,
  ): RfcLifecycleEvent {
    if (typeof value !== "object" || value === null || Array.isArray(value)) {
      throw new Error(
        `project-state.json contains an invalid lifecycleHistory event at index ${index}.`,
      );
    }

    const event = value as Record<string, unknown>;
    const allowedFields = [
      "id",
      "type",
      "rfc",
      "fromRfc",
      "phase",
      "release",
      "timestamp",
    ];
    const hasUnknownField = Object.keys(event).some(
      (field) => !allowedFields.includes(field),
    );
    const timestamp = event.timestamp;

    if (
      hasUnknownField ||
      typeof event.id !== "string" ||
      event.id.length === 0 ||
      !this.isLifecycleEventType(event.type) ||
      typeof event.rfc !== "string" ||
      !/^RFC-[0-9]{4}$/.test(event.rfc) ||
      (event.fromRfc !== undefined &&
        (typeof event.fromRfc !== "string" ||
          !/^RFC-[0-9]{4}$/.test(event.fromRfc))) ||
      (event.type === "rollbackCompleted" &&
        (typeof event.fromRfc !== "string" || event.fromRfc === event.rfc)) ||
      (event.type !== "rollbackCompleted" && event.fromRfc !== undefined) ||
      typeof event.phase !== "string" ||
      typeof event.release !== "string" ||
      typeof timestamp !== "string" ||
      !this.isIsoTimestamp(timestamp)
    ) {
      throw new Error(
        `project-state.json contains an invalid lifecycleHistory event at index ${index}.`,
      );
    }

    return {
      id: event.id,
      type: event.type,
      rfc: event.rfc,
      ...(event.fromRfc !== undefined ? { fromRfc: event.fromRfc } : {}),
      phase: event.phase,
      release: event.release,
      timestamp,
    };
  }

  private isLifecycleEventType(value: unknown): value is RfcLifecycleEventType {
    return value === "started" ||
      value === "completed" ||
      value === "planningSynced" ||
      value === "rollbackCompleted";
  }

  private isIsoTimestamp(value: string): boolean {
    const parsed = new Date(value);

    return !Number.isNaN(parsed.getTime()) && parsed.toISOString() === value;
  }

  private validateReleaseReadiness(value: unknown): ReleaseReadiness {
    if (value === undefined) {
      return createDefaultReleaseReadiness();
    }

    if (typeof value !== "object" || value === null || Array.isArray(value)) {
      throw new Error(
        "project-state.json contains an invalid releaseReadiness object.",
      );
    }

    const persistedReadiness = value as Record<string, unknown>;
    const unknownField = Object.keys(persistedReadiness).find(
      (field) =>
        !RELEASE_READINESS_FIELDS.includes(
          field as (typeof RELEASE_READINESS_FIELDS)[number],
        ),
    );

    if (unknownField !== undefined) {
      throw new Error(
        `project-state.json contains an unknown releaseReadiness field: ${unknownField}.`,
      );
    }

    const defaults = createDefaultReleaseReadiness();
    const readiness = {} as ReleaseReadiness;

    for (const field of RELEASE_READINESS_FIELDS) {
      const fieldValue = field in persistedReadiness
        ? persistedReadiness[field]
        : defaults[field];

      if (!this.isReleaseReadinessState(fieldValue)) {
        throw new Error(
          `project-state.json contains an invalid releaseReadiness value for ${field}.`,
        );
      }

      readiness[field] = fieldValue;
    }

    return readiness;
  }

  private isReleaseReadinessState(
    value: unknown,
  ): value is ReleaseReadinessState {
    return value === "pending" || value === "passed" || value === "failed";
  }
}
