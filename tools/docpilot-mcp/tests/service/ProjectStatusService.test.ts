import { readFile } from "node:fs/promises";

import { afterEach, describe, expect, it } from "vitest";

import {
  createProjectStatus,
  createTemporaryState,
  type TemporaryState,
} from "../support/testState.js";

describe("ProjectStatusService", () => {
  let temporaryState: TemporaryState | undefined;

  afterEach(async () => {
    await temporaryState?.cleanup();
    temporaryState = undefined;
  });

  it("returns repository state", async () => {
    const status = createProjectStatus();
    temporaryState = await createTemporaryState(status);

    await expect(temporaryState.service.getProjectStatus()).resolves.toEqual(
      status,
    );
  });

  it("updates one readiness field and preserves omitted fields", async () => {
    temporaryState = await createTemporaryState(createProjectStatus());

    const updated = await temporaryState.service.updateReleaseReadiness({
      coreBuild: "passed",
    });

    expect(updated.releaseReadiness).toEqual({
      ...createProjectStatus().releaseReadiness,
      coreBuild: "passed",
    });
    await expect(temporaryState.repository.load()).resolves.toEqual(updated);
  });

  it("updates multiple readiness fields atomically", async () => {
    temporaryState = await createTemporaryState(createProjectStatus());

    const updated = await temporaryState.service.updateReleaseReadiness({
      coreTests: "failed",
      cli: "passed",
      releaseCandidate: "passed",
    });

    expect(updated.releaseReadiness).toMatchObject({
      coreTests: "failed",
      cli: "passed",
      releaseCandidate: "passed",
    });
    await expect(temporaryState.repository.load()).resolves.toEqual(updated);
  });

  it("rejects empty, unknown, and invalid readiness updates without persisting", async () => {
    temporaryState = await createTemporaryState(createProjectStatus());
    const before = await readFile(temporaryState.stateFilePath, "utf-8");

    await expect(
      temporaryState.service.updateReleaseReadiness({}),
    ).rejects.toThrow("At least one Release Readiness field must be provided.");
    await expect(
      temporaryState.service.updateReleaseReadiness({
        unexpected: "passed",
      } as never),
    ).rejects.toThrow("Unknown Release Readiness field: unexpected.");
    await expect(
      temporaryState.service.updateReleaseReadiness({
        coreBuild: "invalid",
        coreTests: "passed",
      } as never),
    ).rejects.toThrow("Invalid Release Readiness value for coreBuild");
    await expect(readFile(temporaryState.stateFilePath, "utf-8")).resolves.toBe(
      before,
    );
  });

  it("preserves existing project status update behavior and persistence", async () => {
    temporaryState = await createTemporaryState(createProjectStatus());

    const updated = await temporaryState.service.updateProjectStatus({
      phase: "Phase 2",
      release: "v0.7",
      currentRfc: "RFC-0040",
    });

    expect(updated).toMatchObject({
      phase: "Phase 2",
      release: "v0.7",
      currentRfc: "RFC-0040",
    });
    await expect(temporaryState.repository.load()).resolves.toEqual(updated);
  });

  it("completes the current RFC without disturbing completed ordering", async () => {
    temporaryState = await createTemporaryState(createProjectStatus());

    const result = await temporaryState.service.completeCurrentRfc("RFC-0040");

    expect(result.completedRfcs).toEqual([
      "RFC-0037",
      "RFC-0038",
      "RFC-0039",
    ]);
    const persisted = await temporaryState.repository.load();
    expect(persisted.currentRfc).toBe("RFC-0040");
    expect(persisted.completedRfcs).toEqual(result.completedRfcs);
  });

  it("does not duplicate an already completed RFC", async () => {
    temporaryState = await createTemporaryState(
      createProjectStatus({
        completedRfcs: ["RFC-0037", "RFC-0039", "RFC-0038"],
      }),
    );

    const result = await temporaryState.service.completeCurrentRfc("RFC-0040");

    expect(result.completedRfcs).toEqual([
      "RFC-0037",
      "RFC-0039",
      "RFC-0038",
    ]);
  });
});
