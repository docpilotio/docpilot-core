import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";

import type { ReleaseReadiness } from "../model/ProjectStatus.js";
import { ProjectStatusService } from "../service/ProjectStatusService.js";

const readinessStateSchema = z.enum(["pending", "passed", "failed"]);

export function registerUpdateReleaseReadinessTool(
  server: McpServer,
  service: ProjectStatusService,
): void {
  server.registerTool(
    "updateReleaseReadiness",
    {
      title: "Update Release Readiness",
      description:
        "Updates one or more persisted DocPilot Release Readiness fields.",
      inputSchema: {
        updates: z
          .object({
            coreBuild: readinessStateSchema.optional(),
            coreTests: readinessStateSchema.optional(),
            cli: readinessStateSchema.optional(),
            incremental: readinessStateSchema.optional(),
            reviewWorkflow: readinessStateSchema.optional(),
            architectureSamplesValidation: readinessStateSchema.optional(),
            documentationSync: readinessStateSchema.optional(),
            releaseCandidate: readinessStateSchema.optional(),
          })
          .strict(),
      },
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
          type: z.enum(["started", "completed", "planningSynced"]),
          rfc: z.string(),
          phase: z.string(),
          release: z.string(),
          timestamp: z.string(),
        })),
      },
    },
    async ({ updates }) => {
      try {
        const result = await service.updateReleaseReadiness(
          updates as Partial<ReleaseReadiness>,
        );

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
            : "An unknown error occurred while updating Release Readiness.";

        return {
          isError: true,
          content: [
            {
              type: "text" as const,
              text: `Failed to update Release Readiness: ${message}`,
            },
          ],
        };
      }
    },
  );
}
