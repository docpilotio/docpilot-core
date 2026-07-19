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
    };
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
