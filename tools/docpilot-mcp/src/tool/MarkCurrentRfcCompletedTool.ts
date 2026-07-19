import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";

import { ProjectStatusService } from "../service/ProjectStatusService.js";

const readinessStateSchema = z.enum(["pending", "passed", "failed"]);

export function registerMarkCurrentRfcCompletedTool(
  server: McpServer,
  service: ProjectStatusService,
): void {
  server.registerTool(
    "markCurrentRfcCompleted",
    {
      title: "Mark Current RFC Completed",
      description:
        "Marks the current RFC completed without starting another RFC.",
      inputSchema: z.object({}).strict(),
      outputSchema: {
        project: z.string(),
        phase: z.string(),
        currentRfc: z.string(),
        release: z.string(),
        completedRfcs: z.array(z.string()),
        releaseReadiness: z.object({
          coreBuild: readinessStateSchema,
          coreTests: readinessStateSchema,
          cli: readinessStateSchema,
          incremental: readinessStateSchema,
          reviewWorkflow: readinessStateSchema,
          architectureSamplesValidation: readinessStateSchema,
          documentationSync: readinessStateSchema,
          releaseCandidate: readinessStateSchema,
        }),
      },
    },
    async () => {
      try {
        const result = await service.markCurrentRfcCompleted();

        return {
          content: [
            {
              type: "text" as const,
              text: `Marked ${result.currentRfc} completed.`,
            },
          ],
          structuredContent: result,
        };
      } catch (error: unknown) {
        const message =
          error instanceof Error
            ? error.message
            : "An unknown error occurred while marking the current RFC completed.";

        return {
          isError: true,
          content: [
            {
              type: "text" as const,
              text: `Failed to mark the current RFC completed: ${message}`,
            },
          ],
        };
      }
    },
  );
}
