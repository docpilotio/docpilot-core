import type {
  ProjectStatus,
  ReleaseReadiness,
  ReleaseReadinessState,
} from "../model/ProjectStatus.js";
import { createDefaultReleaseReadiness } from "../model/ProjectStatus.js";
import type { RfcLifecycleGuidance } from "../model/RfcLifecycleGuidance.js";
import type {
  PlanningSynchronizationStatus,
  PlanningSynchronizationState,
} from "../model/PlanningSynchronizationStatus.js";
import type { RfcRollbackPreview } from "../model/RfcRollbackPreview.js";
import {
  RFC_EXECUTION_CONTEXT_SCHEMA_VERSION,
  type AlphaCriterion,
  type RfcExecutionContext,
} from "../model/RfcExecutionContext.js";
import {
  RFC_HANDOFF_SCHEMA_VERSION,
  type RfcHandoff,
} from "../model/RfcHandoff.js";
import type {
  RfcLifecycleEvent,
  RfcLifecycleEventType,
} from "../model/RfcLifecycleEvent.js";
import { ProjectStateRepository } from "../repository/ProjectStateRepository.js";
import {
  COMPLETION_READINESS_SCHEMA_VERSION,
  type CompletionCheck,
  type CompletionReadiness,
} from "../model/CompletionReadiness.js";
import {
  PROJECT_CONTROL_CONTEXT_SCHEMA_VERSION,
  type DocPilotProjectControlContext,
} from "../model/DocPilotProjectControlContext.js";
import {
  createProjectControlCapabilityManifest,
  type ProjectControlCapabilityManifest,
} from "../model/ProjectControlCapabilityManifest.js";

export type CurrentRfcStatus = {
  [key: string]: unknown;

  currentRfc: string;
  phase: string;
  release: string;
};

export type CompleteCurrentRfcResult = {
  [key: string]: unknown;

  completedRfc: string;
  currentRfc: string;
  phase: string;
  release: string;
  completedRfcs: string[];
};

export type CompletedRfcsStatus = {
  [key: string]: unknown;

  project: string;
  currentRfc: string;
  completedRfcs: string[];
  completedCount: number;
};

export type MainPlanningSyncResult = {
  [key: string]: unknown;

  project: string;
  phase: string;
  currentRfc: string;
  release: string;
  completedRfcs: string[];
  completedCount: number;
  markdown: string;
  lifecycleGuidance: RfcLifecycleGuidance;
  lifecycleHistory: readonly RfcLifecycleEvent[];
  rollbackPreview: RfcRollbackPreview;
  planningSynchronization: PlanningSynchronizationStatus;
};

export type UpdateProjectStatusRequest = {
  phase?: string;
  release?: string;
  currentRfc?: string;
};

export type StartNextRfcRequest = {
  nextRfc: string;
  phase?: string;
  release?: string;
};

export type PendingRfcHandoffResult = {
  found: boolean;
  rfcId: string;
  handoff?: RfcHandoff;
  markdown?: string;
};

export type SubmitRfcHandoffResult = {
  handoff: RfcHandoff;
  markdown: string;
};

const START_NEXT_RFC_FIELDS = ["nextRfc", "phase", "release"] as const;

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

type ActiveRfcContext = {
  rfc: string;
  phase: string;
  release: string;
};

type RfcTransition = {
  type: "started" | "rollbackCompleted";
  from: ActiveRfcContext;
  to: ActiveRfcContext;
};

type LifecycleAnalysis = {
  active?: ActiveRfcContext;
  latestTransition?: RfcTransition;
};

const PLANNING_RELEVANT_EVENT_TYPES: readonly RfcLifecycleEventType[] = [
  "started",
  "completed",
  "rollbackCompleted",
];

const DEFAULT_ALPHA_CRITERIA: readonly AlphaCriterion[] = [
  { id: "A1", type: "BUILD", required: true, description: "The complete build passes." },
  { id: "A2", type: "TEST", required: true, description: "Relevant automated tests pass." },
  { id: "A3", type: "REGRESSION", required: true, description: "The complete regression suite passes." },
  { id: "A4", type: "SMOKE", required: true, description: "Core smoke scenarios pass." },
  { id: "A5", type: "SCOPE", required: true, description: "No changes exist outside the authorized scope." },
  { id: "A6", type: "REVIEW", required: true, description: "Diff review is complete and known limitations are recorded." },
];

export class ProjectStatusService {
  public constructor(
    private readonly repository: ProjectStateRepository,
  ) {}

  public async getProjectStatus(): Promise<ProjectStatus> {
    return this.repository.load();
  }

  public async loadRfcContext(
    rfcId?: string,
    providedStatus?: ProjectStatus,
  ): Promise<RfcExecutionContext> {
    if (rfcId !== undefined && !this.isValidRfcIdentifier(rfcId)) {
      throw new Error("rfcId must use the exact format RFC-0000.");
    }

    const status = providedStatus ?? await this.repository.load();
    if (!this.isValidRfcIdentifier(status.currentRfc)) {
      throw new Error("The current RFC must use the exact format RFC-0000.");
    }
    if (rfcId !== undefined && rfcId !== status.currentRfc) {
      throw new Error(`RFC Context is available only for the current RFC ${status.currentRfc}.`);
    }

    return {
      schemaVersion: RFC_EXECUTION_CONTEXT_SCHEMA_VERSION,
      project: { name: status.project, phase: status.phase, release: status.release },
      rfc: { id: status.currentRfc },
      completedRfcs: [...status.completedRfcs],
      operatingRules: [
        "Preserve the existing architecture and public contracts.",
        "Tools call Services; persistence belongs to Repositories.",
        "Keep command side effects separate from read-only queries.",
        "Use deterministic output and evidence-first verification.",
        "Do not automatically complete or advance the current RFC.",
      ],
      scope: { inScope: [], outOfScope: [] },
      acceptanceCriteria: [],
      alphaCriteria: DEFAULT_ALPHA_CRITERIA.map((criterion) => ({ ...criterion })),
      changePolicy: {
        allowedPaths: [],
        forbiddenPaths: [],
        refactoringPolicy: "Avoid unrelated refactoring.",
        publicApiPolicy: "Preserve existing public names and behavior unless explicitly changed.",
      },
      verification: {
        buildCommands: ["npm.cmd run build"],
        testCommands: ["npm.cmd test"],
        smokeCommands: [],
      },
      lifecycleGuidance: await this.getRfcLifecycleGuidance(status),
      planningSynchronization: await this.getPlanningSynchronizationStatus(status),
      releaseReadiness: { ...status.releaseReadiness },
      warnings: [
        "RFC title, goal, detailed scope, acceptance criteria, repository baseline, and next RFC are not persisted in Project State.",
      ],
    };
  }

  public getProjectControlCapabilityManifest(): ProjectControlCapabilityManifest {
    return createProjectControlCapabilityManifest();
  }

  public async evaluateRfcCompletionReadiness(
    rfcId?: string,
    providedStatus?: ProjectStatus,
  ): Promise<CompletionReadiness> {
    if (rfcId !== undefined && !this.isValidRfcIdentifier(rfcId)) {
      throw new Error("rfcId must use the exact format RFC-0000.");
    }
    const status = providedStatus ?? await this.repository.load();
    if (!this.isValidRfcIdentifier(status.currentRfc)) {
      throw new Error("The current RFC must use the exact format RFC-0000.");
    }
    if (rfcId !== undefined && rfcId !== status.currentRfc) {
      throw new Error(`Completion Readiness is available only for the current RFC ${status.currentRfc}.`);
    }

    const handoff = status.pendingRfcHandoff;
    const checks: CompletionCheck[] = [];
    const blockers: string[] = [];
    const warnings: string[] = [];
    const add = (
      id: string,
      label: string,
      required: boolean,
      checkStatus: CompletionCheck["status"],
      evidence: string[] = [],
      checkWarnings: string[] = [],
    ) => checks.push({ id, label, required, status: checkStatus, evidence, warnings: checkWarnings });

    add("CURRENT_RFC_MATCH", "Current RFC identity", true, "SATISFIED", [status.currentRfc]);
    add("HANDOFF_PRESENT", "Pending Handoff is present", true,
      handoff === undefined ? "NOT_SATISFIED" : "SATISFIED",
      handoff === undefined ? [] : [handoff.rfcId]);

    if (handoff === undefined) {
      for (const [id, label, required] of [
        ["HANDOFF_SCHEMA_VALID", "Handoff schema is supported", true],
        ["HANDOFF_RFC_MATCH", "Handoff RFC matches current RFC", true],
        ["IMPLEMENTATION_STATUS", "Implementation passed", true],
        ["BUILD_VERIFICATION", "Build verification passed", true],
        ["TEST_VERIFICATION", "Test verification passed", true],
        ["REGRESSION_VERIFICATION", "Regression verification passed", true],
        ["SMOKE_VERIFICATION", "Smoke verification passed", true],
        ["SCOPE_VERIFICATION", "Scope verification passed", true],
        ["ALPHA_REVIEW", "Alpha review passed", true],
        ["KNOWN_LIMITATIONS_RECORDED", "Known limitations reviewed", false],
        ["GIT_PUSH_POLICY", "Git push policy", false],
      ] as const) add(id, label, required, "NOT_AVAILABLE");
      return {
        schemaVersion: COMPLETION_READINESS_SCHEMA_VERSION,
        rfcId: status.currentRfc,
        status: "NOT_READY",
        checks,
        blockers: [],
        warnings: [],
      };
    }

    const schemaValid = handoff.schemaVersion === RFC_HANDOFF_SCHEMA_VERSION;
    add("HANDOFF_SCHEMA_VALID", "Handoff schema is supported", true,
      schemaValid ? "SATISFIED" : "BLOCKED", [handoff.schemaVersion]);
    if (!schemaValid) blockers.push(`Unsupported Handoff schemaVersion: ${handoff.schemaVersion}.`);
    const rfcMatches = handoff.rfcId === status.currentRfc;
    add("HANDOFF_RFC_MATCH", "Handoff RFC matches current RFC", true,
      rfcMatches ? "SATISFIED" : "BLOCKED", [handoff.rfcId, status.currentRfc]);
    if (!rfcMatches) blockers.push(`Handoff RFC ${handoff.rfcId} does not match current RFC ${status.currentRfc}.`);

    const implementation = handoff.implementation.status;
    const implementationPassing = implementation === "PASSED" || implementation === "PASSED_WITH_LIMITATIONS";
    const implementationBlocked = implementation === "FAILED" || implementation === "BLOCKED";
    add("IMPLEMENTATION_STATUS", "Implementation passed", true,
      implementationBlocked ? "BLOCKED" : implementationPassing ? (implementation === "PASSED" ? "SATISFIED" : "WARNING") : "NOT_SATISFIED",
      [implementation]);
    if (implementationBlocked) blockers.push(`Implementation status is ${implementation}.`);
    if (implementation === "PASSED_WITH_LIMITATIONS") warnings.push("Implementation passed with limitations.");

    const verificationEntries = [
      ["BUILD_VERIFICATION", "Build verification passed", "build", handoff.verification.build],
      ["TEST_VERIFICATION", "Test verification passed", "tests", handoff.verification.tests],
      ["REGRESSION_VERIFICATION", "Regression verification passed", "regression", handoff.verification.regression],
      ["SMOKE_VERIFICATION", "Smoke verification passed", "smoke", handoff.verification.smoke],
      ["SCOPE_VERIFICATION", "Scope verification passed", "scope", handoff.verification.scope],
    ] as const;
    let missingEvidence = false;
    for (const [id, label, field, value] of verificationEntries) {
      const failed = value === "FAILED" || value === "BLOCKED";
      add(
        id,
        label,
        true,
        failed ? "BLOCKED" : value === "PASSED" ? "SATISFIED" : "NOT_SATISFIED",
        [value],
        field === "scope"
          ? ["Scope verification relies on submitted Handoff evidence because allowedPaths are unavailable."]
          : [],
      );
      if (failed) blockers.push(`${field} verification is ${value}.`);
    }
    if (handoff.verification.commandsExecuted.length === 0 || handoff.verification.details.length === 0) {
      missingEvidence = true;
      warnings.push("Verification claims do not include both executed commands and details.");
    }

    const alpha = handoff.alphaReview.status;
    const alphaPassing = alpha === "PASSED" || alpha === "PASSED_WITH_LIMITATIONS";
    const alphaBlocked = alpha === "FAILED" || alpha === "BLOCKED" || handoff.alphaReview.blockers.length > 0 || handoff.alphaReview.unresolvedItems.length > 0;
    add("ALPHA_REVIEW", "Alpha review passed", true,
      alphaBlocked ? "BLOCKED" : alphaPassing ? (alpha === "PASSED" ? "SATISFIED" : "WARNING") : "NOT_SATISFIED",
      [alpha, ...handoff.alphaReview.findings], [...handoff.alphaReview.warnings]);
    if (alpha === "FAILED" || alpha === "BLOCKED") blockers.push(`Alpha review status is ${alpha}.`);
    blockers.push(...handoff.alphaReview.blockers, ...handoff.alphaReview.unresolvedItems);
    if (alpha === "PASSED_WITH_LIMITATIONS") warnings.push("Alpha review passed with limitations.");

    const limitations = handoff.alphaReview.knownLimitations;
    add("KNOWN_LIMITATIONS_RECORDED", "Known limitations reviewed", false,
      limitations.length === 0 ? "SATISFIED" : "WARNING", [...limitations]);
    if (limitations.length > 0) warnings.push(...limitations);
    warnings.push(...handoff.alphaReview.warnings, ...handoff.planningUpdate.warnings);

    const pushed = handoff.git.pushStatus === "PUSHED";
    add("GIT_PUSH_POLICY", "Git push policy", false, pushed ? "WARNING" : "SATISFIED",
      [handoff.git.pushStatus], pushed ? ["Push was reported although MCP has no push approval capability."] : []);
    if (pushed) warnings.push("Push was reported although MCP has no push approval capability.");

    const hasPendingRequired = checks.some(({ required, status: value }) =>
      required && (value === "NOT_AVAILABLE" || value === "NOT_SATISFIED"));
    const readinessStatus: CompletionReadiness["status"] = blockers.length > 0
      ? "BLOCKED"
      : hasPendingRequired
        ? "NOT_READY"
        : warnings.length > 0 || missingEvidence
          ? "READY_WITH_WARNINGS"
          : "READY";
    return {
      schemaVersion: COMPLETION_READINESS_SCHEMA_VERSION,
      rfcId: status.currentRfc,
      status: readinessStatus,
      checks,
      blockers: [...blockers],
      warnings: [...new Set(warnings)],
    };
  }

  public async getDocPilotProjectControlContext(): Promise<DocPilotProjectControlContext> {
    const status = await this.repository.load();
    const rfcExecution = await this.loadRfcContext(undefined, status);
    const completionReadiness = await this.evaluateRfcCompletionReadiness(undefined, status);
    const planningSynchronization = await this.getPlanningSynchronizationStatus(status);
    const lifecycleGuidance = await this.getRfcLifecycleGuidance(status);
    const handoff = status.pendingRfcHandoff;
    return {
      schemaVersion: PROJECT_CONTROL_CONTEXT_SCHEMA_VERSION,
      project: { name: status.project, phase: status.phase, release: status.release },
      lifecycle: {
        currentRfc: status.currentRfc,
        completedRfcs: [...status.completedRfcs],
        status: lifecycleGuidance.state,
      },
      rfcExecution,
      handoff: {
        pending: handoff !== undefined,
        rfcId: handoff?.rfcId ?? status.currentRfc,
        ...(handoff === undefined ? {} : {
          summary: handoff.implementation.summary,
          implementationStatus: handoff.implementation.status,
          alphaStatus: handoff.alphaReview.status,
        }),
      },
      completionReadiness,
      capabilities: this.getProjectControlCapabilityManifest(),
      policies: {
        lifecycleAutoAdvance: false,
        automaticCommit: false,
        automaticPush: false,
        automaticMerge: false,
        pushRequiresUserApproval: true,
      },
      planningSynchronization,
      releaseReadiness: { ...status.releaseReadiness },
      warnings: [
        ...rfcExecution.warnings,
        "Build, test, diff, and command execution evidence is structurally validated but not independently executed by MCP.",
      ],
    };
  }

  public async submitRfcHandoff(handoff: RfcHandoff): Promise<SubmitRfcHandoffResult> {
    const status = await this.repository.load();
    this.validateHandoffBusinessRules(handoff, status.currentRfc);
    if (status.pendingRfcHandoff !== undefined) {
      throw new Error(`A Pending Handoff already exists for ${status.pendingRfcHandoff.rfcId}.`);
    }

    const normalized = this.normalizeHandoff(handoff);
    await this.repository.save({ ...status, pendingRfcHandoff: normalized });
    return { handoff: normalized, markdown: this.renderRfcHandoff(normalized) };
  }

  public async getPendingRfcHandoff(): Promise<PendingRfcHandoffResult> {
    const status = await this.repository.load();
    const handoff = status.pendingRfcHandoff;
    if (handoff === undefined) return { found: false, rfcId: status.currentRfc };
    if (handoff.rfcId !== status.currentRfc) {
      throw new Error(`Pending Handoff RFC ${handoff.rfcId} does not match current RFC ${status.currentRfc}.`);
    }
    return {
      found: true,
      rfcId: status.currentRfc,
      handoff,
      markdown: this.renderRfcHandoff(handoff),
    };
  }

  public renderRfcHandoff(handoff: RfcHandoff): string {
    const list = (items: readonly string[]) => items.length === 0 ? ["- None"] : items.map((item) => `- ${item}`);
    return [
      `# RFC Handoff: ${handoff.rfcId}`,
      "", "## RFC", "", `- ID: ${handoff.rfcId}`, `- Schema Version: ${handoff.schemaVersion}`,
      "", "## Implementation Summary", "", handoff.implementation.summary,
      "", "## Implemented", "", ...list(handoff.implementation.implemented),
      "", "## Not Implemented", "", ...list(handoff.implementation.notImplemented),
      "", "## Changed Files", "", ...list([...handoff.implementation.changedFiles, ...handoff.implementation.createdFiles, ...handoff.implementation.deletedFiles]),
      "", "## Verification", "",
      `- Build: ${handoff.verification.build}`, `- Tests: ${handoff.verification.tests}`,
      `- Regression: ${handoff.verification.regression}`, `- Smoke: ${handoff.verification.smoke}`, `- Scope: ${handoff.verification.scope}`,
      "", "## Alpha Review", "", `- Status: ${handoff.alphaReview.status}`, ...list(handoff.alphaReview.findings),
      "", "## Known Limitations", "", ...list(handoff.alphaReview.knownLimitations),
      "", "## Architecture Changes", "", ...list(handoff.architectureChanges),
      "", "## API Changes", "", ...list(handoff.apiChanges),
      "", "## Git Status", "", `- Commit: ${handoff.git.commitStatus}`, `- Push: ${handoff.git.pushStatus}`,
      "", "## ADR Candidates", "", ...list(handoff.adrCandidates),
      "", "## Technical Debt", "", ...list(handoff.technicalDebt),
      "", "## Planning Update", "", ...list(handoff.planningUpdate.summary),
    ].join("\n");
  }

  public async getRfcLifecycleGuidance(
    status?: ProjectStatus,
  ): Promise<RfcLifecycleGuidance> {
    const currentStatus = status ?? await this.repository.load();

    return this.deriveRfcLifecycleGuidance(currentStatus);
  }

  public async getPlanningSynchronizationStatus(
    status?: ProjectStatus,
  ): Promise<PlanningSynchronizationStatus> {
    const currentStatus = status ?? await this.repository.load();

    return this.derivePlanningSynchronizationStatus(currentStatus);
  }

  public async getRfcLifecycleHistory(): Promise<readonly RfcLifecycleEvent[]> {
    const status = await this.repository.load();

    return [...status.lifecycleHistory];
  }

  public async getLatestRfcLifecycleEvents(
    limit: number = 5,
  ): Promise<readonly RfcLifecycleEvent[]> {
    if (!Number.isInteger(limit) || limit < 1) {
      throw new Error("Lifecycle history limit must be a positive integer.");
    }

    const history = await this.getRfcLifecycleHistory();

    return history.slice(-limit);
  }

  public async markCurrentRfcCompleted(): Promise<ProjectStatus> {
    const status = await this.repository.load();

    if (!this.isValidRfcIdentifier(status.currentRfc)) {
      throw new Error("The current RFC must use the exact format RFC-0000.");
    }

    if (status.completedRfcs.includes(status.currentRfc)) {
      throw new Error("The current RFC is already completed.");
    }

    const updatedStatus: ProjectStatus = {
      ...status,
      completedRfcs: this.addCompletedRfcDeterministically(
        status.completedRfcs,
        status.currentRfc,
      ),
      lifecycleHistory: this.appendLifecycleEvent(status, "completed"),
    };

    await this.repository.save(updatedStatus);

    return updatedStatus;
  }

  public async rollbackCurrentRfc(): Promise<ProjectStatus> {
    const status = await this.repository.load();

    const previous = this.resolveRollbackTransition(status);

    if (previous.rfc === status.currentRfc) {
      throw new Error("Rollback must restore a different RFC.");
    }

    const updatedStatus: ProjectStatus = {
      ...status,
      phase: previous.phase,
      currentRfc: previous.rfc,
      release: previous.release,
      completedRfcs: [...status.completedRfcs],
      releaseReadiness: createDefaultReleaseReadiness(),
      lifecycleHistory: this.appendLifecycleEvent(
        {
          ...status,
          phase: previous.phase,
          currentRfc: previous.rfc,
          release: previous.release,
        },
        "rollbackCompleted",
        status.currentRfc,
      ),
    };

    await this.repository.save(updatedStatus);

    return updatedStatus;
  }

  public async previewCurrentRfcRollback(
    status?: ProjectStatus,
  ): Promise<RfcRollbackPreview> {
    const currentStatus = status ?? await this.repository.load();

    try {
      const target = this.resolveRollbackTransition(currentStatus);

      return {
        eligible: true,
        currentRfc: currentStatus.currentRfc,
        targetRfc: target.rfc,
        targetPhase: target.phase,
        targetRelease: target.release,
        readinessAfterRollback: createDefaultReleaseReadiness(),
      };
    } catch (error: unknown) {
      return {
        eligible: false,
        currentRfc: currentStatus.currentRfc,
        blockingReason: error instanceof Error
          ? error.message
          : "Rollback eligibility could not be determined.",
      };
    }
  }

  public async startNextRfc(
    input: StartNextRfcRequest,
  ): Promise<ProjectStatus> {
    const unknownField = Object.keys(input).find(
      (field) =>
        !START_NEXT_RFC_FIELDS.includes(
          field as (typeof START_NEXT_RFC_FIELDS)[number],
        ),
    );

    if (unknownField !== undefined) {
      throw new Error(`Unknown startNextRfc field: ${unknownField}.`);
    }

    if (typeof input.nextRfc !== "string" || !/^RFC-[0-9]{4}$/.test(input.nextRfc)) {
      throw new Error("nextRfc must use the exact format RFC-0000.");
    }

    const phase = input.phase?.trim();
    const release = input.release?.trim();

    if (phase !== undefined && phase.length === 0) {
      throw new Error("phase must not be empty.");
    }

    if (release !== undefined && release.length === 0) {
      throw new Error("release must not be empty.");
    }

    const status = await this.repository.load();

    if (input.nextRfc === status.currentRfc) {
      throw new Error("nextRfc must be different from the current RFC.");
    }

    if (status.completedRfcs.includes(input.nextRfc)) {
      throw new Error("nextRfc must not already be completed.");
    }

    if (!/^RFC-[0-9]{4}$/.test(status.currentRfc)) {
      throw new Error("The current RFC must use the exact format RFC-0000.");
    }

    const currentRfcNumber = Number(status.currentRfc.slice(4));
    const nextRfcNumber = Number(input.nextRfc.slice(4));

    if (nextRfcNumber <= currentRfcNumber) {
      throw new Error("nextRfc must be numerically greater than the current RFC.");
    }

    if (!status.completedRfcs.includes(status.currentRfc)) {
      throw new Error(
        "The current RFC must be completed before starting the next RFC.",
      );
    }

    const updatedStatus: ProjectStatus = {
      ...status,
      phase: phase ?? status.phase,
      currentRfc: input.nextRfc,
      release: release ?? status.release,
      completedRfcs: [...status.completedRfcs],
      releaseReadiness: createDefaultReleaseReadiness(),
      lifecycleHistory: this.appendLifecycleEvent(
        {
          ...status,
          phase: phase ?? status.phase,
          currentRfc: input.nextRfc,
          release: release ?? status.release,
        },
        "started",
      ),
    };

    await this.repository.save(updatedStatus);

    return updatedStatus;
  }

  public async updateReleaseReadiness(
    updates: Partial<ReleaseReadiness>,
  ): Promise<ProjectStatus> {
    const entries = Object.entries(updates);

    if (entries.length === 0) {
      throw new Error("At least one Release Readiness field must be provided.");
    }

    for (const [field, value] of entries) {
      if (
        !RELEASE_READINESS_FIELDS.includes(
          field as (typeof RELEASE_READINESS_FIELDS)[number],
        )
      ) {
        throw new Error(`Unknown Release Readiness field: ${field}.`);
      }

      if (!this.isReleaseReadinessState(value)) {
        throw new Error(
          `Invalid Release Readiness value for ${field}; expected pending, passed, or failed.`,
        );
      }
    }

    const status = await this.repository.load();
    const updatedStatus: ProjectStatus = {
      ...status,
      releaseReadiness: {
        ...status.releaseReadiness,
        ...updates,
      },
    };

    await this.repository.save(updatedStatus);

    return updatedStatus;
  }

  public async getCurrentRfc(): Promise<CurrentRfcStatus> {
    const status = await this.repository.load();

    return {
      currentRfc: status.currentRfc,
      phase: status.phase,
      release: status.release,
    };
  }

  public async listCompletedRfcs(): Promise<CompletedRfcsStatus> {
    const status = await this.repository.load();

    return {
      project: status.project,
      currentRfc: status.currentRfc,
      completedRfcs: status.completedRfcs,
      completedCount: status.completedRfcs.length,
    };
  }

  public async generateMainPlanningSync(): Promise<MainPlanningSyncResult> {
    const status = await this.repository.load();
    const updatedStatus: ProjectStatus = {
      ...status,
      lifecycleHistory: this.appendLifecycleEvent(status, "planningSynced"),
    };

    await this.repository.save(updatedStatus);

    const lifecycleGuidance = this.deriveRfcLifecycleGuidance(updatedStatus);
    const rollbackPreview = await this.previewCurrentRfcRollback(updatedStatus);
    const planningSynchronization =
      this.derivePlanningSynchronizationStatus(updatedStatus);
    const completionReadiness =
      await this.evaluateRfcCompletionReadiness(undefined, updatedStatus);
    const completedRfcLines = updatedStatus.completedRfcs.length > 0
      ? updatedStatus.completedRfcs.map((rfc) => `- ${rfc}`)
      : ["- None"];
    const timelineLines = updatedStatus.lifecycleHistory.map(
      (event) => `- ${this.formatLifecycleEvent(event)} (${event.timestamp})`,
    );
    const readinessItems = [
      "Core Build",
      "Core Tests",
      "CLI",
      "Incremental",
      "Review Workflow",
      "architecture-samples Validation",
      "Documentation Sync",
      "Release Candidate",
    ];
    const markdown = [
      "# DocPilot Main Planning Sync",
      "",
      "## Project Status",
      "",
      `- Project: ${updatedStatus.project}`,
      `- Current Phase: ${updatedStatus.phase}`,
      `- Current RFC: ${updatedStatus.currentRfc}`,
      `- Next Release: ${updatedStatus.release}`,
      "",
      "## Completed RFCs",
      "",
      ...completedRfcLines,
      "",
      "## Current Work",
      "",
      `The current RFC, ${updatedStatus.currentRfc}, is in progress.`,
      "",
      "## RFC Lifecycle",
      "",
      `- State: \`${lifecycleGuidance.state}\``,
      `- Recommended Tool: \`${lifecycleGuidance.nextAction}\``,
      `- Reason: ${lifecycleGuidance.reason}`,
      "",
      "## RFC Lifecycle Timeline",
      "",
      ...timelineLines,
      "",
      "## Rollback Preview",
      "",
      ...this.formatRollbackPreview(rollbackPreview),
      "",
      "## Planning Synchronization",
      "",
      ...this.formatPlanningSynchronization(planningSynchronization),
      ...(updatedStatus.pendingRfcHandoff === undefined
        ? []
        : ["", "## Pending RFC Handoff", "", this.renderRfcHandoff(updatedStatus.pendingRfcHandoff)]),
      "",
      "## Project Control",
      "",
      `- Current RFC: ${updatedStatus.currentRfc}`,
      `- Pending Handoff: ${updatedStatus.pendingRfcHandoff === undefined ? "No" : "Yes"}`,
      `- Completion Readiness: ${completionReadiness.status}`,
      "- Worker Execution: Controlled local execution supported",
      "- Commit Automation: Alpha-gated candidate commit supported",
      "- Push Approval: Required; MCP does not push",
      ...(completionReadiness.blockers.length === 0
        ? []
        : completionReadiness.blockers.map((blocker) => `- Blocker: ${blocker}`)),
      ...(completionReadiness.warnings.length === 0
        ? []
        : completionReadiness.warnings.map((warning) => `- Warning: ${warning}`)),
      "",
      "## Implementation Orchestration",
      "",
      `- Work Order: ${updatedStatus.pendingImplementationWorkOrder === undefined ? "None" : updatedStatus.pendingImplementationWorkOrder.id}`,
      `- Preflight: ${updatedStatus.implementationExecutionRecord?.preflight?.status ?? "Not Run"}`,
      `- Worker Execution: ${updatedStatus.implementationExecutionRecord?.workerExecution?.status ?? "Not Run"}`,
      `- Verification: ${updatedStatus.implementationExecutionRecord?.verification?.status ?? "Not Run"}`,
      `- Diff Validation: ${updatedStatus.implementationExecutionRecord?.diffValidation?.status ?? "Not Run"}`,
      `- Alpha: ${updatedStatus.implementationExecutionRecord?.alpha?.status ?? "Not Run"}`,
      `- Commit: ${updatedStatus.implementationExecutionRecord?.commitSha ?? "Not Created"}`,
      `- Push: ${updatedStatus.implementationExecutionRecord?.commitSha === undefined ? "Not Supported" : "Pending User Approval (execution not supported)"}`,
      "",
      "## Release Readiness",
      "",
      ...readinessItems.map((item) => `- ${item}: ⏳`),
    ].join("\n");

    return {
      project: updatedStatus.project,
      phase: updatedStatus.phase,
      currentRfc: updatedStatus.currentRfc,
      release: updatedStatus.release,
      completedRfcs: updatedStatus.completedRfcs,
      completedCount: updatedStatus.completedRfcs.length,
      markdown,
      lifecycleGuidance,
      lifecycleHistory: updatedStatus.lifecycleHistory,
      rollbackPreview,
      planningSynchronization,
    };
  }

  public async completeCurrentRfc(
    nextRfc: string,
  ): Promise<CompleteCurrentRfcResult> {
    const normalizedNextRfc = nextRfc.trim();

    if (!/^RFC-\d{4}$/.test(normalizedNextRfc)) {
      throw new Error("nextRfc must use the format RFC-0000.");
    }

    const status = await this.repository.load();
    const completedRfc = status.currentRfc;

    if (normalizedNextRfc === completedRfc) {
      throw new Error(
        "nextRfc must be different from the current RFC.",
      );
    }

    const completedRfcs = this.addCompletedRfcDeterministically(
      status.completedRfcs,
      completedRfc,
    );

    const updatedStatus: ProjectStatus = {
      ...status,
      currentRfc: normalizedNextRfc,
      completedRfcs,
    };

    await this.repository.save(updatedStatus);

    return {
      completedRfc,
      currentRfc: updatedStatus.currentRfc,
      phase: updatedStatus.phase,
      release: updatedStatus.release,
      completedRfcs: updatedStatus.completedRfcs,
    };
  }

  public async updateProjectStatus(
    request: UpdateProjectStatusRequest,
  ): Promise<ProjectStatus> {
    const status = await this.repository.load();

    const phase = request.phase?.trim();
    const release = request.release?.trim();
    const currentRfc = request.currentRfc?.trim();

    if (phase !== undefined && phase.length === 0) {
      throw new Error("phase must not be empty.");
    }

    if (release !== undefined && release.length === 0) {
      throw new Error("release must not be empty.");
    }

    if (
      currentRfc !== undefined &&
      !/^RFC-\d{4}$/.test(currentRfc)
    ) {
      throw new Error(
        "currentRfc must use the format RFC-0000.",
      );
    }

    if (
      phase === undefined &&
      release === undefined &&
      currentRfc === undefined
    ) {
      throw new Error(
        "At least one field must be provided.",
      );
    }

    const updatedStatus: ProjectStatus = {
      ...status,
      phase: phase ?? status.phase,
      release: release ?? status.release,
      currentRfc: currentRfc ?? status.currentRfc,
    };

    await this.repository.save(updatedStatus);

    return updatedStatus;
  }

  private isReleaseReadinessState(
    value: unknown,
  ): value is ReleaseReadinessState {
    return value === "pending" || value === "passed" || value === "failed";
  }

  private validateHandoffBusinessRules(handoff: RfcHandoff, currentRfc: string): void {
    if (handoff.schemaVersion !== RFC_HANDOFF_SCHEMA_VERSION) {
      throw new Error(`Unsupported Handoff schemaVersion: ${handoff.schemaVersion}.`);
    }
    if (!this.isValidRfcIdentifier(handoff.rfcId)) {
      throw new Error("Handoff rfcId must use the exact format RFC-0000.");
    }
    if (handoff.rfcId !== currentRfc) {
      throw new Error(`Handoff RFC ${handoff.rfcId} does not match current RFC ${currentRfc}.`);
    }
    if (handoff.implementation.summary.trim().length === 0) {
      throw new Error("Handoff implementation summary must not be empty.");
    }
    const implementationStatuses = ["NOT_STARTED", "IN_PROGRESS", "BLOCKED", "FAILED", "PASSED_WITH_LIMITATIONS", "PASSED"];
    const verificationStatuses = ["NOT_RUN", "PASSED", "FAILED", "BLOCKED"];
    const alphaStatuses = ["NOT_STARTED", "BLOCKED", "FAILED", "PASSED_WITH_LIMITATIONS", "PASSED"];
    if (!implementationStatuses.includes(handoff.implementation.status)) throw new Error("Invalid Handoff implementation status.");
    for (const [field, value] of [
      ["build", handoff.verification.build],
      ["tests", handoff.verification.tests],
      ["regression", handoff.verification.regression],
      ["smoke", handoff.verification.smoke],
      ["scope", handoff.verification.scope],
    ] as const) {
      if (!verificationStatuses.includes(value as string)) throw new Error(`Invalid Handoff verification status for ${field}.`);
    }
    if (!alphaStatuses.includes(handoff.alphaReview.status)) throw new Error("Invalid Handoff alpha review status.");
  }

  private normalizeHandoff(handoff: RfcHandoff): RfcHandoff {
    const copy = (items: readonly string[]) => [...items];
    const files = (items: readonly string[]) => [...new Set(items)].sort((left, right) => left < right ? -1 : left > right ? 1 : 0);
    return {
      schemaVersion: handoff.schemaVersion,
      rfcId: handoff.rfcId,
      ...(handoff.worker === undefined ? {} : { worker: { ...handoff.worker } }),
      implementation: {
        ...handoff.implementation,
        summary: handoff.implementation.summary.trim(),
        implemented: copy(handoff.implementation.implemented),
        notImplemented: copy(handoff.implementation.notImplemented),
        changedFiles: files(handoff.implementation.changedFiles),
        createdFiles: files(handoff.implementation.createdFiles),
        deletedFiles: files(handoff.implementation.deletedFiles),
      },
      verification: {
        ...handoff.verification,
        commandsExecuted: copy(handoff.verification.commandsExecuted),
        details: copy(handoff.verification.details),
      },
      alphaReview: {
        ...handoff.alphaReview,
        findings: copy(handoff.alphaReview.findings),
        blockers: copy(handoff.alphaReview.blockers),
        warnings: copy(handoff.alphaReview.warnings),
        knownLimitations: copy(handoff.alphaReview.knownLimitations),
        unresolvedItems: copy(handoff.alphaReview.unresolvedItems),
      },
      architectureChanges: copy(handoff.architectureChanges),
      apiChanges: copy(handoff.apiChanges),
      adrCandidates: copy(handoff.adrCandidates),
      technicalDebt: copy(handoff.technicalDebt),
      git: { ...handoff.git },
      planningUpdate: {
        summary: copy(handoff.planningUpdate.summary),
        releaseReadinessChanges: copy(handoff.planningUpdate.releaseReadinessChanges),
        warnings: copy(handoff.planningUpdate.warnings),
      },
    };
  }

  private isValidRfcIdentifier(value: string): boolean {
    return /^RFC-[0-9]{4}$/.test(value);
  }

  private deriveRfcLifecycleGuidance(
    status: ProjectStatus,
  ): RfcLifecycleGuidance {
    const planningFields: Pick<
      RfcLifecycleGuidance,
      "planningSynchronizationState" | "planningSynchronizationRequired"
    > = {};

    try {
      const planningSynchronization =
        this.derivePlanningSynchronizationStatus(status);
      planningFields.planningSynchronizationState = planningSynchronization.state;
      planningFields.planningSynchronizationRequired =
        !planningSynchronization.synchronized;
    } catch {
      // Primary lifecycle guidance retains its established inconsistent result.
    }

    if (!this.isValidRfcIdentifier(status.currentRfc)) {
      return {
        state: "inconsistent",
        nextAction: "manualReview",
        reason: "Current RFC does not use the required RFC-0000 format.",
        ...planningFields,
      };
    }

    if (status.completedRfcs.some((rfc) => !this.isValidRfcIdentifier(rfc))) {
      return {
        state: "inconsistent",
        nextAction: "manualReview",
        reason: "Completed RFC history contains a malformed RFC identifier.",
        ...planningFields,
      };
    }

    if (new Set(status.completedRfcs).size !== status.completedRfcs.length) {
      return {
        state: "inconsistent",
        nextAction: "manualReview",
        reason: "Completed RFC history contains duplicate RFC identifiers.",
        ...planningFields,
      };
    }

    if (status.completedRfcs.includes(status.currentRfc)) {
      return {
        state: "completed_waiting_next",
        nextAction: "startNextRfc",
        reason: "Current RFC is completed and the next RFC may now be started.",
        ...planningFields,
      };
    }

    return {
      state: "in_progress",
      nextAction: "markCurrentRfcCompleted",
      reason: "Current RFC has not been marked completed.",
      ...planningFields,
    };
  }

  private derivePlanningSynchronizationStatus(
    status: ProjectStatus,
  ): PlanningSynchronizationStatus {
    this.analyzeLifecycleHistory(status, true);

    const latestPlanningSync = [...status.lifecycleHistory]
      .reverse()
      .find(({ type }) => type === "planningSynced");
    const latestRelevant = [...status.lifecycleHistory]
      .reverse()
      .find(({ type }) => PLANNING_RELEVANT_EVENT_TYPES.includes(type));
    let state: PlanningSynchronizationState;
    let reason: string;

    if (latestPlanningSync === undefined) {
      state = "neverSynced";
      reason = "Main Planning has not been synchronized yet.";
    } else {
      const syncIndex = status.lifecycleHistory.indexOf(latestPlanningSync);
      const relevantIndex = latestRelevant === undefined
        ? -1
        : status.lifecycleHistory.indexOf(latestRelevant);

      if (
        relevantIndex > syncIndex ||
        latestPlanningSync.rfc !== status.currentRfc
      ) {
        state = "stale";
        reason = latestRelevant?.type === "rollbackCompleted"
          ? "RFC rollback occurred after the latest Main Planning synchronization."
          : "RFC lifecycle changed after the latest Main Planning synchronization.";
      } else {
        state = "current";
        reason = "Main Planning reflects the latest lifecycle state.";
      }
    }

    const expectedDocumentationSync = state === "current" ? "passed" : "pending";
    const documentationSyncConsistent =
      status.releaseReadiness.documentationSync === expectedDocumentationSync;

    return {
      state,
      synchronized: state === "current",
      currentRfc: status.currentRfc,
      ...(latestPlanningSync === undefined ? {} : {
        lastPlanningSyncEventId: latestPlanningSync.id,
        lastPlanningSyncRfc: latestPlanningSync.rfc,
      }),
      ...(latestRelevant === undefined ? {} : {
        latestRelevantEventId: latestRelevant.id,
        latestRelevantEventType: latestRelevant.type,
      }),
      reason,
      recommendedAction: state === "current" ? "none" : "generateMainPlanningSync",
      expectedDocumentationSync,
      documentationSyncConsistent,
      ...(documentationSyncConsistent ? {} : {
        documentationSyncReason:
          `Documentation Sync is ${status.releaseReadiness.documentationSync}, but expected ${expectedDocumentationSync} while Main Planning is ${state}.`,
      }),
    };
  }

  private addCompletedRfcDeterministically(
    completedRfcs: string[],
    completedRfc: string,
  ): string[] {
    return [...new Set([...completedRfcs, completedRfc])].sort((left, right) => {
      const leftMatch = /^RFC-([0-9]{4})$/.exec(left);
      const rightMatch = /^RFC-([0-9]{4})$/.exec(right);

      if (leftMatch !== null && rightMatch !== null) {
        return Number(leftMatch[1]) - Number(rightMatch[1]);
      }

      if (leftMatch !== null) {
        return -1;
      }

      if (rightMatch !== null) {
        return 1;
      }

      return left < right ? -1 : left > right ? 1 : 0;
    });
  }

  private appendLifecycleEvent(
    status: ProjectStatus,
    type: RfcLifecycleEventType,
    fromRfc?: string,
  ): readonly RfcLifecycleEvent[] {
    const existingIds = new Set(status.lifecycleHistory.map(({ id }) => id));
    let sequence = status.lifecycleHistory.length + 1;
    let id = this.formatLifecycleEventId(sequence);

    while (existingIds.has(id)) {
      sequence += 1;
      id = this.formatLifecycleEventId(sequence);
    }

    const event: RfcLifecycleEvent = {
      id,
      type,
      rfc: status.currentRfc,
      ...(fromRfc !== undefined ? { fromRfc } : {}),
      phase: status.phase,
      release: status.release,
      timestamp: new Date().toISOString(),
    };

    return [...status.lifecycleHistory, event];
  }

  private analyzeLifecycleHistory(
    status: ProjectStatus,
    allowLegacyPlanningReanchor: boolean = false,
  ): LifecycleAnalysis {
    if (!this.isValidRfcIdentifier(status.currentRfc)) {
      throw new Error("The current RFC must use the exact format RFC-0000.");
    }

    let active: ActiveRfcContext | undefined;
    let latestTransition: RfcTransition | undefined;

    for (const [index, event] of status.lifecycleHistory.entries()) {
      const expectedId = this.formatLifecycleEventId(index + 1);

      if (event.id !== expectedId) {
        throw new Error(
          `Lifecycle event ID sequence is invalid at index ${index}; expected ${expectedId}.`,
        );
      }

      if (!this.isValidRfcIdentifier(event.rfc)) {
        throw new Error(`Lifecycle event at index ${index} has a malformed RFC.`);
      }

      const eventContext: ActiveRfcContext = {
        rfc: event.rfc,
        phase: event.phase,
        release: event.release,
      };

      if (active === undefined) {
        if (event.type === "rollbackCompleted") {
          throw new Error(
            "Lifecycle history begins with a rollback and cannot resolve a previous RFC.",
          );
        }

        active = eventContext;
        continue;
      }

      if (event.type === "started") {
        if (event.rfc === active.rfc) {
          throw new Error(
            `Lifecycle history contains an ambiguous started event at index ${index}.`,
          );
        }

        latestTransition = {
          type: "started",
          from: active,
          to: eventContext,
        };
        active = eventContext;
        continue;
      }

      if (event.type === "rollbackCompleted") {
        if (event.fromRfc !== active.rfc || event.rfc === active.rfc) {
          throw new Error(
            `Lifecycle rollback evidence is inconsistent at index ${index}.`,
          );
        }

        latestTransition = {
          type: "rollbackCompleted",
          from: active,
          to: eventContext,
        };
        active = eventContext;
        continue;
      }

      if (
        event.type === "planningSynced" &&
        event.rfc !== active.rfc &&
        allowLegacyPlanningReanchor &&
        event.rfc === status.currentRfc
      ) {
        active = eventContext;
        continue;
      }

      if (event.rfc !== active.rfc) {
        throw new Error(
          `Lifecycle history conflicts with the active RFC at index ${index}.`,
        );
      }

      active = eventContext;
    }

    if (
      active !== undefined &&
      active.rfc !== status.currentRfc &&
      !allowLegacyPlanningReanchor
    ) {
      throw new Error(
        "Current project state conflicts with lifecycle history.",
      );
    }

    return {
      ...(active === undefined ? {} : { active }),
      ...(latestTransition === undefined ? {} : { latestTransition }),
    };
  }

  private resolveRollbackTransition(status: ProjectStatus): ActiveRfcContext {
    if (status.lifecycleHistory.length === 0) {
      throw new Error("Lifecycle history is empty; no previous RFC can be resolved.");
    }

    const { latestTransition } = this.analyzeLifecycleHistory(status);

    if (latestTransition === undefined) {
      throw new Error("Lifecycle history does not contain a previous RFC transition.");
    }

    if (latestTransition.to.rfc !== status.currentRfc) {
      throw new Error("Lifecycle history does not resolve the current RFC transition.");
    }

    if (latestTransition.type === "rollbackCompleted") {
      throw new Error(
        "Repeated rollback is not supported after the latest rollback event.",
      );
    }

    return latestTransition.from;
  }

  private formatLifecycleEventId(sequence: number): string {
    return `rfc-event-${sequence.toString().padStart(6, "0")}`;
  }

  private formatLifecycleEvent(event: RfcLifecycleEvent): string {
    if (event.type === "started") {
      return `Started ${event.rfc}`;
    }

    if (event.type === "completed") {
      return `Completed ${event.rfc}`;
    }

    if (event.type === "rollbackCompleted") {
      return `Rolled back ${event.fromRfc} → ${event.rfc}`;
    }

    return `Planning Synced for ${event.rfc}`;
  }

  private formatRollbackPreview(preview: RfcRollbackPreview): string[] {
    if (!preview.eligible) {
      return [
        "- Eligible: No",
        `- Current RFC: ${preview.currentRfc}`,
        `- Reason: ${preview.blockingReason}`,
      ];
    }

    return [
      "- Eligible: Yes",
      `- Current RFC: ${preview.currentRfc}`,
      `- Rollback Target: ${preview.targetRfc}`,
      `- Restored Phase: ${preview.targetPhase}`,
      `- Restored Release: ${preview.targetRelease}`,
    ];
  }

  private formatPlanningSynchronization(
    status: PlanningSynchronizationStatus,
  ): string[] {
    return [
      `- Status: ${status.state}`,
      `- Current RFC: ${status.currentRfc}`,
      ...(status.lastPlanningSyncEventId === undefined
        ? []
        : [`- Last Planning Sync: ${status.lastPlanningSyncEventId}`]),
      `- Reason: ${status.reason}`,
      `- Documentation Sync: ${status.documentationSyncConsistent ? "Consistent" : "Inconsistent"}`,
      `- Recommended Action: ${status.recommendedAction}`,
    ];
  }
}
