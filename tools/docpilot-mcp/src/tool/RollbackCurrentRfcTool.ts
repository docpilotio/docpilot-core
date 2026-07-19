import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";

import { ProjectStatusService } from "../service/ProjectStatusService.js";

const readinessStateSchema = z.enum(["pending", "passed", "failed"]);

export function registerRollbackCurrentRfcTool(
  server: McpServer,
  service: ProjectStatusService,
): void {
  server.registerTool(
    "rollbackCurrentRfc",
    {
      title: "Rollback Current RFC",
      description:
        "Restores the immediately previous RFC using lifecycle evidence.",
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
        lifecycleHistory: z.array(z.object({
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
        })),
      },
    },
    async () => {
      try {
        const result = await service.rollbackCurrentRfc();

        return {
          content: [
            {
              type: "text" as const,
              text: `Rolled back to ${result.currentRfc}.`,
            },
          ],
          structuredContent: result,
        };
      } catch (error: unknown) {
        const message =
          error instanceof Error
            ? error.message
            : "An unknown error occurred while rolling back the current RFC.";

        return {
          isError: true,
          content: [
            {
              type: "text" as const,
              text: `Failed to rollback the current RFC: ${message}`,
            },
          ],
        };
      }
    },
  );
}
