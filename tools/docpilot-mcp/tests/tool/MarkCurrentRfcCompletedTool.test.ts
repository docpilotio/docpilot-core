import { readFile } from "node:fs/promises";

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { afterEach, describe, expect, it, vi } from "vitest";

import { createDefaultReleaseReadiness } from "../../src/model/ProjectStatus.js";
import { registerMarkCurrentRfcCompletedTool } from "../../src/tool/MarkCurrentRfcCompletedTool.js";
import {
  connectTestClient,
  createProjectStatus,
  createTemporaryState,
  type TemporaryState,
} from "../support/testState.js";

describe("MarkCurrentRfcCompletedTool", () => {
  let temporaryState: TemporaryState | undefined;
  let closeClient: (() => Promise<void>) | undefined;

  afterEach(async () => {
    await closeClient?.();
    await temporaryState?.cleanup();
    closeClient = undefined;
    temporaryState = undefined;
  });

  async function setup(alreadyCompleted = false) {
    const readiness = {
      ...createDefaultReleaseReadiness(),
      coreBuild: "passed" as const,
      releaseCandidate: "failed" as const,
    };
    temporaryState = await createTemporaryState(
      createProjectStatus({
        completedRfcs: alreadyCompleted
          ? ["RFC-0037", "RFC-0038", "RFC-0039"]
          : ["RFC-0037", "RFC-0038"],
        releaseReadiness: readiness,
      }),
    );
    const markSpy = vi.spyOn(
      temporaryState.service,
      "markCurrentRfcCompleted",
    );
    const server = new McpServer({ name: "tool-test", version: "0.0.0" });
    registerMarkCurrentRfcCompletedTool(server, temporaryState.service);
    const connection = await connectTestClient(server);
    closeClient = connection.close;

    return { ...connection, markSpy, readiness };
  }

  it("accepts strict empty input, calls the Service, and returns preserved state", async () => {
    const { client, markSpy, readiness } = await setup();

    expect((await client.listTools()).tools.map(({ name }) => name)).toEqual([
      "markCurrentRfcCompleted",
    ]);
    const result = await client.callTool({
      name: "markCurrentRfcCompleted",
      arguments: {},
    });

    expect(markSpy).toHaveBeenCalledOnce();
    expect(result.isError).not.toBe(true);
    expect(result.content).toEqual([
      { type: "text", text: "Marked RFC-0039 completed." },
    ]);
    expect(result.structuredContent).toMatchObject({
      currentRfc: "RFC-0039",
      completedRfcs: ["RFC-0037", "RFC-0038", "RFC-0039"],
      releaseReadiness: readiness,
    });
  });

  it("rejects unknown input before calling the Service", async () => {
    const { client, markSpy } = await setup();
    const before = await readFile(temporaryState!.stateFilePath, "utf-8");

    const result = await client.callTool({
      name: "markCurrentRfcCompleted",
      arguments: { nextRfc: "RFC-0040" },
    });

    expect(result.isError).toBe(true);
    expect(markSpy).not.toHaveBeenCalled();
    await expect(readFile(temporaryState!.stateFilePath, "utf-8")).resolves.toBe(
      before,
    );
  });

  it("maps Service errors using the existing Tool error style", async () => {
    const { client } = await setup(true);

    const result = await client.callTool({
      name: "markCurrentRfcCompleted",
      arguments: {},
    });

    expect(result.isError).toBe(true);
    expect(result.content).toEqual([
      expect.objectContaining({
        text: expect.stringContaining(
          "Failed to mark the current RFC completed: The current RFC is already completed.",
        ),
      }),
    ]);
  });

  it("does not import the Repository directly", async () => {
    const source = await readFile(
      new URL(
        "../../src/tool/MarkCurrentRfcCompletedTool.ts",
        import.meta.url,
      ),
      "utf-8",
    );

    expect(source).toContain("service.markCurrentRfcCompleted");
    expect(source).not.toContain("ProjectStateRepository");
  });
});
