import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { afterEach, describe, expect, it } from "vitest";

import { registerGenerateMainPlanningSyncPrompt } from "../../src/prompt/GenerateMainPlanningSyncPrompt.js";
import { registerProjectStatusResource } from "../../src/resource/ProjectStatusResource.js";
import { createServer } from "../../src/server.js";
import { registerCompleteCurrentRfcTool } from "../../src/tool/CompleteCurrentRfcTool.js";
import { registerGenerateMainPlanningSyncTool } from "../../src/tool/GenerateMainPlanningSyncTool.js";
import { registerGetCurrentRfcTool } from "../../src/tool/GetCurrentRfcTool.js";
import { registerGetProjectStatusTool } from "../../src/tool/GetProjectStatusTool.js";
import { registerListCompletedRfcsTool } from "../../src/tool/ListCompletedRfcsTool.js";
import {
  connectTestClient,
  createProjectStatus,
  createTemporaryState,
  type TemporaryState,
} from "../support/testState.js";

describe("server registration and existing behavior", () => {
  let temporaryState: TemporaryState | undefined;
  const closers: Array<() => Promise<void>> = [];

  afterEach(async () => {
    for (const close of closers.reverse()) {
      await close();
    }
    closers.length = 0;
    await temporaryState?.cleanup();
    temporaryState = undefined;
  });

  it("registers every existing Tool, Resource, and Prompt", async () => {
    const connection = await connectTestClient(createServer());
    closers.push(connection.close);

    const tools = (await connection.client.listTools()).tools.map(
      ({ name }) => name,
    );
    const resources = (await connection.client.listResources()).resources.map(
      ({ uri }) => uri,
    );
    const prompts = (await connection.client.listPrompts()).prompts.map(
      ({ name }) => name,
    );

    expect(tools).toEqual([
      "getProjectStatus",
      "getCurrentRfc",
      "completeCurrentRfc",
      "markCurrentRfcCompleted",
      "rollbackCurrentRfc",
      "previewCurrentRfcRollback",
      "generateMainPlanningSync",
      "listCompletedRfcs",
      "updateProjectStatus",
      "updateReleaseReadiness",
      "startNextRfc",
    ]);
    expect(resources).toEqual([
      "docpilot://project/status",
      "docpilot://project/dashboard",
    ]);
    expect(prompts).toEqual(["generateMainPlanningSync"]);
  });

  it("smoke-tests existing Tools, the status Resource, and planning Prompt", async () => {
    temporaryState = await createTemporaryState(createProjectStatus());
    const server = new McpServer({ name: "smoke-test", version: "0.0.0" });
    registerGetProjectStatusTool(server, temporaryState.service);
    registerGetCurrentRfcTool(server, temporaryState.service);
    registerListCompletedRfcsTool(server, temporaryState.service);
    registerCompleteCurrentRfcTool(server, temporaryState.service);
    registerGenerateMainPlanningSyncTool(server, temporaryState.service);
    registerProjectStatusResource(server, temporaryState.service);
    registerGenerateMainPlanningSyncPrompt(server, temporaryState.service);
    const connection = await connectTestClient(server);
    closers.push(connection.close);

    for (const name of [
      "getProjectStatus",
      "getCurrentRfc",
      "listCompletedRfcs",
      "generateMainPlanningSync",
    ]) {
      const result = await connection.client.callTool({ name, arguments: {} });
      expect(result.isError).not.toBe(true);
    }
    const missingCompletionInput = await connection.client.callTool({
      name: "completeCurrentRfc",
      arguments: {},
    });
    expect(missingCompletionInput.isError).toBe(true);
    const completion = await connection.client.callTool({
      name: "completeCurrentRfc",
      arguments: { nextRfc: "RFC-0040" },
    });
    expect(completion.isError).not.toBe(true);

    const resource = await connection.client.readResource({
      uri: "docpilot://project/status",
    });
    expect(resource.contents[0]?.mimeType).toBe("application/json");
    const prompt = await connection.client.getPrompt({
      name: "generateMainPlanningSync",
      arguments: { completedWork: "Done", nextWork: "Next" },
    });
    expect(prompt.messages).toHaveLength(1);
  });
});
