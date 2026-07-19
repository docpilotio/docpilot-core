import { readFile } from "node:fs/promises";

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { afterEach, describe, expect, it } from "vitest";

import { registerProjectDashboardResource } from "../../src/resource/ProjectDashboardResource.js";
import { createDefaultReleaseReadiness } from "../../src/model/ProjectStatus.js";
import {
  connectTestClient,
  createProjectStatus,
  createTemporaryState,
  type TemporaryState,
} from "../support/testState.js";

describe("ProjectDashboardResource", () => {
  let temporaryState: TemporaryState | undefined;
  let closeClient: (() => Promise<void>) | undefined;

  afterEach(async () => {
    await closeClient?.();
    await temporaryState?.cleanup();
    closeClient = undefined;
    temporaryState = undefined;
  });

  it("returns deterministic persisted dashboard state without modifying it", async () => {
    const status = createProjectStatus({
      releaseReadiness: {
        ...createProjectStatus().releaseReadiness,
        coreBuild: "passed",
        coreTests: "failed",
      },
    });
    temporaryState = await createTemporaryState(status);
    const server = new McpServer({ name: "resource-test", version: "0.0.0" });
    registerProjectDashboardResource(server, temporaryState.service);
    const connection = await connectTestClient(server);
    closeClient = connection.close;
    const before = await readFile(temporaryState.stateFilePath, "utf-8");

    const result = await connection.client.readResource({
      uri: "docpilot://project/dashboard",
    });
    const content = result.contents[0];
    expect(content).toBeDefined();
    expect(content?.mimeType).toBe("application/json");
    expect(content?.uri).toBe("docpilot://project/dashboard");
    expect("text" in content!).toBe(true);
    const text = "text" in content! ? content.text : "";
    const dashboard = JSON.parse(text) as Record<string, unknown>;

    expect(dashboard).toEqual({
      project: status.project,
      phase: status.phase,
      currentRfc: status.currentRfc,
      release: status.release,
      completedCount: status.completedRfcs.length,
      completedRfcs: status.completedRfcs,
      releaseReadiness: status.releaseReadiness,
      lifecycleGuidance: {
        state: "in_progress",
        nextAction: "markCurrentRfcCompleted",
        reason: "Current RFC has not been marked completed.",
      },
      lifecycleHistory: [],
    });
    expect(text).toBe(`${JSON.stringify(dashboard, null, 2)}`);
    await expect(readFile(temporaryState.stateFilePath, "utf-8")).resolves.toBe(
      before,
    );
  });

  it("depends on the Service rather than importing the Repository", async () => {
    const source = await readFile(
      new URL("../../src/resource/ProjectDashboardResource.ts", import.meta.url),
      "utf-8",
    );

    expect(source).toContain("service.getProjectStatus()");
    expect(source).not.toContain("ProjectStateRepository");
    expect(source).not.toContain('coreBuild: "pending"');
  });

  it("reflects a started RFC and its reset readiness", async () => {
    const status = createProjectStatus({
      completedRfcs: ["RFC-0037", "RFC-0038", "RFC-0039"],
      releaseReadiness: {
        ...createDefaultReleaseReadiness(),
        coreBuild: "passed",
        releaseCandidate: "failed",
      },
    });
    temporaryState = await createTemporaryState(status);
    await temporaryState.service.startNextRfc({ nextRfc: "RFC-0040" });
    const server = new McpServer({ name: "resource-test", version: "0.0.0" });
    registerProjectDashboardResource(server, temporaryState.service);
    const connection = await connectTestClient(server);
    closeClient = connection.close;

    const result = await connection.client.readResource({
      uri: "docpilot://project/dashboard",
    });
    const content = result.contents[0];
    const text = content !== undefined && "text" in content ? content.text : "";
    const dashboard = JSON.parse(text) as Record<string, unknown>;

    expect(dashboard.currentRfc).toBe("RFC-0040");
    expect(dashboard.completedRfcs).toEqual(status.completedRfcs);
    expect(dashboard.releaseReadiness).toEqual(
      createDefaultReleaseReadiness(),
    );
  });

  it("reflects mark-only completion before the next RFC starts", async () => {
    const readiness = {
      ...createDefaultReleaseReadiness(),
      coreBuild: "passed" as const,
      releaseCandidate: "failed" as const,
    };
    temporaryState = await createTemporaryState(
      createProjectStatus({ releaseReadiness: readiness }),
    );
    await temporaryState.service.markCurrentRfcCompleted();
    const server = new McpServer({ name: "resource-test", version: "0.0.0" });
    registerProjectDashboardResource(server, temporaryState.service);
    const connection = await connectTestClient(server);
    closeClient = connection.close;

    const markedResult = await connection.client.readResource({
      uri: "docpilot://project/dashboard",
    });
    const markedContent = markedResult.contents[0];
    const markedText =
      markedContent !== undefined && "text" in markedContent
        ? markedContent.text
        : "";
    const markedDashboard = JSON.parse(markedText) as Record<string, unknown>;

    expect(markedDashboard.currentRfc).toBe("RFC-0039");
    expect(markedDashboard.completedRfcs).toEqual([
      "RFC-0037",
      "RFC-0038",
      "RFC-0039",
    ]);
    expect(markedDashboard.releaseReadiness).toEqual(readiness);
    expect(markedDashboard.lifecycleHistory).toEqual([
      expect.objectContaining({
        id: "rfc-event-000001",
        type: "completed",
        rfc: "RFC-0039",
      }),
    ]);
    expect(markedDashboard.lifecycleGuidance).toEqual({
      state: "completed_waiting_next",
      nextAction: "startNextRfc",
      reason: "Current RFC is completed and the next RFC may now be started.",
    });

    await temporaryState.service.startNextRfc({ nextRfc: "RFC-0040" });
    const startedResult = await connection.client.readResource({
      uri: "docpilot://project/dashboard",
    });
    const startedContent = startedResult.contents[0];
    const startedText =
      startedContent !== undefined && "text" in startedContent
        ? startedContent.text
        : "";
    const startedDashboard = JSON.parse(startedText) as Record<string, unknown>;

    expect(startedDashboard.currentRfc).toBe("RFC-0040");
    expect(startedDashboard.completedRfcs).toEqual(
      markedDashboard.completedRfcs,
    );
    expect(startedDashboard.releaseReadiness).toEqual(
      createDefaultReleaseReadiness(),
    );
    expect(startedDashboard.lifecycleGuidance).toEqual({
      state: "in_progress",
      nextAction: "markCurrentRfcCompleted",
      reason: "Current RFC has not been marked completed.",
    });
    expect(startedDashboard.lifecycleHistory).toEqual([
      expect.objectContaining({ type: "completed", rfc: "RFC-0039" }),
      expect.objectContaining({ type: "started", rfc: "RFC-0040" }),
    ]);
  });

  it("reflects a persisted rollback without rewriting history on read", async () => {
    const readiness = {
      ...createDefaultReleaseReadiness(),
      coreBuild: "passed" as const,
    };
    temporaryState = await createTemporaryState(
      createProjectStatus({
        currentRfc: "RFC-0040",
        completedRfcs: ["RFC-0039"],
        releaseReadiness: readiness,
        lifecycleHistory: [
          {
            id: "rfc-event-000001",
            type: "completed",
            rfc: "RFC-0039",
            phase: "Phase 1 — MVP",
            release: "v0.6 MVP",
            timestamp: "2026-01-01T00:00:00.000Z",
          },
          {
            id: "rfc-event-000002",
            type: "started",
            rfc: "RFC-0040",
            phase: "Phase 2",
            release: "v0.7 MVP",
            timestamp: "2026-01-02T00:00:00.000Z",
          },
        ],
      }),
    );
    await temporaryState.service.rollbackCurrentRfc();
    const server = new McpServer({ name: "resource-test", version: "0.0.0" });
    registerProjectDashboardResource(server, temporaryState.service);
    const connection = await connectTestClient(server);
    closeClient = connection.close;
    const before = await readFile(temporaryState.stateFilePath, "utf-8");

    const result = await connection.client.readResource({
      uri: "docpilot://project/dashboard",
    });
    const content = result.contents[0];
    const text = content !== undefined && "text" in content ? content.text : "";
    const dashboard = JSON.parse(text) as Record<string, unknown>;

    expect(dashboard.currentRfc).toBe("RFC-0039");
    expect(dashboard.completedRfcs).toEqual(["RFC-0039"]);
    expect(dashboard.releaseReadiness).toEqual(createDefaultReleaseReadiness());
    expect(dashboard.lifecycleGuidance).toMatchObject({
      state: "completed_waiting_next",
      nextAction: "startNextRfc",
    });
    expect(dashboard.lifecycleHistory).toEqual([
      expect.objectContaining({ id: "rfc-event-000001", type: "completed" }),
      expect.objectContaining({ id: "rfc-event-000002", type: "started" }),
      expect.objectContaining({
        id: "rfc-event-000003",
        type: "rollbackCompleted",
        rfc: "RFC-0039",
        fromRfc: "RFC-0040",
      }),
    ]);
    await expect(readFile(temporaryState.stateFilePath, "utf-8")).resolves.toBe(
      before,
    );
  });
});
