import { readFile } from "node:fs/promises";

import { afterEach, describe, expect, it, vi } from "vitest";

import { createDefaultReleaseReadiness } from "../../src/model/ProjectStatus.js";
import type { RfcLifecycleEvent } from "../../src/model/RfcLifecycleEvent.js";
import {
  createProjectStatus,
  createTemporaryState,
  type TemporaryState,
} from "../support/testState.js";

const sync39: RfcLifecycleEvent = {
  id: "rfc-event-000001",
  type: "planningSynced",
  rfc: "RFC-0039",
  phase: "Phase 1",
  release: "v0.8.0",
  timestamp: "2099-01-01T00:00:00.000Z",
};

describe("Planning Synchronization Status", () => {
  let temporaryState: TemporaryState | undefined;

  afterEach(async () => {
    await temporaryState?.cleanup();
    temporaryState = undefined;
  });

  it("returns deterministic neverSynced status without persistence", async () => {
    const status = createProjectStatus();
    temporaryState = await createTemporaryState(status);
    const saveSpy = vi.spyOn(temporaryState.repository, "save");
    const before = await readFile(temporaryState.stateFilePath, "utf-8");

    const first = await temporaryState.service.getPlanningSynchronizationStatus();
    const second = await temporaryState.service.getPlanningSynchronizationStatus();

    expect(first).toEqual({
      state: "neverSynced",
      synchronized: false,
      currentRfc: "RFC-0039",
      reason: "Main Planning has not been synchronized yet.",
      recommendedAction: "generateMainPlanningSync",
      expectedDocumentationSync: "pending",
      documentationSyncConsistent: true,
    });
    expect(second).toEqual(first);
    expect(saveSpy).not.toHaveBeenCalled();
    await expect(readFile(temporaryState.stateFilePath, "utf-8")).resolves.toBe(before);
  });

  it("returns current from the latest sync using append order, not timestamps", async () => {
    temporaryState = await createTemporaryState(createProjectStatus({
      releaseReadiness: {
        ...createDefaultReleaseReadiness(),
        documentationSync: "passed",
      },
      lifecycleHistory: [
        sync39,
        {
          ...sync39,
          id: "rfc-event-000002",
          timestamp: "2000-01-01T00:00:00.000Z",
        },
      ],
    }));

    await expect(temporaryState.service.getPlanningSynchronizationStatus()).resolves.toEqual({
      state: "current",
      synchronized: true,
      currentRfc: "RFC-0039",
      lastPlanningSyncEventId: "rfc-event-000002",
      lastPlanningSyncRfc: "RFC-0039",
      reason: "Main Planning reflects the latest lifecycle state.",
      recommendedAction: "none",
      expectedDocumentationSync: "passed",
      documentationSyncConsistent: true,
    });
  });

  it.each([
    ["completed", "RFC lifecycle changed after the latest Main Planning synchronization."],
    ["started", "RFC lifecycle changed after the latest Main Planning synchronization."],
  ] as const)("returns stale after %s", async (type, reason) => {
    const changingEvent: RfcLifecycleEvent = type === "completed"
      ? { ...sync39, id: "rfc-event-000002", type: "completed" }
      : {
          ...sync39,
          id: "rfc-event-000002",
          type: "started",
          rfc: "RFC-0040",
          phase: "Phase 2",
          release: "v0.9.0",
        };
    temporaryState = await createTemporaryState(createProjectStatus({
      currentRfc: type === "started" ? "RFC-0040" : "RFC-0039",
      lifecycleHistory: [sync39, changingEvent],
    }));

    await expect(temporaryState.service.getPlanningSynchronizationStatus()).resolves.toMatchObject({
      state: "stale",
      synchronized: false,
      latestRelevantEventId: "rfc-event-000002",
      latestRelevantEventType: type,
      reason,
      recommendedAction: "generateMainPlanningSync",
      expectedDocumentationSync: "pending",
    });
  });

  it("returns rollback-specific stale status and Preview does not alter it", async () => {
    const history: RfcLifecycleEvent[] = [
      { ...sync39, type: "completed" },
      { ...sync39, id: "rfc-event-000002", type: "started", rfc: "RFC-0040" },
      { ...sync39, id: "rfc-event-000003", type: "planningSynced", rfc: "RFC-0040" },
      {
        ...sync39,
        id: "rfc-event-000004",
        type: "rollbackCompleted",
        rfc: "RFC-0039",
        fromRfc: "RFC-0040",
      },
    ];
    temporaryState = await createTemporaryState(createProjectStatus({ lifecycleHistory: history }));

    const before = await temporaryState.service.getPlanningSynchronizationStatus();
    await temporaryState.service.previewCurrentRfcRollback();
    const after = await temporaryState.service.getPlanningSynchronizationStatus();

    expect(before).toMatchObject({
      state: "stale",
      latestRelevantEventType: "rollbackCompleted",
      reason: "RFC rollback occurred after the latest Main Planning synchronization.",
    });
    expect(after).toEqual(before);
  });

  it.each([
    ["passed", false],
    ["pending", true],
    ["failed", false],
  ] as const)("detects stale documentationSync=%s consistency", async (documentationSync, consistent) => {
    temporaryState = await createTemporaryState(createProjectStatus({
      releaseReadiness: { ...createDefaultReleaseReadiness(), documentationSync },
      lifecycleHistory: [sync39, { ...sync39, id: "rfc-event-000002", type: "completed" }],
    }));

    const result = await temporaryState.service.getPlanningSynchronizationStatus();
    expect(result.documentationSyncConsistent).toBe(consistent);
    expect(result.expectedDocumentationSync).toBe("pending");
    if (!consistent) expect(result.documentationSyncReason).toContain(`Documentation Sync is ${documentationSync}`);
  });

  it("explicit synchronization appends exactly one event and restores current status", async () => {
    temporaryState = await createTemporaryState(createProjectStatus({
      lifecycleHistory: [sync39, { ...sync39, id: "rfc-event-000002", type: "completed" }],
    }));
    const beforeHistory = structuredClone((await temporaryState.repository.load()).lifecycleHistory);
    const saveSpy = vi.spyOn(temporaryState.repository, "save");

    const planning = await temporaryState.service.generateMainPlanningSync();
    const status = await temporaryState.service.getPlanningSynchronizationStatus();

    expect(saveSpy).toHaveBeenCalledTimes(1);
    expect(planning.lifecycleHistory.slice(0, -1)).toEqual(beforeHistory);
    expect(planning.lifecycleHistory.at(-1)).toMatchObject({
      id: "rfc-event-000003",
      type: "planningSynced",
    });
    expect(status).toMatchObject({ state: "current", synchronized: true });
  });

  it("actual rollback makes a previously current Planning state stale", async () => {
    temporaryState = await createTemporaryState(createProjectStatus({
      currentRfc: "RFC-0040",
      lifecycleHistory: [
        { ...sync39, type: "completed" },
        { ...sync39, id: "rfc-event-000002", type: "started", rfc: "RFC-0040" },
        { ...sync39, id: "rfc-event-000003", type: "planningSynced", rfc: "RFC-0040" },
      ],
    }));
    await expect(temporaryState.service.getPlanningSynchronizationStatus()).resolves.toMatchObject({
      state: "current",
    });

    await temporaryState.service.rollbackCurrentRfc();

    await expect(temporaryState.service.getPlanningSynchronizationStatus()).resolves.toMatchObject({
      state: "stale",
      latestRelevantEventType: "rollbackCompleted",
    });
  });

  it("completion and start make Planning stale until explicit synchronization", async () => {
    temporaryState = await createTemporaryState(createProjectStatus({ lifecycleHistory: [sync39] }));

    await temporaryState.service.markCurrentRfcCompleted();
    await expect(temporaryState.service.getPlanningSynchronizationStatus()).resolves.toMatchObject({
      state: "stale",
      latestRelevantEventType: "completed",
    });
    await temporaryState.service.startNextRfc({ nextRfc: "RFC-0040" });
    await expect(temporaryState.service.getPlanningSynchronizationStatus()).resolves.toMatchObject({
      state: "stale",
      latestRelevantEventType: "started",
    });
    await temporaryState.service.generateMainPlanningSync();
    await expect(temporaryState.service.getPlanningSynchronizationStatus()).resolves.toMatchObject({
      state: "current",
      lastPlanningSyncRfc: "RFC-0040",
    });
  });

  it("adds planning status to guidance without replacing its primary action", async () => {
    temporaryState = await createTemporaryState(createProjectStatus({ lifecycleHistory: [sync39] }));

    await expect(temporaryState.service.getRfcLifecycleGuidance()).resolves.toMatchObject({
      nextAction: "markCurrentRfcCompleted",
      planningSynchronizationState: "current",
      planningSynchronizationRequired: false,
    });
  });
});
