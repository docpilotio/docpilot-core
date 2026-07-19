import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";

import { ProjectStatusService } from "../service/ProjectStatusService.js";

export function registerGetCurrentRfcTool(
  server: McpServer,
  service: ProjectStatusService,
): void {
  server.registerTool(
    "getCurrentRfc",
    {
      title: "Get Current RFC",
      description: "Returns the current DocPilot RFC and release context.",
      inputSchema: {},
      outputSchema: {
        currentRfc: z.string(),
        phase: z.string(),
        release: z.string(),
      },
    },
    async () => {
      try {
        const currentRfc = await service.getCurrentRfc();

        return {
          content: [
            {
              type: "text" as const,
              text: JSON.stringify(currentRfc, null, 2),
            },
          ],
          structuredContent: currentRfc,
        };
      } catch (error: unknown) {
        const message =
          error instanceof Error
            ? error.message
            : "An unknown error occurred while loading the current RFC.";

        return {
          isError: true,
          content: [
            {
              type: "text" as const,
              text: `Failed to load the current RFC: ${message}`,
            },
          ],
        };
      }
    },
  );
}