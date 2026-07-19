import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";

import { registerGenerateMainPlanningSyncPrompt } from "./prompt/GenerateMainPlanningSyncPrompt.js";
import { ProjectStateRepository } from "./repository/ProjectStateRepository.js";
import { registerProjectDashboardResource } from "./resource/ProjectDashboardResource.js";
import { registerProjectStatusResource } from "./resource/ProjectStatusResource.js";
import { ProjectStatusService } from "./service/ProjectStatusService.js";
import { ImplementationOrchestrationService } from "./service/ImplementationOrchestrationService.js";
import { ControlledProcessRunner } from "./orchestration/ControlledProcessRunner.js";
import { GitRepositoryController } from "./orchestration/GitRepositoryController.js";
import { LocalCodexWorkerAdapter } from "./orchestration/CodexWorkerAdapter.js";
import { registerCompleteCurrentRfcTool } from "./tool/CompleteCurrentRfcTool.js";
import { registerGenerateMainPlanningSyncTool } from "./tool/GenerateMainPlanningSyncTool.js";
import { registerGetCurrentRfcTool } from "./tool/GetCurrentRfcTool.js";
import { registerGetProjectStatusTool } from "./tool/GetProjectStatusTool.js";
import { registerGetPlanningSynchronizationStatusTool } from "./tool/GetPlanningSynchronizationStatusTool.js";
import { registerLoadRfcContextTool } from "./tool/LoadRfcContextTool.js";
import { registerSubmitRfcHandoffTool } from "./tool/SubmitRfcHandoffTool.js";
import { registerGetPendingRfcHandoffTool } from "./tool/GetPendingRfcHandoffTool.js";
import { registerEvaluateRfcCompletionReadinessTool } from "./tool/EvaluateRfcCompletionReadinessTool.js";
import { registerGetDocPilotProjectControlContextTool } from "./tool/GetDocPilotProjectControlContextTool.js";
import { registerListCompletedRfcsTool } from "./tool/ListCompletedRfcsTool.js";
import { registerMarkCurrentRfcCompletedTool } from "./tool/MarkCurrentRfcCompletedTool.js";
import { registerPreviewCurrentRfcRollbackTool } from "./tool/PreviewCurrentRfcRollbackTool.js";
import { registerRollbackCurrentRfcTool } from "./tool/RollbackCurrentRfcTool.js";
import { registerStartNextRfcTool } from "./tool/StartNextRfcTool.js";
import { registerUpdateProjectStatusTool } from "./tool/UpdateProjectStatusTool.js";
import { registerUpdateReleaseReadinessTool } from "./tool/UpdateReleaseReadinessTool.js";
import { registerPrepareImplementationWorkOrderTool } from "./tool/PrepareImplementationWorkOrderTool.js";
import { registerGetPendingImplementationWorkOrderTool } from "./tool/GetPendingImplementationWorkOrderTool.js";
import { registerExecutePendingImplementationWorkOrderTool } from "./tool/ExecutePendingImplementationWorkOrderTool.js";
import { registerCreateImplementationCommitTool } from "./tool/CreateImplementationCommitTool.js";

export function createServer(): McpServer {
  const repository = new ProjectStateRepository();
  const service = new ProjectStatusService(repository);
  const runner = new ControlledProcessRunner();
  const git = new GitRepositoryController(runner);
  const orchestration = new ImplementationOrchestrationService(repository, service, runner, git, new LocalCodexWorkerAdapter(runner));

  const server = new McpServer({
    name: "docpilot-project-control",
    version: "0.12.0",
  });

  registerGetProjectStatusTool(server, service);
  registerGetCurrentRfcTool(server, service);
  registerCompleteCurrentRfcTool(server, service);
  registerMarkCurrentRfcCompletedTool(server, service);
  registerRollbackCurrentRfcTool(server, service);
  registerPreviewCurrentRfcRollbackTool(server, service);
  registerGetPlanningSynchronizationStatusTool(server, service);
  registerLoadRfcContextTool(server, service);
  registerSubmitRfcHandoffTool(server, service);
  registerGetPendingRfcHandoffTool(server, service);
  registerGetDocPilotProjectControlContextTool(server, service);
  registerEvaluateRfcCompletionReadinessTool(server, service);
  registerPrepareImplementationWorkOrderTool(server, orchestration);
  registerGetPendingImplementationWorkOrderTool(server, orchestration);
  registerExecutePendingImplementationWorkOrderTool(server, orchestration);
  registerCreateImplementationCommitTool(server, orchestration);
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
