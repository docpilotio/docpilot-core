import { readFile } from "node:fs/promises";

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { afterEach, describe, expect, it, vi } from "vitest";

import { registerUpdateReleaseReadinessTool } from "../../src/tool/UpdateReleaseReadinessTool.js";
import {
  connectTestClient,
  createProjectStatus,
  createTemporaryState,
  type TemporaryState,
} from "../support/testState.js";

describe("UpdateReleaseReadinessTool", () => {
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
    const updateSpy = vi.spyOn(
      temporaryState.service,
      "updateReleaseReadiness",
    );
    const server = new McpServer({ name: "tool-test", version: "0.0.0" });
    registerUpdateReleaseReadinessTool(server, temporaryState.service);
    const connection = await connectTestClient(server);
    closeClient = connection.close;

    return { ...connection, updateSpy };
  }

  it("registers the exact Tool name and calls the Service for one update", async () => {
    const { client, updateSpy } = await setup();
    const tools = await client.listTools();

    expect(tools.tools.map(({ name }) => name)).toEqual([
      "updateReleaseReadiness",
    ]);
    const result = await client.callTool({
      name: "updateReleaseReadiness",
      arguments: { updates: { coreBuild: "passed" } },
    });

    expect(updateSpy).toHaveBeenCalledWith({ coreBuild: "passed" });
    expect(result.isError).not.toBe(true);
    expect(result.structuredContent).toMatchObject({
      releaseReadiness: { coreBuild: "passed" },
    });
  });

  it("accepts multiple readiness updates", async () => {
    const { client } = await setup();

    const result = await client.callTool({
      name: "updateReleaseReadiness",
      arguments: {
        updates: { coreTests: "failed", releaseCandidate: "passed" },
      },
    });

    expect(result.structuredContent).toMatchObject({
      releaseReadiness: {
        coreTests: "failed",
        releaseCandidate: "passed",
      },
    });
  });

  it("returns a clear Tool error for an empty update without persisting", async () => {
    const { client } = await setup();
    const before = await readFile(temporaryState!.stateFilePath, "utf-8");

    const result = await client.callTool({
      name: "updateReleaseReadiness",
      arguments: { updates: {} },
    });

    expect(result.isError).toBe(true);
    expect(result.content).toEqual([
      expect.objectContaining({
        text: expect.stringContaining(
          "At least one Release Readiness field must be provided.",
        ),
      }),
    ]);
    await expect(readFile(temporaryState!.stateFilePath, "utf-8")).resolves.toBe(
      before,
    );
  });

  it("rejects invalid values and unknown fields without persisting", async () => {
    const { client } = await setup();
    const before = await readFile(temporaryState!.stateFilePath, "utf-8");

    const invalidValue = await client.callTool({
      name: "updateReleaseReadiness",
      arguments: { updates: { coreBuild: "invalid" } },
    });
    const unknownField = await client.callTool({
      name: "updateReleaseReadiness",
      arguments: { updates: { unexpected: "passed" } },
    });

    expect(invalidValue.isError).toBe(true);
    expect(invalidValue.content).toEqual([
      expect.objectContaining({
        text: expect.stringContaining("Input validation error"),
      }),
    ]);
    expect(unknownField.isError).toBe(true);
    expect(unknownField.content).toEqual([
      expect.objectContaining({
        text: expect.stringContaining("Input validation error"),
      }),
    ]);
    await expect(readFile(temporaryState!.stateFilePath, "utf-8")).resolves.toBe(
      before,
    );
  });

  it("does not import the Repository directly", async () => {
    const source = await readFile(
      new URL("../../src/tool/UpdateReleaseReadinessTool.ts", import.meta.url),
      "utf-8",
    );

    expect(source).toContain("service.updateReleaseReadiness");
    expect(source).not.toContain("ProjectStateRepository");
  });
});
