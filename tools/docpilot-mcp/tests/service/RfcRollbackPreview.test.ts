import { readFile } from "node:fs/promises";

import { afterEach, describe, expect, it, vi } from "vitest";

import { createDefaultReleaseReadiness } from "../../src/model/ProjectStatus.js";
import type { ProjectStatus } from "../../src/model/ProjectStatus.js";
import {
  createProjectStatus,
  createTemporaryState,
  type TemporaryState,
} from "../support/testState.js";

const eligibleStatus = createProjectStatus({
  phase: "Current Phase",
  currentRfc: "RFC-0099",
  release: "v0.8.0",
  releaseReadiness: {
    ...createDefaultReleaseReadiness(),
    coreBuild: "passed",
    coreTests: "failed",
  },
  lifecycleHistory: [
    {
      id: "rfc-event-000001",
      type: "completed",
      rfc: "RFC-0042",
      phase: "Target Phase",
      release: "v0.7.0",
      timestamp: "2026-07-19T10:00:00.000Z",
    },
    {
      id: "rfc-event-000002",
      type: "started",
      rfc: "RFC-0099",
      phase: "Current Phase",
      release: "v0.8.0",
      timestamp: "2026-07-19T10:01:00.000Z",
    },
  ],
});

describe("ProjectStatusService rollback Preview", () => {
  let temporaryState: TemporaryState | undefined;

  afterEach(async () => {
    await temporaryState?.cleanup();
    temporaryState = undefined;
  });

  it("derives a deterministic eligible Preview without saves or event changes", async () => {
    temporaryState = await createTemporaryState(eligibleStatus);
    const saveSpy = vi.spyOn(temporaryState.repository, "save");
    const before = await readFile(temporaryState.stateFilePath, "utf-8");

    const first = await temporaryState.service.previewCurrentRfcRollback();
    const second = await temporaryState.service.previewCurrentRfcRollback();

    expect(first).toEqual({
      eligible: true,
      currentRfc: "RFC-0099",
      targetRfc: "RFC-0042",
      targetPhase: "Target Phase",
      targetRelease: "v0.7.0",
      readinessAfterRollback: createDefaultReleaseReadiness(),
    });
    expect(second).toEqual(first);
    expect(saveSpy).not.toHaveBeenCalled();
    await expect(readFile(temporaryState.stateFilePath, "utf-8")).resolves.toBe(before);
    await expect(temporaryState.repository.load()).resolves.toEqual(eligibleStatus);
  });

  it("matches the actual rollback result without consuming an event ID", async () => {
    temporaryState = await createTemporaryState(eligibleStatus);
    const preview = await temporaryState.service.previewCurrentRfcRollback();
    const updated = await temporaryState.service.rollbackCurrentRfc();

    expect(updated.currentRfc).toBe(preview.targetRfc);
    expect(updated.phase).toBe(preview.targetPhase);
    expect(updated.release).toBe(preview.targetRelease);
    expect(updated.releaseReadiness).toEqual(preview.readinessAfterRollback);
    expect(updated.lifecycleHistory.at(-1)).toMatchObject({
      id: "rfc-event-000003",
      type: "rollbackCompleted",
    });
  });

  it("reports the post-rollback repeated context deterministically", async () => {
    temporaryState = await createTemporaryState(eligibleStatus);
    await temporaryState.service.rollbackCurrentRfc();

    await expect(temporaryState.service.previewCurrentRfcRollback()).resolves.toEqual({
      eligible: false,
      currentRfc: "RFC-0042",
      blockingReason: "Repeated rollback is not supported after the latest rollback event.",
    });
  });

  it.each([
    ["empty history", createProjectStatus(), "Lifecycle history is empty"],
    [
      "ambiguous history",
      createProjectStatus({
        currentRfc: "RFC-0042",
        lifecycleHistory: [
          eligibleStatus.lifecycleHistory[0]!,
          {
            ...eligibleStatus.lifecycleHistory[0]!,
            id: "rfc-event-000002",
            type: "started",
          },
        ],
      }),
      "ambiguous started event",
    ],
    [
      "current state conflict",
      createProjectStatus({ currentRfc: "RFC-0100", lifecycleHistory: eligibleStatus.lifecycleHistory }),
      "Current project state conflicts with lifecycle history",
    ],
    [
      "malformed current RFC",
      createProjectStatus({ currentRfc: "RFC-99", lifecycleHistory: eligibleStatus.lifecycleHistory }),
      "current RFC must use the exact format",
    ],
  ] as const)("returns an ineligible Preview for %s", async (_name, status: ProjectStatus, reason) => {
    temporaryState = await createTemporaryState(status);
    const saveSpy = vi.spyOn(temporaryState.repository, "save");

    const preview = await temporaryState.service.previewCurrentRfcRollback();

    expect(preview).toEqual({
      eligible: false,
      currentRfc: status.currentRfc,
      blockingReason: expect.stringContaining(reason),
    });
    expect(saveSpy).not.toHaveBeenCalled();
  });
});
