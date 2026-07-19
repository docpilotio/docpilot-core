import { readFile } from "node:fs/promises";

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { afterEach, describe, expect, it, vi } from "vitest";

import { registerGetPlanningSynchronizationStatusTool } from "../../src/tool/GetPlanningSynchronizationStatusTool.js";
import {
  connectTestClient,
  createProjectStatus,
  createTemporaryState,
  type TemporaryState,
} from "../support/testState.js";

describe("GetPlanningSynchronizationStatusTool", () => {
  let temporaryState: TemporaryState | undefined;
  let closeClient: (() => Promise<void>) | undefined;

  afterEach(async () => {
    await closeClient?.();
    await temporaryState?.cleanup();
    closeClient = undefined;
    temporaryState = undefined;
  });

  async function setup() {
    temporaryState = await createTemporaryState(createProjectStatus());
    const statusSpy = vi.spyOn(temporaryState.service, "getPlanningSynchronizationStatus");
    const server = new McpServer({ name: "sync-status-test", version: "0.0.0" });
    registerGetPlanningSynchronizationStatusTool(server, temporaryState.service);
    const connection = await connectTestClient(server);
    closeClient = connection.close;
    return { ...connection, statusSpy };
  }

  it("accepts strict empty input and delegates to the Service without writes", async () => {
    const { client, statusSpy } = await setup();
    const before = await readFile(temporaryState!.stateFilePath, "utf-8");

    expect((await client.listTools()).tools.map(({ name }) => name)).toEqual([
      "getPlanningSynchronizationStatus",
    ]);
    const result = await client.callTool({
      name: "getPlanningSynchronizationStatus",
      arguments: {},
    });

    expect(statusSpy).toHaveBeenCalledOnce();
    expect(result.structuredContent).toMatchObject({
      state: "neverSynced",
      recommendedAction: "generateMainPlanningSync",
    });
    await expect(readFile(temporaryState!.stateFilePath, "utf-8")).resolves.toBe(before);
  });

  it("rejects unknown input before calling the Service", async () => {
    const { client, statusSpy } = await setup();
    const result = await client.callTool({
      name: "getPlanningSynchronizationStatus",
      arguments: { refresh: true },
    });
    expect(result.isError).toBe(true);
    expect(statusSpy).not.toHaveBeenCalled();
  });

  it("does not import the Repository", async () => {
    const source = await readFile(
      new URL("../../src/tool/GetPlanningSynchronizationStatusTool.ts", import.meta.url),
      "utf-8",
    );
    expect(source).toContain("service.getPlanningSynchronizationStatus");
    expect(source).not.toContain("ProjectStateRepository");
  });
});
