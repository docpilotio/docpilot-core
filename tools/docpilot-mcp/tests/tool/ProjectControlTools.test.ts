import { readFile } from "node:fs/promises";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { afterEach, describe, expect, it, vi } from "vitest";
import { registerEvaluateRfcCompletionReadinessTool } from "../../src/tool/EvaluateRfcCompletionReadinessTool.js";
import { registerGetDocPilotProjectControlContextTool } from "../../src/tool/GetDocPilotProjectControlContextTool.js";
import {
  connectTestClient, createProjectStatus, createTemporaryState, type TemporaryState,
} from "../support/testState.js";

describe("Project Control Tools", () => {
  let state: TemporaryState | undefined;
  let close: (() => Promise<void>) | undefined;
  afterEach(async () => { await close?.(); await state?.cleanup(); close = undefined; state = undefined; });

  async function setup() {
    state = await createTemporaryState(createProjectStatus());
    const contextSpy = vi.spyOn(state.service, "getDocPilotProjectControlContext");
    const readinessSpy = vi.spyOn(state.service, "evaluateRfcCompletionReadiness");
    const server = new McpServer({ name: "project-control-test", version: "0.0.0" });
    registerGetDocPilotProjectControlContextTool(server, state.service);
    registerEvaluateRfcCompletionReadinessTool(server, state.service);
    const connection = await connectTestClient(server);
    close = connection.close;
    return { client: connection.client, contextSpy, readinessSpy };
  }

  it("registers both Query Tools and returns read-only results", async () => {
    const { client, contextSpy, readinessSpy } = await setup();
    const before = await readFile(state!.stateFilePath, "utf-8");
    expect((await client.listTools()).tools.map(({ name }) => name)).toEqual([
      "getDocPilotProjectControlContext", "evaluateRfcCompletionReadiness",
    ]);
    const context = await client.callTool({ name: "getDocPilotProjectControlContext", arguments: {} });
    const readiness = await client.callTool({ name: "evaluateRfcCompletionReadiness", arguments: {} });
    expect(contextSpy).toHaveBeenCalledOnce();
    expect(readinessSpy).toHaveBeenCalledTimes(2);
    expect(context.structuredContent).toMatchObject({ schemaVersion: "1.0", completionReadiness: { status: "NOT_READY" } });
    expect(readiness.structuredContent).toMatchObject({ rfcId: "RFC-0039", status: "NOT_READY" });
    await expect(readFile(state!.stateFilePath, "utf-8")).resolves.toBe(before);
  });

  it("enforces strict inputs and current RFC identity", async () => {
    const { client, contextSpy, readinessSpy } = await setup();
    const invalidContext = await client.callTool({ name: "getDocPilotProjectControlContext", arguments: { extra: true } });
    expect(invalidContext.isError).toBe(true);
    expect(contextSpy).not.toHaveBeenCalled();
    for (const argumentsValue of [{ rfcId: "RFC-39" }, { rfcId: "RFC-0040" }, { extra: true }]) {
      const result = await client.callTool({ name: "evaluateRfcCompletionReadiness", arguments: argumentsValue });
      expect(result.isError).toBe(true);
    }
    expect(readinessSpy).toHaveBeenCalledTimes(2);
  });

  it("does not import the Repository", async () => {
    for (const file of ["GetDocPilotProjectControlContextTool.ts", "EvaluateRfcCompletionReadinessTool.ts"]) {
      const source = await readFile(new URL(`../../src/tool/${file}`, import.meta.url), "utf-8");
      expect(source).not.toContain("ProjectStateRepository");
    }
  });
});
