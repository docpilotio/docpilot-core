import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";

import { registerGenerateMainPlanningSyncPrompt } from "./prompt/GenerateMainPlanningSyncPrompt.js";
import { ProjectStateRepository } from "./repository/ProjectStateRepository.js";
import { registerProjectDashboardResource } from "./resource/ProjectDashboardResource.js";
import { registerProjectStatusResource } from "./resource/ProjectStatusResource.js";
import { ProjectStatusService } from "./service/ProjectStatusService.js";
import { registerCompleteCurrentRfcTool } from "./tool/CompleteCurrentRfcTool.js";
import { registerGenerateMainPlanningSyncTool } from "./tool/GenerateMainPlanningSyncTool.js";
import { registerGetCurrentRfcTool } from "./tool/GetCurrentRfcTool.js";
import { registerGetProjectStatusTool } from "./tool/GetProjectStatusTool.js";
import { registerListCompletedRfcsTool } from "./tool/ListCompletedRfcsTool.js";
import { registerMarkCurrentRfcCompletedTool } from "./tool/MarkCurrentRfcCompletedTool.js";
import { registerRollbackCurrentRfcTool } from "./tool/RollbackCurrentRfcTool.js";
import { registerStartNextRfcTool } from "./tool/StartNextRfcTool.js";
import { registerUpdateProjectStatusTool } from "./tool/UpdateProjectStatusTool.js";
import { registerUpdateReleaseReadinessTool } from "./tool/UpdateReleaseReadinessTool.js";

export function createServer(): McpServer {
  const repository = new ProjectStateRepository();
  const service = new ProjectStatusService(repository);

  const server = new McpServer({
    name: "docpilot-project-control",
    version: "0.1.0",
  });

  registerGetProjectStatusTool(server, service);
  registerGetCurrentRfcTool(server, service);
  registerCompleteCurrentRfcTool(server, service);
  registerMarkCurrentRfcCompletedTool(server, service);
  registerRollbackCurrentRfcTool(server, service);
  registerGenerateMainPlanningSyncTool(server, service);
  registerListCompletedRfcsTool(server, service);
  registerUpdateProjectStatusTool(server, service);
  registerUpdateReleaseReadinessTool(server, service);
  registerStartNextRfcTool(server, service);

  registerProjectStatusResource(server, service);
  registerProjectDashboardResource(server, service);
  registerGenerateMainPlanningSyncPrompt(server, service);

  return server;
}
