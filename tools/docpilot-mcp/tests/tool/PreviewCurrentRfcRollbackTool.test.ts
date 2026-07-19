import { readFile } from "node:fs/promises";

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { afterEach, describe, expect, it, vi } from "vitest";

import { registerPreviewCurrentRfcRollbackTool } from "../../src/tool/PreviewCurrentRfcRollbackTool.js";
import {
  connectTestClient,
  createProjectStatus,
  createTemporaryState,
  type TemporaryState,
} from "../support/testState.js";

describe("PreviewCurrentRfcRollbackTool", () => {
  let temporaryState: TemporaryState | undefined;
  let closeClient: (() => Promise<void>) | undefined;

  afterEach(async () => {
    await closeClient?.();
    await temporaryState?.cleanup();
    closeClient = undefined;
    temporaryState = undefined;
  });

  async function setup() {
    temporaryState = await createTemporaryState(createProjectStatus({
      currentRfc: "RFC-0040",
      lifecycleHistory: [
        {
          id: "rfc-event-000001", type: "completed", rfc: "RFC-0039",
          phase: "Phase 1", release: "v0.7.0", timestamp: "2026-01-01T00:00:00.000Z",
        },
        {
          id: "rfc-event-000002", type: "started", rfc: "RFC-0040",
          phase: "Phase 2", release: "v0.8.0", timestamp: "2026-01-02T00:00:00.000Z",
        },
      ],
    }));
    const previewSpy = vi.spyOn(temporaryState.service, "previewCurrentRfcRollback");
    const server = new McpServer({ name: "preview-test", version: "0.0.0" });
    registerPreviewCurrentRfcRollbackTool(server, temporaryState.service);
    const connection = await connectTestClient(server);
    closeClient = connection.close;
    return { ...connection, previewSpy };
  }

  it("accepts strict empty input and returns the complete read-only Preview", async () => {
    const { client, previewSpy } = await setup();
    const before = await readFile(temporaryState!.stateFilePath, "utf-8");

    expect((await client.listTools()).tools.map(({ name }) => name)).toEqual([
      "previewCurrentRfcRollback",
    ]);
    const result = await client.callTool({ name: "previewCurrentRfcRollback", arguments: {} });

    expect(previewSpy).toHaveBeenCalledOnce();
    expect(result.isError).not.toBe(true);
    expect(result.structuredContent).toMatchObject({
      eligible: true,
      currentRfc: "RFC-0040",
      targetRfc: "RFC-0039",
      targetPhase: "Phase 1",
      targetRelease: "v0.7.0",
    });
    await expect(readFile(temporaryState!.stateFilePath, "utf-8")).resolves.toBe(before);
  });

  it("rejects unknown input before calling the Service", async () => {
    const { client, previewSpy } = await setup();
    const result = await client.callTool({
      name: "previewCurrentRfcRollback",
      arguments: { targetRfc: "RFC-0039" },
    });
    expect(result.isError).toBe(true);
    expect(previewSpy).not.toHaveBeenCalled();
  });

  it("does not import the Repository", async () => {
    const source = await readFile(
      new URL("../../src/tool/PreviewCurrentRfcRollbackTool.ts", import.meta.url),
      "utf-8",
    );
    expect(source).toContain("service.previewCurrentRfcRollback");
    expect(source).not.toContain("ProjectStateRepository");
  });
});
