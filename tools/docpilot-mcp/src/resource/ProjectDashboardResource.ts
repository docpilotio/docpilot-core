import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";

import { ProjectStatusService } from "../service/ProjectStatusService.js";

const PROJECT_DASHBOARD_URI = "docpilot://project/dashboard";

type ReleaseReadiness = {
  coreBuild: "pending";
  coreTests: "pending";
  cli: "pending";
  incremental: "pending";
  reviewWorkflow: "pending";
  architectureSamplesValidation: "pending";
  documentationSync: "pending";
  releaseCandidate: "pending";
};

type ProjectDashboard = {
  project: string;
  phase: string;
  currentRfc: string;
  release: string;
  completedCount: number;
  completedRfcs: string[];
  releaseReadiness: ReleaseReadiness;
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
      const releaseReadiness: ReleaseReadiness = {
        coreBuild: "pending",
        coreTests: "pending",
        cli: "pending",
        incremental: "pending",
        reviewWorkflow: "pending",
        architectureSamplesValidation: "pending",
        documentationSync: "pending",
        releaseCandidate: "pending",
      };
      const dashboard: ProjectDashboard = {
        project: status.project,
        phase: status.phase,
        currentRfc: status.currentRfc,
        release: status.release,
        completedCount: status.completedRfcs.length,
        completedRfcs: status.completedRfcs,
        releaseReadiness,
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
