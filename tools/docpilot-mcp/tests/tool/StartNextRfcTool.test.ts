import { readFile } from "node:fs/promises";

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { afterEach, describe, expect, it, vi } from "vitest";

import { createDefaultReleaseReadiness } from "../../src/model/ProjectStatus.js";
import { registerStartNextRfcTool } from "../../src/tool/StartNextRfcTool.js";
import {
  connectTestClient,
  createProjectStatus,
  createTemporaryState,
  type TemporaryState,
} from "../support/testState.js";

describe("StartNextRfcTool", () => {
  let temporaryState: TemporaryState | undefined;
  let closeClient: (() => Promise<void>) | undefined;

  afterEach(async () => {
    await closeClient?.();
    await temporaryState?.cleanup();
    closeClient = undefined;
    temporaryState = undefined;
  });

  async function setup(completed = true) {
    temporaryState = await createTemporaryState(
      createProjectStatus({
        completedRfcs: completed
          ? ["RFC-0037", "RFC-0038", "RFC-0039"]
          : ["RFC-0037", "RFC-0038"],
      }),
    );
    const startSpy = vi.spyOn(temporaryState.service, "startNextRfc");
    const server = new McpServer({ name: "tool-test", version: "0.0.0" });
    registerStartNextRfcTool(server, temporaryState.service);
    const connection = await connectTestClient(server);
    closeClient = connection.close;

    return { ...connection, startSpy };
  }

  it("registers the exact name, calls the Service, and returns updated state", async () => {
    const { client, startSpy } = await setup();

    expect((await client.listTools()).tools.map(({ name }) => name)).toEqual([
      "startNextRfc",
    ]);
    const result = await client.callTool({
      name: "startNextRfc",
      arguments: {
        nextRfc: "RFC-0040",
        phase: "Phase 2",
        release: "v0.7",
      },
    });

    expect(startSpy).toHaveBeenCalledWith({
      nextRfc: "RFC-0040",
      phase: "Phase 2",
      release: "v0.7",
    });
    expect(result.isError).not.toBe(true);
    expect(result.content).toEqual([
      { type: "text", text: "Started RFC-0040." },
    ]);
    expect(result.structuredContent).toMatchObject({
      currentRfc: "RFC-0040",
      completedRfcs: ["RFC-0037", "RFC-0038", "RFC-0039"],
      releaseReadiness: createDefaultReleaseReadiness(),
    });
  });

  it("maps Service validation failures to the existing Tool error style", async () => {
    const { client } = await setup(false);

    const result = await client.callTool({
      name: "startNextRfc",
      arguments: { nextRfc: "RFC-0040" },
    });

    expect(result.isError).toBe(true);
    expect(result.content).toEqual([
      expect.objectContaining({
        text: expect.stringContaining(
          "Failed to start the next RFC: The current RFC must be completed",
        ),
      }),
    ]);
  });

  it("rejects unknown fields at the Tool boundary without modifying state", async () => {
    const { client, startSpy } = await setup();
    const before = await readFile(temporaryState!.stateFilePath, "utf-8");

    const result = await client.callTool({
      name: "startNextRfc",
      arguments: { nextRfc: "RFC-0040", unexpected: true },
    });

    expect(result.isError).toBe(true);
    expect(startSpy).not.toHaveBeenCalled();
    await expect(readFile(temporaryState!.stateFilePath, "utf-8")).resolves.toBe(
      before,
    );
  });

  it("does not import the Repository directly", async () => {
    const source = await readFile(
      new URL("../../src/tool/StartNextRfcTool.ts", import.meta.url),
      "utf-8",
    );

    expect(source).toContain("service.startNextRfc");
    expect(source).not.toContain("ProjectStateRepository");
  });
});
