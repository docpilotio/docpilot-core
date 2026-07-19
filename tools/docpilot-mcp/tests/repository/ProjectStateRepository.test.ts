import { access, readFile } from "node:fs/promises";

import { afterEach, describe, expect, it } from "vitest";

import { createDefaultReleaseReadiness } from "../../src/model/ProjectStatus.js";
import {
  createProjectStatus,
  createRfcHandoff,
  createTemporaryState,
  type TemporaryState,
} from "../support/testState.js";

describe("ProjectStateRepository", () => {
  let temporaryState: TemporaryState | undefined;

  afterEach(async () => {
    await temporaryState?.cleanup();
    temporaryState = undefined;
  });

  it("creates and reloads a new state file without data loss", async () => {
    temporaryState = await createTemporaryState();
    const status = createProjectStatus();

    await temporaryState.repository.save(status);

    await expect(access(temporaryState.stateFilePath)).resolves.toBeUndefined();
    await expect(temporaryState.repository.load()).resolves.toEqual(status);
  });

  it("loads a legacy state with all readiness fields defaulted to pending", async () => {
    const {
      releaseReadiness: _releaseReadiness,
      lifecycleHistory: _lifecycleHistory,
      ...legacyStatus
    } = createProjectStatus();
    temporaryState = await createTemporaryState(legacyStatus);

    const loaded = await temporaryState.repository.load();

    expect(loaded.releaseReadiness).toEqual(createDefaultReleaseReadiness());
    expect(loaded.lifecycleHistory).toEqual([]);
  });

  it("defaults missing individual readiness fields", async () => {
    temporaryState = await createTemporaryState({
      ...createProjectStatus(),
      releaseReadiness: {
        coreBuild: "passed",
        coreTests: "failed",
      },
    });

    const loaded = await temporaryState.repository.load();

    expect(loaded.releaseReadiness).toEqual({
      ...createDefaultReleaseReadiness(),
      coreBuild: "passed",
      coreTests: "failed",
    });
  });

  it("loads every valid readiness value", async () => {
    const readiness = {
      ...createDefaultReleaseReadiness(),
      coreBuild: "passed" as const,
      coreTests: "failed" as const,
    };
    temporaryState = await createTemporaryState(
      createProjectStatus({ releaseReadiness: readiness }),
    );

    await expect(temporaryState.repository.load()).resolves.toMatchObject({
      releaseReadiness: readiness,
    });
  });

  it("rejects invalid readiness values with a deterministic error", async () => {
    temporaryState = await createTemporaryState({
      ...createProjectStatus(),
      releaseReadiness: { coreBuild: "unknown" },
    });

    await expect(temporaryState.repository.load()).rejects.toThrow(
      "project-state.json contains an invalid releaseReadiness value for coreBuild.",
    );
  });

  it("rejects unknown readiness fields", async () => {
    temporaryState = await createTemporaryState({
      ...createProjectStatus(),
      releaseReadiness: {
        ...createDefaultReleaseReadiness(),
        unexpected: "pending",
      },
    });

    await expect(temporaryState.repository.load()).rejects.toThrow(
      "project-state.json contains an unknown releaseReadiness field: unexpected.",
    );
  });

  it("serializes all fields and readiness fields in deterministic order", async () => {
    temporaryState = await createTemporaryState();
    await temporaryState.repository.save(createProjectStatus());

    const serialized = await readFile(temporaryState.stateFilePath, "utf-8");
    const parsed = JSON.parse(serialized) as Record<string, unknown>;
    const readiness = parsed.releaseReadiness as Record<string, unknown>;

    expect(Object.keys(parsed)).toEqual([
      "project",
      "phase",
      "currentRfc",
      "release",
      "completedRfcs",
      "releaseReadiness",
      "lifecycleHistory",
    ]);
    expect(Object.keys(readiness)).toEqual([
      "coreBuild",
      "coreTests",
      "cli",
      "incremental",
      "reviewWorkflow",
      "architectureSamplesValidation",
      "documentationSync",
      "releaseCandidate",
    ]);
  });

  it("does not rewrite a legacy state while reading it", async () => {
    const {
      releaseReadiness: _releaseReadiness,
      lifecycleHistory: _lifecycleHistory,
      ...legacyStatus
    } = createProjectStatus();
    temporaryState = await createTemporaryState(legacyStatus);
    const before = await readFile(temporaryState.stateFilePath, "utf-8");

    await temporaryState.repository.load();

    await expect(readFile(temporaryState.stateFilePath, "utf-8")).resolves.toBe(
      before,
    );
  });

  it("round-trips lifecycle events with stable serialization order", async () => {
    const lifecycleHistory = [
      {
        id: "rfc-event-000001",
        type: "started" as const,
        rfc: "RFC-0039",
        phase: "Phase 1 — MVP",
        release: "v0.6 MVP",
        timestamp: "2026-07-19T10:00:00.000Z",
      },
    ];
    const status = createProjectStatus({ lifecycleHistory });
    temporaryState = await createTemporaryState();

    await temporaryState.repository.save(status);

    await expect(temporaryState.repository.load()).resolves.toEqual(status);
    const serialized = JSON.parse(
      await readFile(temporaryState.stateFilePath, "utf-8"),
    ) as { lifecycleHistory: Array<Record<string, unknown>> };
    expect(Object.keys(serialized.lifecycleHistory[0]!)).toEqual([
      "id",
      "type",
      "rfc",
      "phase",
      "release",
      "timestamp",
    ]);
  });

  it("rejects invalid lifecycle events deterministically", async () => {
    temporaryState = await createTemporaryState({
      ...createProjectStatus(),
      lifecycleHistory: [
        {
          id: "rfc-event-000001",
          type: "unknown",
          rfc: "RFC-0039",
          phase: "Phase 1 — MVP",
          release: "v0.6 MVP",
          timestamp: "not-a-timestamp",
        },
      ],
    });

    await expect(temporaryState.repository.load()).rejects.toThrow(
      "project-state.json contains an invalid lifecycleHistory event at index 0.",
    );
  });

  it("round-trips rollback events with stable optional-field ordering", async () => {
    const rollbackEvent = {
      id: "rfc-event-000001",
      type: "rollbackCompleted" as const,
      rfc: "RFC-0047",
      fromRfc: "RFC-0048",
      phase: "Phase 1",
      release: "v0.7.0",
      timestamp: "2026-07-19T11:00:00.000Z",
    };
    const status = createProjectStatus({ lifecycleHistory: [rollbackEvent] });
    temporaryState = await createTemporaryState();

    await temporaryState.repository.save(status);

    await expect(temporaryState.repository.load()).resolves.toEqual(status);
    const serialized = JSON.parse(
      await readFile(temporaryState.stateFilePath, "utf-8"),
    ) as { lifecycleHistory: Array<Record<string, unknown>> };
    expect(Object.keys(serialized.lifecycleHistory[0]!)).toEqual([
      "id",
      "type",
      "rfc",
      "fromRfc",
      "phase",
      "release",
      "timestamp",
    ]);
  });

  it("rejects a non-sequential lifecycle event ID", async () => {
    temporaryState = await createTemporaryState({
      ...createProjectStatus(),
      lifecycleHistory: [
        {
          id: "rfc-event-000002",
          type: "started",
          rfc: "RFC-0039",
          phase: "Phase 1 — MVP",
          release: "v0.6 MVP",
          timestamp: "2026-07-19T10:00:00.000Z",
        },
      ],
    });

    await expect(temporaryState.repository.load()).rejects.toThrow(
      "project-state.json contains an invalid lifecycleHistory event ID at index 0; expected rfc-event-000001.",
    );
  });

  it("round-trips an additive Pending Handoff and keeps legacy state compatible", async () => {
    temporaryState = await createTemporaryState(createProjectStatus());
    await expect(temporaryState.repository.load()).resolves.not.toHaveProperty("pendingRfcHandoff");
    const status = createProjectStatus({ pendingRfcHandoff: createRfcHandoff() });
    await temporaryState.repository.save(status);
    await expect(temporaryState.repository.load()).resolves.toEqual(status);
  });

  it("rejects an unsupported persisted Handoff schemaVersion", async () => {
    temporaryState = await createTemporaryState({
      ...createProjectStatus(),
      pendingRfcHandoff: createRfcHandoff({ schemaVersion: "2.0" }),
    });
    await expect(temporaryState.repository.load()).rejects.toThrow(
      "unsupported pendingRfcHandoff schemaVersion: 2.0",
    );
  });
});
