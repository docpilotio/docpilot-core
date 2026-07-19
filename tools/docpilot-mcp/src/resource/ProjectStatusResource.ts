import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";

import { ProjectStatusService } from "../service/ProjectStatusService.js";

const PROJECT_STATUS_URI = "docpilot://project/status";

export function registerProjectStatusResource(
  server: McpServer,
  service: ProjectStatusService,
): void {
  server.registerResource(
    "project-status",
    PROJECT_STATUS_URI,
    {
      title: "DocPilot Project Status",
      description: "Current status of the DocPilot project.",
      mimeType: "application/json",
    },
    async (uri) => {
      const status = await service.getProjectStatus();

      return {
        contents: [
          {
            uri: uri.href,
            mimeType: "application/json",
            text: JSON.stringify(status, null, 2),
          },
        ],
      };
    },
  );
}