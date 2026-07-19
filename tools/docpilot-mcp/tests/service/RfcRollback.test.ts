import { readFile } from "node:fs/promises";

import { afterEach, describe, expect, it, vi } from "vitest";

import { createDefaultReleaseReadiness } from "../../src/model/ProjectStatus.js";
import type { RfcLifecycleEvent } from "../../src/model/RfcLifecycleEvent.js";
import {
  createProjectStatus,
  createTemporaryState,
  type TemporaryState,
} from "../support/testState.js";

const baseHistory: RfcLifecycleEvent[] = [
  {
    id: "rfc-event-000001",
    type: "completed",
    rfc: "RFC-0042",
    phase: "Previous Phase",
    release: "v0.6.0",
    timestamp: "2026-07-19T10:00:00.000Z",
  },
  {
    id: "rfc-event-000002",
    type: "started",
    rfc: "RFC-0099",
    phase: "Current Phase",
    release: "v0.7.0",
    timestamp: "2026-07-19T10:01:00.000Z",
  },
];

describe("ProjectStatusService rollback", () => {
  let temporaryState: TemporaryState | undefined;

  afterEach(async () => {
    vi.useRealTimers();
    await temporaryState?.cleanup();
    temporaryState = undefined;
  });

  it.each([
    ["started", []],
    [
      "completed",
      [
        {
          id: "rfc-event-000003",
          type: "completed",
          rfc: "RFC-0099",
          phase: "Current Phase",
          release: "v0.7.0",
          timestamp: "2026-07-19T10:02:00.000Z",
        },
      ],
    ],
    [
      "planningSynced",
      [
        {
          id: "rfc-event-000003",
          type: "planningSynced",
          rfc: "RFC-0099",
          phase: "Current Phase",
          release: "v0.7.0",
          timestamp: "2026-07-19T10:02:00.000Z",
        },
      ],
    ],
  ] as const)("rolls back after %s evidence", async (_caseName, suffix) => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-07-19T11:00:00.000Z"));
    const lifecycleHistory = [...baseHistory, ...suffix] as RfcLifecycleEvent[];
    const status = createProjectStatus({
      phase: "Current Phase",
      currentRfc: "RFC-0099",
      release: "v0.7.0",
      completedRfcs: ["RFC-0042", "RFC-0099"],
      releaseReadiness: {
        ...createDefaultReleaseReadiness(),
        coreBuild: "passed",
        releaseCandidate: "failed",
      },
      lifecycleHistory,
    });
    temporaryState = await createTemporaryState(status);
    const saveSpy = vi.spyOn(temporaryState.repository, "save");
    const originalEvents = structuredClone(lifecycleHistory);

    const updated = await temporaryState.service.rollbackCurrentRfc();

    expect(updated).toMatchObject({
      phase: "Previous Phase",
      currentRfc: "RFC-0042",
      release: "v0.6.0",
      completedRfcs: status.completedRfcs,
      releaseReadiness: createDefaultReleaseReadiness(),
    });
    expect(updated.lifecycleHistory.slice(0, -1)).toEqual(originalEvents);
    expect(updated.lifecycleHistory.at(-1)).toEqual({
      id: `rfc-event-${(lifecycleHistory.length + 1).toString().padStart(6, "0")}`,
      type: "rollbackCompleted",
      rfc: "RFC-0042",
      fromRfc: "RFC-0099",
      phase: "Previous Phase",
      release: "v0.6.0",
      timestamp: "2026-07-19T11:00:00.000Z",
    });
    expect(saveSpy).toHaveBeenCalledTimes(1);
    await expect(temporaryState.repository.load()).resolves.toEqual(updated);
    await expect(
      temporaryState.service.getRfcLifecycleGuidance(),
    ).resolves.toMatchObject({
      state: "completed_waiting_next",
      nextAction: "startNextRfc",
    });
  });

  it.each([
    [
      "empty history",
      createProjectStatus({ currentRfc: "RFC-0099", lifecycleHistory: [] }),
      "Lifecycle history is empty",
    ],
    [
      "malformed current RFC",
      createProjectStatus({ currentRfc: "RFC-99", lifecycleHistory: baseHistory }),
      "current RFC must use the exact format",
    ],
    [
      "no previous transition",
      createProjectStatus({
        currentRfc: "RFC-0099",
        lifecycleHistory: [{ ...baseHistory[1]!, id: "rfc-event-000001" }],
      }),
      "does not contain a previous RFC transition",
    ],
    [
      "ambiguous started event",
      createProjectStatus({
        currentRfc: "RFC-0042",
        lifecycleHistory: [
          baseHistory[0]!,
          {
            ...baseHistory[0]!,
            id: "rfc-event-000002",
            type: "started",
          },
        ],
      }),
      "ambiguous started event",
    ],
    [
      "state conflict",
      createProjectStatus({
        currentRfc: "RFC-0100",
        lifecycleHistory: baseHistory,
      }),
      "Current project state conflicts with lifecycle history",
    ],
    [
      "repeated rollback",
      createProjectStatus({
        currentRfc: "RFC-0042",
        lifecycleHistory: [
          ...baseHistory,
          {
            id: "rfc-event-000003",
            type: "rollbackCompleted",
            rfc: "RFC-0042",
            fromRfc: "RFC-0099",
            phase: "Previous Phase",
            release: "v0.6.0",
            timestamp: "2026-07-19T11:00:00.000Z",
          },
        ],
      }),
      "Repeated rollback is not supported",
    ],
  ] as const)("rejects %s without persistence", async (_caseName, status, error) => {
    temporaryState = await createTemporaryState(status);
    const before = await readFile(temporaryState.stateFilePath, "utf-8");
    const saveSpy = vi.spyOn(temporaryState.repository, "save");

    await expect(temporaryState.service.rollbackCurrentRfc()).rejects.toThrow(
      error,
    );

    expect(saveSpy).not.toHaveBeenCalled();
    await expect(readFile(temporaryState.stateFilePath, "utf-8")).resolves.toBe(
      before,
    );
  });
});
