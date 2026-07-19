import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";

import { ProjectStatusService } from "../service/ProjectStatusService.js";

export function registerListCompletedRfcsTool(
  server: McpServer,
  service: ProjectStatusService,
): void {
  server.registerTool(
    "listCompletedRfcs",
    {
      title: "List Completed RFCs",
      description:
        "Returns the completed DocPilot RFCs and their count.",
      inputSchema: {},
      outputSchema: {
        project: z.string(),
        currentRfc: z.string(),
        completedRfcs: z.array(z.string()),
        completedCount: z.number().int().nonnegative(),
      },
    },
    async () => {
      try {
        const result = await service.listCompletedRfcs();

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
            : "An unknown error occurred while loading completed RFCs.";

        return {
          isError: true,
          content: [
            {
              type: "text" as const,
              text: `Failed to load completed RFCs: ${message}`,
            },
          ],
        };
      }
    },
  );
}
