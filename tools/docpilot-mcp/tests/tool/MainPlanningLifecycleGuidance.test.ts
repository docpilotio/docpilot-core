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

  it("adds in-progress guidance to the existing Tool output without writes", async () => {
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
    });
    expect(result.content).toEqual([
      expect.objectContaining({
        text: expect.stringContaining("## RFC Lifecycle"),
      }),
    ]);
    expect(markSpy).not.toHaveBeenCalled();
    expect(startSpy).not.toHaveBeenCalled();
    await expect(readFile(temporaryState!.stateFilePath, "utf-8")).resolves.toBe(
      before,
    );
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
    await expect(readFile(temporaryState!.stateFilePath, "utf-8")).resolves.toBe(
      before,
    );
  });
});
