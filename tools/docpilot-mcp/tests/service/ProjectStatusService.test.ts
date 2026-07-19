import { readFile } from "node:fs/promises";

import { afterEach, describe, expect, it, vi } from "vitest";

import { createDefaultReleaseReadiness } from "../../src/model/ProjectStatus.js";

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

  it("starts and persists a valid next RFC with one final save", async () => {
    const status = createProjectStatus({
      completedRfcs: ["RFC-0037", "RFC-0038", "RFC-0039"],
      releaseReadiness: {
        ...createDefaultReleaseReadiness(),
        coreBuild: "passed",
        coreTests: "failed",
      },
    });
    temporaryState = await createTemporaryState(status);
    const saveSpy = vi.spyOn(temporaryState.repository, "save");

    const updated = await temporaryState.service.startNextRfc({
      nextRfc: "RFC-0040",
    });

    expect(updated).toEqual({
      ...status,
      currentRfc: "RFC-0040",
      completedRfcs: status.completedRfcs,
      releaseReadiness: createDefaultReleaseReadiness(),
    });
    expect(saveSpy).toHaveBeenCalledTimes(1);
    expect(saveSpy).toHaveBeenCalledWith(updated);
    await expect(temporaryState.repository.load()).resolves.toEqual(updated);
  });

  it("applies a valid optional phase update", async () => {
    temporaryState = await createTemporaryState(
      createProjectStatus({
        completedRfcs: ["RFC-0037", "RFC-0038", "RFC-0039"],
      }),
    );

    const updated = await temporaryState.service.startNextRfc({
      nextRfc: "RFC-0040",
      phase: "Phase 2",
    });

    expect(updated.phase).toBe("Phase 2");
    expect(updated.release).toBe("v0.6 MVP");
  });

  it("applies a valid optional release update", async () => {
    temporaryState = await createTemporaryState(
      createProjectStatus({
        completedRfcs: ["RFC-0037", "RFC-0038", "RFC-0039"],
      }),
    );

    const updated = await temporaryState.service.startNextRfc({
      nextRfc: "RFC-0040",
      release: "v0.7",
    });

    expect(updated.phase).toBe("Phase 1 — MVP");
    expect(updated.release).toBe("v0.7");
  });

  it.each([
    ["equal to current", "RFC-0039", ["RFC-0039"], "different from"],
    ["already completed", "RFC-0041", ["RFC-0039", "RFC-0041"], "must not already"],
    ["numerically lower", "RFC-0036", ["RFC-0039"], "numerically greater"],
    ["malformed", "RFC-45", ["RFC-0039"], "exact format"],
    ["lowercase", "rfc-0040", ["RFC-0039"], "exact format"],
    ["surrounded by whitespace", " RFC-0040 ", ["RFC-0039"], "exact format"],
    ["started before current completion", "RFC-0040", ["RFC-0037", "RFC-0038"], "must be completed"],
  ])(
    "rejects a next RFC that is %s without modifying persistence",
    async (_caseName, nextRfc, completedRfcs, errorText) => {
      temporaryState = await createTemporaryState(
        createProjectStatus({ completedRfcs }),
      );
      const before = await readFile(temporaryState.stateFilePath, "utf-8");
      const saveSpy = vi.spyOn(temporaryState.repository, "save");

      await expect(
        temporaryState.service.startNextRfc({ nextRfc }),
      ).rejects.toThrow(errorText);

      expect(saveSpy).not.toHaveBeenCalled();
      await expect(
        readFile(temporaryState.stateFilePath, "utf-8"),
      ).resolves.toBe(before);
    },
  );

  it("rejects empty optional updates and unknown fields before persistence", async () => {
    temporaryState = await createTemporaryState(
      createProjectStatus({ completedRfcs: ["RFC-0039"] }),
    );
    const before = await readFile(temporaryState.stateFilePath, "utf-8");

    await expect(
      temporaryState.service.startNextRfc({
        nextRfc: "RFC-0040",
        phase: "   ",
      }),
    ).rejects.toThrow("phase must not be empty.");
    await expect(
      temporaryState.service.startNextRfc({
        nextRfc: "RFC-0040",
        release: "",
      }),
    ).rejects.toThrow("release must not be empty.");
    await expect(
      temporaryState.service.startNextRfc({
        nextRfc: "RFC-0040",
        unexpected: true,
      } as never),
    ).rejects.toThrow("Unknown startNextRfc field: unexpected.");
    await expect(readFile(temporaryState.stateFilePath, "utf-8")).resolves.toBe(
      before,
    );
  });
});
