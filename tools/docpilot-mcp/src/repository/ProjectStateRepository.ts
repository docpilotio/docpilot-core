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
    };
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

    return value.map((event, index) =>
      this.validateLifecycleEvent(event, index),
    );
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
      phase: event.phase,
      release: event.release,
      timestamp,
    };
  }

  private isLifecycleEventType(value: unknown): value is RfcLifecycleEventType {
    return value === "started" || value === "completed" || value === "planningSynced";
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
