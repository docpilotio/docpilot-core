import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";

import { ProjectStatusService } from "../service/ProjectStatusService.js";

export function registerCompleteCurrentRfcTool(
  server: McpServer,
  service: ProjectStatusService,
): void {
  server.registerTool(
    "completeCurrentRfc",
    {
      title: "Complete Current RFC",
      description:
        "Completes the current RFC and changes the project to the specified next RFC.",
      inputSchema: {
        nextRfc: z
          .string()
          .regex(
            /^RFC-\d{4}$/,
            "nextRfc must use the format RFC-0000.",
          )
          .describe("RFC that becomes current after completion"),
      },
      outputSchema: {
        completedRfc: z.string(),
        currentRfc: z.string(),
        phase: z.string(),
        release: z.string(),
        completedRfcs: z.array(z.string()),
      },
    },
    async ({ nextRfc }) => {
      try {
        const result = await service.completeCurrentRfc(nextRfc);

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
            : "An unknown error occurred while completing the RFC.";

        return {
          isError: true,
          content: [
            {
              type: "text" as const,
              text: `Failed to complete the current RFC: ${message}`,
            },
          ],
        };
      }
    },
  );
}