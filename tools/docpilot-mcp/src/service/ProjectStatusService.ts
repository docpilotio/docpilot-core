import type {
  ProjectStatus,
  ReleaseReadiness,
  ReleaseReadinessState,
} from "../model/ProjectStatus.js";
import { createDefaultReleaseReadiness } from "../model/ProjectStatus.js";
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
    const completedRfcLines = status.completedRfcs.length > 0
      ? status.completedRfcs.map((rfc) => `- ${rfc}`)
      : ["- None"];
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
      `- Project: ${status.project}`,
      `- Current Phase: ${status.phase}`,
      `- Current RFC: ${status.currentRfc}`,
      `- Next Release: ${status.release}`,
      "",
      "## Completed RFCs",
      "",
      ...completedRfcLines,
      "",
      "## Current Work",
      "",
      `The current RFC, ${status.currentRfc}, is in progress.`,
      "",
      "## Release Readiness",
      "",
      ...readinessItems.map((item) => `- ${item}: ⏳`),
    ].join("\n");

    return {
      project: status.project,
      phase: status.phase,
      currentRfc: status.currentRfc,
      release: status.release,
      completedRfcs: status.completedRfcs,
      completedCount: status.completedRfcs.length,
      markdown,
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
}
