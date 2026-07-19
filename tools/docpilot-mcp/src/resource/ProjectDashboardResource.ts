import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";

import type { ReleaseReadiness } from "../model/ProjectStatus.js";
import type { RfcLifecycleGuidance } from "../model/RfcLifecycleGuidance.js";
import type { RfcLifecycleEvent } from "../model/RfcLifecycleEvent.js";
import { ProjectStatusService } from "../service/ProjectStatusService.js";

const PROJECT_DASHBOARD_URI = "docpilot://project/dashboard";

type ProjectDashboard = {
  project: string;
  phase: string;
  currentRfc: string;
  release: string;
  completedCount: number;
  completedRfcs: string[];
  releaseReadiness: ReleaseReadiness;
  lifecycleGuidance: RfcLifecycleGuidance;
  lifecycleHistory: readonly RfcLifecycleEvent[];
};

export function registerProjectDashboardResource(
  server: McpServer,
  service: ProjectStatusService,
): void {
  server.registerResource(
    "project-dashboard",
    PROJECT_DASHBOARD_URI,
    {
      title: "DocPilot Project Dashboard",
      description: "Consolidated dashboard for the DocPilot project.",
      mimeType: "application/json",
    },
    async (uri) => {
      const status = await service.getProjectStatus();
      const lifecycleGuidance = await service.getRfcLifecycleGuidance(status);
      const dashboard: ProjectDashboard = {
        project: status.project,
        phase: status.phase,
        currentRfc: status.currentRfc,
        release: status.release,
        completedCount: status.completedRfcs.length,
        completedRfcs: status.completedRfcs,
        releaseReadiness: status.releaseReadiness,
        lifecycleGuidance,
        lifecycleHistory: status.lifecycleHistory,
      };

      return {
        contents: [
          {
            uri: uri.href,
            mimeType: "application/json",
            text: JSON.stringify(dashboard, null, 2),
          },
        ],
      };
    },
  );
}
