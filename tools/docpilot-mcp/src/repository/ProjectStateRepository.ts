import {
  readFile,
  rename,
  writeFile,
} from "node:fs/promises";
import { dirname, resolve } from "node:path";

import type { ProjectStatus } from "../model/ProjectStatus.js";

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
    };
  }
}