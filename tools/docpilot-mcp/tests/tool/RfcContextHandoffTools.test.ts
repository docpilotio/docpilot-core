import { readFile } from "node:fs/promises";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { afterEach, describe, expect, it } from "vitest";
import { registerLoadRfcContextTool } from "../../src/tool/LoadRfcContextTool.js";
import { registerSubmitRfcHandoffTool } from "../../src/tool/SubmitRfcHandoffTool.js";
import { registerGetPendingRfcHandoffTool } from "../../src/tool/GetPendingRfcHandoffTool.js";
import { registerGetProjectStatusTool } from "../../src/tool/GetProjectStatusTool.js";
import {
  connectTestClient, createProjectStatus, createRfcHandoff, createTemporaryState, type TemporaryState,
} from "../support/testState.js";

describe("RFC Context and Handoff Tools", () => {
  let state: TemporaryState | undefined;
  let close: (() => Promise<void>) | undefined;
  afterEach(async () => { await close?.(); await state?.cleanup(); close = undefined; state = undefined; });

  async function setup() {
    state = await createTemporaryState(createProjectStatus());
    const server = new McpServer({ name: "handoff-test", version: "0.0.0" });
    registerLoadRfcContextTool(server, state.service);
    registerSubmitRfcHandoffTool(server, state.service);
    registerGetPendingRfcHandoffTool(server, state.service);
    const connection = await connectTestClient(server);
    close = connection.close;
    return connection.client;
  }

  it("registers and smoke-tests Context submit and retrieve", async () => {
    const client = await setup();
    expect((await client.listTools()).tools.map(({ name }) => name)).toEqual([
      "loadRfcContext", "submitRfcHandoff", "getPendingRfcHandoff",
    ]);
    const before = await readFile(state!.stateFilePath, "utf-8");
    const context = await client.callTool({ name: "loadRfcContext", arguments: {} });
    expect(context.structuredContent).toMatchObject({ schemaVersion: "1.0", rfc: { id: "RFC-0039" } });
    await expect(readFile(state!.stateFilePath, "utf-8")).resolves.toBe(before);

    const submission = await client.callTool({
      name: "submitRfcHandoff", arguments: { handoff: createRfcHandoff() },
    });
    expect(submission.isError).not.toBe(true);
    expect(submission.structuredContent).toMatchObject({ handoff: { rfcId: "RFC-0039" } });
    const pending = await client.callTool({ name: "getPendingRfcHandoff", arguments: {} });
    expect(pending.structuredContent).toMatchObject({ found: true, rfcId: "RFC-0039" });
  });

  it("rejects RFC mismatch, malformed RFC, unknown query input, and invalid Handoff schema", async () => {
    const client = await setup();
    for (const argumentsValue of [{ rfcId: "RFC-0040" }, { rfcId: "RFC-40" }, { extra: true }]) {
      const result = await client.callTool({ name: "loadRfcContext", arguments: argumentsValue });
      expect(result.isError).toBe(true);
    }
    const missing = await client.callTool({ name: "submitRfcHandoff", arguments: { handoff: {} } });
    expect(missing.isError).toBe(true);
    const invalidEnum = createRfcHandoff() as unknown as Record<string, unknown>;
    invalidEnum.verification = { ...createRfcHandoff().verification, build: "MAYBE" };
    const invalid = await client.callTool({ name: "submitRfcHandoff", arguments: { handoff: invalidEnum } });
    expect(invalid.isError).toBe(true);
    const unknownPending = await client.callTool({ name: "getPendingRfcHandoff", arguments: { extra: true } });
    expect(unknownPending.isError).toBe(true);
  });

  it("Tools do not import the Repository", async () => {
    for (const file of ["LoadRfcContextTool.ts", "SubmitRfcHandoffTool.ts", "GetPendingRfcHandoffTool.ts"]) {
      const source = await readFile(new URL(`../../src/tool/${file}`, import.meta.url), "utf-8");
      expect(source).not.toContain("ProjectStateRepository");
    }
  });

  it("keeps the existing Project Status Tool compatible after submission", async () => {
    state = await createTemporaryState(createProjectStatus());
    await state.service.submitRfcHandoff(createRfcHandoff());
    const server = new McpServer({ name: "compatibility-test", version: "0.0.0" });
    registerGetProjectStatusTool(server, state.service);
    const connection = await connectTestClient(server);
    close = connection.close;
    const result = await connection.client.callTool({ name: "getProjectStatus", arguments: {} });
    expect(result.isError).not.toBe(true);
    expect(result.structuredContent).toMatchObject({
      currentRfc: "RFC-0039",
      pendingRfcHandoff: { rfcId: "RFC-0039" },
    });
  });
});
