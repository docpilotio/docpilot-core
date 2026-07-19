import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { InMemoryTransport } from "@modelcontextprotocol/sdk/inMemory.js";
import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";

import {
  createDefaultReleaseReadiness,
  type ProjectStatus,
} from "../../src/model/ProjectStatus.js";
import { ProjectStateRepository } from "../../src/repository/ProjectStateRepository.js";
import { ProjectStatusService } from "../../src/service/ProjectStatusService.js";

export type TemporaryState = {
  directoryPath: string;
  stateFilePath: string;
  repository: ProjectStateRepository;
  service: ProjectStatusService;
  cleanup: () => Promise<void>;
};

export function createProjectStatus(
  overrides: Partial<ProjectStatus> = {},
): ProjectStatus {
  return {
    project: "DocPilot",
    phase: "Phase 1 — MVP",
    currentRfc: "RFC-0039",
    release: "v0.6 MVP",
    completedRfcs: ["RFC-0037", "RFC-0038"],
    releaseReadiness: createDefaultReleaseReadiness(),
    ...overrides,
  };
}

export async function createTemporaryState(
  initialState?: unknown,
): Promise<TemporaryState> {
  const directoryPath = await mkdtemp(
    join(tmpdir(), "docpilot-mcp-test-"),
  );
  const stateFilePath = join(directoryPath, "project-state.json");

  if (initialState !== undefined) {
    await writeFile(
      stateFilePath,
      `${JSON.stringify(initialState, null, 2)}\n`,
      "utf-8",
    );
  }

  const repository = new ProjectStateRepository(stateFilePath);

  return {
    directoryPath,
    stateFilePath,
    repository,
    service: new ProjectStatusService(repository),
    cleanup: async () => {
      await rm(directoryPath, { recursive: true, force: true });
    },
  };
}

export async function connectTestClient(server: McpServer): Promise<{
  client: Client;
  close: () => Promise<void>;
}> {
  const client = new Client({
    name: "docpilot-mcp-test-client",
    version: "0.0.0",
  });
  const [clientTransport, serverTransport] =
    InMemoryTransport.createLinkedPair();

  await server.connect(serverTransport);
  await client.connect(clientTransport);

  return {
    client,
    close: async () => {
      await client.close();
      await server.close();
    },
  };
}
