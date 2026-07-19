import type { ProjectStatus } from "../model/ProjectStatus.js";
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

export class ProjectStatusService {
  public constructor(
    private readonly repository: ProjectStateRepository,
  ) {}

  public async getProjectStatus(): Promise<ProjectStatus> {
    return this.repository.load();
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

    const completedRfcs = status.completedRfcs.includes(completedRfc)
      ? [...status.completedRfcs]
      : [...status.completedRfcs, completedRfc];

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
}
