import { readFile } from "node:fs/promises";

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { afterEach, describe, expect, it, vi } from "vitest";

import { registerRollbackCurrentRfcTool } from "../../src/tool/RollbackCurrentRfcTool.js";
import {
  connectTestClient,
  createProjectStatus,
  createTemporaryState,
  type TemporaryState,
} from "../support/testState.js";

describe("RollbackCurrentRfcTool", () => {
  let temporaryState: TemporaryState | undefined;
  let closeClient: (() => Promise<void>) | undefined;

  afterEach(async () => {
    await closeClient?.();
    await temporaryState?.cleanup();
    closeClient = undefined;
    temporaryState = undefined;
  });

  async function setup(withEvidence = true) {
    temporaryState = await createTemporaryState(
      createProjectStatus({
        currentRfc: "RFC-0040",
        lifecycleHistory: withEvidence
          ? [
              {
                id: "rfc-event-000001",
                type: "completed",
                rfc: "RFC-0039",
                phase: "Phase 1 — MVP",
                release: "v0.6 MVP",
                timestamp: "2026-07-19T10:00:00.000Z",
              },
              {
                id: "rfc-event-000002",
                type: "started",
                rfc: "RFC-0040",
                phase: "Phase 2",
                release: "v0.7",
                timestamp: "2026-07-19T10:01:00.000Z",
              },
            ]
          : [],
      }),
    );
    const rollbackSpy = vi.spyOn(temporaryState.service, "rollbackCurrentRfc");
    const server = new McpServer({ name: "rollback-test", version: "0.0.0" });
    registerRollbackCurrentRfcTool(server, temporaryState.service);
    const connection = await connectTestClient(server);
    closeClient = connection.close;

    return { ...connection, rollbackSpy };
  }

  it("accepts strict empty input and returns the restored status", async () => {
    const { client, rollbackSpy } = await setup();

    expect((await client.listTools()).tools.map(({ name }) => name)).toEqual([
      "rollbackCurrentRfc",
    ]);
    const result = await client.callTool({
      name: "rollbackCurrentRfc",
      arguments: {},
    });

    expect(rollbackSpy).toHaveBeenCalledOnce();
    expect(result.isError).not.toBe(true);
    expect(result.content).toEqual([
      { type: "text", text: "Rolled back to RFC-0039." },
    ]);
    expect(result.structuredContent).toMatchObject({
      currentRfc: "RFC-0039",
      lifecycleHistory: [
        expect.any(Object),
        expect.any(Object),
        expect.objectContaining({
          type: "rollbackCompleted",
          fromRfc: "RFC-0040",
          rfc: "RFC-0039",
        }),
      ],
    });
  });

  it("rejects unknown input before calling the Service", async () => {
    const { client, rollbackSpy } = await setup();

    const result = await client.callTool({
      name: "rollbackCurrentRfc",
      arguments: { targetRfc: "RFC-0039" },
    });

    expect(result.isError).toBe(true);
    expect(rollbackSpy).not.toHaveBeenCalled();
  });

  it("maps Service errors using the existing Tool style", async () => {
    const { client } = await setup(false);

    const result = await client.callTool({
      name: "rollbackCurrentRfc",
      arguments: {},
    });

    expect(result.isError).toBe(true);
    expect(result.content).toEqual([
      expect.objectContaining({
        text: expect.stringContaining(
          "Failed to rollback the current RFC: Lifecycle history is empty",
        ),
      }),
    ]);
  });

  it("does not import the Repository directly", async () => {
    const source = await readFile(
      new URL("../../src/tool/RollbackCurrentRfcTool.ts", import.meta.url),
      "utf-8",
    );

    expect(source).toContain("service.rollbackCurrentRfc");
    expect(source).not.toContain("ProjectStateRepository");
  });
});
