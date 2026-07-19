import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";

import type {
  ProjectStatusService,
  UpdateProjectStatusRequest,
} from "../service/ProjectStatusService.js";

export function registerUpdateProjectStatusTool(
  server: McpServer,
  service: ProjectStatusService,
): void {
  server.registerTool(
    "updateProjectStatus",
    {
      title: "Update Project Status",
      description:
        "Updates selected DocPilot project status fields and saves the result.",
      inputSchema: {
        phase: z
          .string()
          .optional()
          .describe("New project phase"),
        release: z
          .string()
          .optional()
          .describe("New release name or version"),
        currentRfc: z
          .string()
          .optional()
          .describe("New current RFC using the format RFC-0000"),
      },
      outputSchema: {
        project: z.string(),
        phase: z.string(),
        currentRfc: z.string(),
        release: z.string(),
        completedRfcs: z.array(z.string()),
        lifecycleHistory: z.array(z.object({
          id: z.string(),
          type: z.enum(["started", "completed", "planningSynced", "rollbackCompleted"]),
          rfc: z.string(),
          fromRfc: z.string().optional(),
          phase: z.string(),
          release: z.string(),
          timestamp: z.string(),
        })),
      },
    },
    async ({ phase, release, currentRfc }) => {
      try {
        const request: UpdateProjectStatusRequest = {
          ...(phase !== undefined ? { phase } : {}),
          ...(release !== undefined ? { release } : {}),
          ...(currentRfc !== undefined ? { currentRfc } : {}),
        };

        const result = await service.updateProjectStatus(request);

        return {
          content: [
            {
              type: "text" as const,
              text: JSON.stringify(result, null, 2),
            },
          ],
          structuredContent: result,
        };
      } catch (error: unknown) {
        const message =
          error instanceof Error
            ? error.message
            : "An unknown error occurred while updating project status.";

        return {
          isError: true,
          content: [
            {
              type: "text" as const,
              text: `Failed to update project status: ${message}`,
            },
          ],
        };
      }
    },
  );
}
