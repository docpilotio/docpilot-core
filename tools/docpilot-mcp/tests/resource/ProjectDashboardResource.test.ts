import { readFile } from "node:fs/promises";

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { afterEach, describe, expect, it } from "vitest";

import { registerProjectDashboardResource } from "../../src/resource/ProjectDashboardResource.js";
import {
  connectTestClient,
  createProjectStatus,
  createTemporaryState,
  type TemporaryState,
} from "../support/testState.js";

describe("ProjectDashboardResource", () => {
  let temporaryState: TemporaryState | undefined;
  let closeClient: (() => Promise<void>) | undefined;

  afterEach(async () => {
    await closeClient?.();
    await temporaryState?.cleanup();
    closeClient = undefined;
    temporaryState = undefined;
  });

  it("returns deterministic persisted dashboard state without modifying it", async () => {
    const status = createProjectStatus({
      releaseReadiness: {
        ...createProjectStatus().releaseReadiness,
        coreBuild: "passed",
        coreTests: "failed",
      },
    });
    temporaryState = await createTemporaryState(status);
    const server = new McpServer({ name: "resource-test", version: "0.0.0" });
    registerProjectDashboardResource(server, temporaryState.service);
    const connection = await connectTestClient(server);
    closeClient = connection.close;
    const before = await readFile(temporaryState.stateFilePath, "utf-8");

    const result = await connection.client.readResource({
      uri: "docpilot://project/dashboard",
    });
    const content = result.contents[0];
    expect(content).toBeDefined();
    expect(content?.mimeType).toBe("application/json");
    expect(content?.uri).toBe("docpilot://project/dashboard");
    expect("text" in content!).toBe(true);
    const text = "text" in content! ? content.text : "";
    const dashboard = JSON.parse(text) as Record<string, unknown>;

    expect(dashboard).toEqual({
      project: status.project,
      phase: status.phase,
      currentRfc: status.currentRfc,
      release: status.release,
      completedCount: status.completedRfcs.length,
      completedRfcs: status.completedRfcs,
      releaseReadiness: status.releaseReadiness,
    });
    expect(text).toBe(`${JSON.stringify(dashboard, null, 2)}`);
    await expect(readFile(temporaryState.stateFilePath, "utf-8")).resolves.toBe(
      before,
    );
  });

  it("depends on the Service rather than importing the Repository", async () => {
    const source = await readFile(
      new URL("../../src/resource/ProjectDashboardResource.ts", import.meta.url),
      "utf-8",
    );

    expect(source).toContain("service.getProjectStatus()");
    expect(source).not.toContain("ProjectStateRepository");
    expect(source).not.toContain('coreBuild: "pending"');
  });
});
