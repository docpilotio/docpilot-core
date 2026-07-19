import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";

import { ProjectStatusService } from "../service/ProjectStatusService.js";
import { rollbackPreviewSchema } from "./PreviewCurrentRfcRollbackTool.js";

export function registerGenerateMainPlanningSyncTool(
  server: McpServer,
  service: ProjectStatusService,
): void {
  server.registerTool(
    "generateMainPlanningSync",
    {
      title: "Generate Main Planning Sync",
      description:
        "Generates a Markdown summary for synchronizing DocPilot Main Planning.",
      inputSchema: {},
      outputSchema: {
        project: z.string(),
        phase: z.string(),
        currentRfc: z.string(),
        release: z.string(),
        completedRfcs: z.array(z.string()),
        completedCount: z.number().int().nonnegative(),
        markdown: z.string(),
        lifecycleGuidance: z.object({
          state: z.enum([
            "in_progress",
            "completed_waiting_next",
            "inconsistent",
          ]),
          nextAction: z.enum([
            "markCurrentRfcCompleted",
            "startNextRfc",
            "manualReview",
          ]),
          reason: z.string(),
        }),
        lifecycleHistory: z.array(
          z.object({
            id: z.string(),
            type: z.enum([
              "started",
              "completed",
              "planningSynced",
              "rollbackCompleted",
            ]),
            rfc: z.string(),
            fromRfc: z.string().optional(),
            phase: z.string(),
            release: z.string(),
            timestamp: z.string(),
          }),
        ),
        rollbackPreview: rollbackPreviewSchema,
      },
    },
    async () => {
      try {
        const result = await service.generateMainPlanningSync();

        return {
          content: [
            {
              type: "text" as const,
              text: result.markdown,
            },
          ],
          structuredContent: result,
        };
      } catch (error: unknown) {
        const message =
          error instanceof Error
            ? error.message
            : "An unknown error occurred while generating the Main Planning sync.";

        return {
          isError: true,
          content: [
            {
              type: "text" as const,
              text: `Failed to generate the Main Planning sync: ${message}`,
            },
          ],
        };
      }
    },
  );
}
