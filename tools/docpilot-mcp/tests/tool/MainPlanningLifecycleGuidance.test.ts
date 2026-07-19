import { readFile } from "node:fs/promises";

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { afterEach, describe, expect, it, vi } from "vitest";

import { registerGenerateMainPlanningSyncPrompt } from "../../src/prompt/GenerateMainPlanningSyncPrompt.js";
import { registerGenerateMainPlanningSyncTool } from "../../src/tool/GenerateMainPlanningSyncTool.js";
import {
  connectTestClient,
  createProjectStatus,
  createTemporaryState,
  type TemporaryState,
} from "../support/testState.js";

describe("Main Planning lifecycle guidance", () => {
  let temporaryState: TemporaryState | undefined;
  let closeClient: (() => Promise<void>) | undefined;

  afterEach(async () => {
    await closeClient?.();
    await temporaryState?.cleanup();
    closeClient = undefined;
    temporaryState = undefined;
  });

  async function setup(completed = false) {
    temporaryState = await createTemporaryState(
      createProjectStatus({
        completedRfcs: completed
          ? ["RFC-0037", "RFC-0038", "RFC-0039"]
          : ["RFC-0037", "RFC-0038"],
      }),
    );
    const markSpy = vi.spyOn(
      temporaryState.service,
      "markCurrentRfcCompleted",
    );
    const startSpy = vi.spyOn(temporaryState.service, "startNextRfc");
    const server = new McpServer({ name: "planning-test", version: "0.0.0" });
    registerGenerateMainPlanningSyncTool(server, temporaryState.service);
    registerGenerateMainPlanningSyncPrompt(server, temporaryState.service);
    const connection = await connectTestClient(server);
    closeClient = connection.close;

    return { ...connection, markSpy, startSpy };
  }

  it("adds guidance and appends a planningSynced event without workflow execution", async () => {
    const { client, markSpy, startSpy } = await setup();
    const before = await readFile(temporaryState!.stateFilePath, "utf-8");

    expect((await client.listTools()).tools.map(({ name }) => name)).toEqual([
      "generateMainPlanningSync",
    ]);
    const result = await client.callTool({
      name: "generateMainPlanningSync",
      arguments: {},
    });

    expect(result.structuredContent).toMatchObject({
      project: "DocPilot",
      currentRfc: "RFC-0039",
      completedRfcs: ["RFC-0037", "RFC-0038"],
      completedCount: 2,
      lifecycleGuidance: {
        state: "in_progress",
        nextAction: "markCurrentRfcCompleted",
        reason: "Current RFC has not been marked completed.",
      },
      lifecycleHistory: [
        expect.objectContaining({
          id: "rfc-event-000001",
          type: "planningSynced",
          rfc: "RFC-0039",
        }),
      ],
      planningSynchronization: {
        state: "current",
        synchronized: true,
        recommendedAction: "none",
      },
    });
    expect(result.content).toEqual([
      expect.objectContaining({
        text: expect.stringContaining("## RFC Lifecycle"),
      }),
    ]);
    expect(result.content).toEqual([
      expect.objectContaining({
        text: expect.stringContaining("## Planning Synchronization"),
      }),
    ]);
    expect(result.content).toEqual([
      expect.objectContaining({
        text: expect.stringContaining("- Status: current"),
      }),
    ]);
    expect(result.content).toEqual([
      expect.objectContaining({
        text: expect.stringContaining("## RFC Lifecycle Timeline"),
      }),
    ]);
    expect(markSpy).not.toHaveBeenCalled();
    expect(startSpy).not.toHaveBeenCalled();
    const after = await readFile(temporaryState!.stateFilePath, "utf-8");
    expect(after).not.toBe(before);
    const persisted = JSON.parse(after) as {
      lifecycleHistory: Array<{ type: string }>;
    };
    expect(persisted.lifecycleHistory).toEqual([
      expect.objectContaining({ type: "planningSynced" }),
    ]);
  });

  it("recommends startNextRfc when the current RFC is completed", async () => {
    const { client } = await setup(true);

    const result = await client.callTool({
      name: "generateMainPlanningSync",
      arguments: {},
    });

    expect(result.structuredContent).toMatchObject({
      lifecycleGuidance: {
        state: "completed_waiting_next",
        nextAction: "startNextRfc",
      },
    });
  });

  it("adds identical deterministic guidance to the existing Prompt", async () => {
    const { client } = await setup();
    const before = await readFile(temporaryState!.stateFilePath, "utf-8");

    expect((await client.listPrompts()).prompts.map(({ name }) => name)).toEqual([
      "generateMainPlanningSync",
    ]);
    const argumentsValue = { completedWork: "Done", nextWork: "Next" };
    const first = await client.getPrompt({
      name: "generateMainPlanningSync",
      arguments: argumentsValue,
    });
    const second = await client.getPrompt({
      name: "generateMainPlanningSync",
      arguments: argumentsValue,
    });
    const content = first.messages[0]?.content;
    const text = content !== undefined && "text" in content ? content.text : "";

    expect(second).toEqual(first);
    expect(text).toContain("## RFC Lifecycle");
    expect(text).toContain("- State: `in_progress`");
    expect(text).toContain(
      "- Recommended Tool: `markCurrentRfcCompleted`",
    );
    expect(text).toContain("Done");
    expect(text).toContain("Next");
    expect(text).toContain("## Rollback Preview");
    expect(text).toContain("- Eligible: No");
    expect(text).toContain("## Planning Synchronization");
    expect(text).toContain("- Status: neverSynced");
    await expect(readFile(temporaryState!.stateFilePath, "utf-8")).resolves.toBe(
      before,
    );
  });

  it("renders rollback evidence in the structured history and timeline", async () => {
    temporaryState = await createTemporaryState(
      createProjectStatus({
        currentRfc: "RFC-0039",
        completedRfcs: ["RFC-0039"],
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
          {
            id: "rfc-event-000003",
            type: "rollbackCompleted",
            rfc: "RFC-0039",
            fromRfc: "RFC-0040",
            phase: "Phase 1 — MVP",
            release: "v0.6 MVP",
            timestamp: "2026-01-03T00:00:00.000Z",
          },
        ],
      }),
    );
    const server = new McpServer({ name: "planning-test", version: "0.0.0" });
    registerGenerateMainPlanningSyncTool(server, temporaryState.service);
    const connection = await connectTestClient(server);
    closeClient = connection.close;

    const result = await connection.client.callTool({
      name: "generateMainPlanningSync",
      arguments: {},
    });
    const text = result.content[0] !== undefined && "text" in result.content[0]
      ? result.content[0].text
      : "";

    expect(result.structuredContent).toMatchObject({
      currentRfc: "RFC-0039",
      lifecycleHistory: [
        expect.any(Object),
        expect.any(Object),
        expect.objectContaining({
          type: "rollbackCompleted",
          rfc: "RFC-0039",
          fromRfc: "RFC-0040",
        }),
        expect.objectContaining({ type: "planningSynced", rfc: "RFC-0039" }),
      ],
    });
    expect(text).toContain("Rolled back RFC-0040 → RFC-0039");
    expect(text).toContain("## Rollback Preview");
    expect(result.structuredContent).toMatchObject({
      rollbackPreview: {
        eligible: false,
        currentRfc: "RFC-0039",
        blockingReason: "Repeated rollback is not supported after the latest rollback event.",
      },
    });
  });

  it("adds an eligible Preview to structured and Markdown planning output", async () => {
    temporaryState = await createTemporaryState(
      createProjectStatus({
        phase: "Current Phase",
        currentRfc: "RFC-0040",
        release: "v0.8.0",
        lifecycleHistory: [
          {
            id: "rfc-event-000001", type: "completed", rfc: "RFC-0039",
            phase: "Target Phase", release: "v0.7.0", timestamp: "2026-01-01T00:00:00.000Z",
          },
          {
            id: "rfc-event-000002", type: "started", rfc: "RFC-0040",
            phase: "Current Phase", release: "v0.8.0", timestamp: "2026-01-02T00:00:00.000Z",
          },
        ],
      }),
    );
    const server = new McpServer({ name: "planning-test", version: "0.0.0" });
    registerGenerateMainPlanningSyncTool(server, temporaryState.service);
    const connection = await connectTestClient(server);
    closeClient = connection.close;

    const result = await connection.client.callTool({
      name: "generateMainPlanningSync",
      arguments: {},
    });
    const text = result.content[0] !== undefined && "text" in result.content[0]
      ? result.content[0].text
      : "";

    expect(result.structuredContent).toMatchObject({
      rollbackPreview: {
        eligible: true,
        currentRfc: "RFC-0040",
        targetRfc: "RFC-0039",
        targetPhase: "Target Phase",
        targetRelease: "v0.7.0",
      },
    });
    expect(text).toContain("## Rollback Preview");
    expect(text).toContain("- Eligible: Yes");
    expect(text).toContain("- Rollback Target: RFC-0039");
  });

  it("renders stale Planning Synchronization in the read-only Prompt", async () => {
    temporaryState = await createTemporaryState(
      createProjectStatus({
        lifecycleHistory: [
          {
            id: "rfc-event-000001", type: "planningSynced", rfc: "RFC-0039",
            phase: "Phase 1", release: "v0.8.0", timestamp: "2026-01-01T00:00:00.000Z",
          },
          {
            id: "rfc-event-000002", type: "completed", rfc: "RFC-0039",
            phase: "Phase 1", release: "v0.8.0", timestamp: "2026-01-02T00:00:00.000Z",
          },
        ],
      }),
    );
    const server = new McpServer({ name: "planning-test", version: "0.0.0" });
    registerGenerateMainPlanningSyncPrompt(server, temporaryState.service);
    const connection = await connectTestClient(server);
    closeClient = connection.close;
    const before = await readFile(temporaryState.stateFilePath, "utf-8");

    const result = await connection.client.getPrompt({
      name: "generateMainPlanningSync",
      arguments: {},
    });
    const content = result.messages[0]?.content;
    const text = content !== undefined && "text" in content ? content.text : "";

    expect(text).toContain("## Planning Synchronization");
    expect(text).toContain("- Status: stale");
    expect(text).toContain("- Recommended Action: generateMainPlanningSync");
    await expect(readFile(temporaryState.stateFilePath, "utf-8")).resolves.toBe(before);
  });
});
