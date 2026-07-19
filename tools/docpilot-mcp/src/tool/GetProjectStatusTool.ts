import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";

import { ProjectStatusService } from "../service/ProjectStatusService.js";

export function registerGetProjectStatusTool(
  server: McpServer,
  service: ProjectStatusService,
): void {
  server.registerTool(
    "getProjectStatus",
    {
      title: "Get Project Status",
      description: "Reads and returns the current DocPilot project status.",
      inputSchema: {},
	outputSchema: {
	  project: z.string(),
	  phase: z.string(),
	  currentRfc: z.string(),
	  release: z.string(),
	  completedRfcs: z.array(z.string()),
	},
    },
    async () => {
      try {
        const status = await service.getProjectStatus();

        return {
          content: [
            {
              type: "text" as const,
              text: JSON.stringify(status, null, 2),
            },
          ],
          structuredContent: status,
        };
      } catch (error: unknown) {
        const message =
          error instanceof Error
            ? error.message
            : "An unknown error occurred while loading project status.";

        return {
          isError: true,
          content: [
            {
              type: "text" as const,
              text: `Failed to load DocPilot project status: ${message}`,
            },
          ],
        };
      }
    },
  );
}