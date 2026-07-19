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

  it("guides an in-progress RFC to markCurrentRfcCompleted", async () => {
    temporaryState = await createTemporaryState(createProjectStatus());

    await expect(
      temporaryState.service.getRfcLifecycleGuidance(),
    ).resolves.toEqual({
      state: "in_progress",
      nextAction: "markCurrentRfcCompleted",
      reason: "Current RFC has not been marked completed.",
    });
  });

  it("guides a completed current RFC to startNextRfc", async () => {
    temporaryState = await createTemporaryState(
      createProjectStatus({
        completedRfcs: ["RFC-0037", "RFC-0038", "RFC-0039"],
      }),
    );

    await expect(
      temporaryState.service.getRfcLifecycleGuidance(),
    ).resolves.toEqual({
      state: "completed_waiting_next",
      nextAction: "startNextRfc",
      reason: "Current RFC is completed and the next RFC may now be started.",
    });
  });

  it.each([
    [
      "malformed current RFC",
      createProjectStatus({ currentRfc: "RFC-39" }),
      "Current RFC does not use the required RFC-0000 format.",
    ],
    [
      "malformed completed RFC",
      createProjectStatus({ completedRfcs: ["RFC-0037", "bad-rfc"] }),
      "Completed RFC history contains a malformed RFC identifier.",
    ],
    [
      "duplicate completed RFC",
      createProjectStatus({ completedRfcs: ["RFC-0037", "RFC-0037"] }),
      "Completed RFC history contains duplicate RFC identifiers.",
    ],
  ])(
    "returns manual review guidance for %s",
    async (_caseName, status, reason) => {
      temporaryState = await createTemporaryState(status);

      await expect(
        temporaryState.service.getRfcLifecycleGuidance(),
      ).resolves.toEqual({
        state: "inconsistent",
        nextAction: "manualReview",
        reason,
      });
    },
  );

  it("derives guidance deterministically without modifying state", async () => {
    const status = createProjectStatus({
      completedRfcs: ["RFC-0038", "RFC-0037"],
      releaseReadiness: {
        ...createDefaultReleaseReadiness(),
        coreBuild: "passed",
        releaseCandidate: "failed",
      },
    });
    temporaryState = await createTemporaryState(status);
    const before = await readFile(temporaryState.stateFilePath, "utf-8");
    const saveSpy = vi.spyOn(temporaryState.repository, "save");

    const first = await temporaryState.service.getRfcLifecycleGuidance();
    const second = await temporaryState.service.getRfcLifecycleGuidance();

    expect(second).toEqual(first);
    expect(saveSpy).not.toHaveBeenCalled();
    await expect(readFile(temporaryState.stateFilePath, "utf-8")).resolves.toBe(
      before,
    );
    await expect(temporaryState.repository.load()).resolves.toEqual(status);
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

  it("preserves the legacy combined complete-and-advance behavior", async () => {
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
    expect(persisted.releaseReadiness).toEqual(
      createProjectStatus().releaseReadiness,
    );
  });

  it("canonicalizes completed RFC ordering without duplicates", async () => {
    temporaryState = await createTemporaryState(
      createProjectStatus({
        completedRfcs: ["RFC-0037", "RFC-0039", "RFC-0038"],
      }),
    );

    const result = await temporaryState.service.completeCurrentRfc("RFC-0040");

    expect(result.completedRfcs).toEqual(["RFC-0037", "RFC-0038", "RFC-0039"]);
  });

  it("marks the current RFC completed with preserved state and one save", async () => {
    const status = createProjectStatus({
      completedRfcs: ["RFC-0038", "RFC-0037", "RFC-0037"],
      releaseReadiness: {
        ...createDefaultReleaseReadiness(),
        coreBuild: "passed",
        coreTests: "failed",
        releaseCandidate: "passed",
      },
    });
    temporaryState = await createTemporaryState(status);
    const saveSpy = vi.spyOn(temporaryState.repository, "save");

    const updated = await temporaryState.service.markCurrentRfcCompleted();

    expect(updated).toEqual({
      ...status,
      currentRfc: status.currentRfc,
      phase: status.phase,
      release: status.release,
      completedRfcs: ["RFC-0037", "RFC-0038", "RFC-0039"],
      releaseReadiness: status.releaseReadiness,
      lifecycleHistory: [
        expect.objectContaining({
          id: "rfc-event-000001",
          type: "completed",
          rfc: "RFC-0039",
          phase: status.phase,
          release: status.release,
          timestamp: expect.any(String),
        }),
      ],
    });
    expect(saveSpy).toHaveBeenCalledTimes(1);
    expect(saveSpy).toHaveBeenCalledWith(updated);
    await expect(temporaryState.repository.load()).resolves.toEqual(updated);
  });

  it.each([
    [
      "an already completed current RFC",
      createProjectStatus({ completedRfcs: ["RFC-0039"] }),
      "The current RFC is already completed.",
    ],
    [
      "a malformed current RFC",
      createProjectStatus({ currentRfc: "RFC-39" }),
      "The current RFC must use the exact format RFC-0000.",
    ],
  ])(
    "rejects %s without modifying persistence",
    async (_caseName, status, errorMessage) => {
      temporaryState = await createTemporaryState(status);
      const before = await readFile(temporaryState.stateFilePath, "utf-8");
      const saveSpy = vi.spyOn(temporaryState.repository, "save");

      await expect(
        temporaryState.service.markCurrentRfcCompleted(),
      ).rejects.toThrow(errorMessage);

      expect(saveSpy).not.toHaveBeenCalled();
      await expect(
        readFile(temporaryState.stateFilePath, "utf-8"),
      ).resolves.toBe(before);
    },
  );

  it("supports the preferred mark-then-start lifecycle", async () => {
    const readiness = {
      ...createDefaultReleaseReadiness(),
      coreBuild: "passed" as const,
      documentationSync: "failed" as const,
    };
    temporaryState = await createTemporaryState(
      createProjectStatus({ releaseReadiness: readiness }),
    );

    await expect(
      temporaryState.service.startNextRfc({ nextRfc: "RFC-0040" }),
    ).rejects.toThrow("must be completed");
    const marked = await temporaryState.service.markCurrentRfcCompleted();
    expect(marked.currentRfc).toBe("RFC-0039");
    expect(marked.releaseReadiness).toEqual(readiness);

    const started = await temporaryState.service.startNextRfc({
      nextRfc: "RFC-0040",
    });
    expect(started.currentRfc).toBe("RFC-0040");
    expect(started.completedRfcs).toEqual(marked.completedRfcs);
    expect(started.releaseReadiness).toEqual(
      createDefaultReleaseReadiness(),
    );
  });

  it("appends completed, started, and planningSynced events in order", async () => {
    vi.useFakeTimers();
    try {
      temporaryState = await createTemporaryState(createProjectStatus());
      vi.setSystemTime(new Date("2026-07-19T10:00:00.000Z"));
      await temporaryState.service.markCurrentRfcCompleted();
      vi.setSystemTime(new Date("2026-07-19T10:01:00.000Z"));
      await temporaryState.service.startNextRfc({ nextRfc: "RFC-0040" });
      vi.setSystemTime(new Date("2026-07-19T10:02:00.000Z"));
      const planning = await temporaryState.service.generateMainPlanningSync();

      const history = await temporaryState.service.getRfcLifecycleHistory();
      expect(history).toEqual([
        {
          id: "rfc-event-000001",
          type: "completed",
          rfc: "RFC-0039",
          phase: "Phase 1 — MVP",
          release: "v0.6 MVP",
          timestamp: "2026-07-19T10:00:00.000Z",
        },
        {
          id: "rfc-event-000002",
          type: "started",
          rfc: "RFC-0040",
          phase: "Phase 1 — MVP",
          release: "v0.6 MVP",
          timestamp: "2026-07-19T10:01:00.000Z",
        },
        {
          id: "rfc-event-000003",
          type: "planningSynced",
          rfc: "RFC-0040",
          phase: "Phase 1 — MVP",
          release: "v0.6 MVP",
          timestamp: "2026-07-19T10:02:00.000Z",
        },
      ]);
      await expect(
        temporaryState.service.getLatestRfcLifecycleEvents(2),
      ).resolves.toEqual(history.slice(1));
      expect(planning.lifecycleHistory).toEqual(history);
      expect(planning.markdown).toContain("## RFC Lifecycle Timeline");
      expect(planning.markdown).toContain("Completed RFC-0039");
      expect(planning.markdown).toContain("Started RFC-0040");
      expect(planning.markdown).toContain("Planning Synced for RFC-0040");
    } finally {
      vi.useRealTimers();
    }
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
      lifecycleHistory: [
        expect.objectContaining({
          id: "rfc-event-000001",
          type: "started",
          rfc: "RFC-0040",
          phase: status.phase,
          release: status.release,
          timestamp: expect.any(String),
        }),
      ],
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
