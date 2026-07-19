import { access, readFile } from "node:fs/promises";

import { afterEach, describe, expect, it } from "vitest";

import { createDefaultReleaseReadiness } from "../../src/model/ProjectStatus.js";
import {
  createProjectStatus,
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
});
