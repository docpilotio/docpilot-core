import type {
  ProjectStatus,
  ReleaseReadiness,
  ReleaseReadinessState,
} from "../model/ProjectStatus.js";
import { createDefaultReleaseReadiness } from "../model/ProjectStatus.js";
import type { RfcLifecycleGuidance } from "../model/RfcLifecycleGuidance.js";
import type {
  RfcLifecycleEvent,
  RfcLifecycleEventType,
} from "../model/RfcLifecycleEvent.js";
import { ProjectStateRepository } from "../repository/ProjectStateRepository.js";

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

export class ProjectStatusService {
  public constructor(
    private readonly repository: ProjectStateRepository,
  ) {}

  public async getProjectStatus(): Promise<ProjectStatus> {
    return this.repository.load();
  }

  public async getRfcLifecycleGuidance(
    status?: ProjectStatus,
  ): Promise<RfcLifecycleGuidance> {
    const currentStatus = status ?? await this.repository.load();

    return this.deriveRfcLifecycleGuidance(currentStatus);
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

  private isValidRfcIdentifier(value: string): boolean {
    return /^RFC-[0-9]{4}$/.test(value);
  }

  private deriveRfcLifecycleGuidance(
    status: ProjectStatus,
  ): RfcLifecycleGuidance {
    if (!this.isValidRfcIdentifier(status.currentRfc)) {
      return {
        state: "inconsistent",
        nextAction: "manualReview",
        reason: "Current RFC does not use the required RFC-0000 format.",
      };
    }

    if (status.completedRfcs.some((rfc) => !this.isValidRfcIdentifier(rfc))) {
      return {
        state: "inconsistent",
        nextAction: "manualReview",
        reason: "Completed RFC history contains a malformed RFC identifier.",
      };
    }

    if (new Set(status.completedRfcs).size !== status.completedRfcs.length) {
      return {
        state: "inconsistent",
        nextAction: "manualReview",
        reason: "Completed RFC history contains duplicate RFC identifiers.",
      };
    }

    if (status.completedRfcs.includes(status.currentRfc)) {
      return {
        state: "completed_waiting_next",
        nextAction: "startNextRfc",
        reason: "Current RFC is completed and the next RFC may now be started.",
      };
    }

    return {
      state: "in_progress",
      nextAction: "markCurrentRfcCompleted",
      reason: "Current RFC has not been marked completed.",
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
      phase: status.phase,
      release: status.release,
      timestamp: new Date().toISOString(),
    };

    return [...status.lifecycleHistory, event];
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

    return `Planning Synced for ${event.rfc}`;
  }
}
