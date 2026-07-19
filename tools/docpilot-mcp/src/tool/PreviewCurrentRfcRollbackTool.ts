import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";

import { ProjectStatusService } from "../service/ProjectStatusService.js";

export const PREVIEW_CURRENT_RFC_ROLLBACK_TOOL_NAME =
  "previewCurrentRfcRollback";

const releaseReadinessSchema = z.object({
  coreBuild: z.literal("pending"),
  coreTests: z.literal("pending"),
  cli: z.literal("pending"),
  incremental: z.literal("pending"),
  reviewWorkflow: z.literal("pending"),
  architectureSamplesValidation: z.literal("pending"),
  documentationSync: z.literal("pending"),
  releaseCandidate: z.literal("pending"),
});

export const rollbackPreviewSchema = z.object({
  eligible: z.boolean(),
  currentRfc: z.string(),
  targetRfc: z.string().optional(),
  targetPhase: z.string().optional(),
  targetRelease: z.string().optional(),
  readinessAfterRollback: releaseReadinessSchema.optional(),
  blockingReason: z.string().optional(),
});

export function registerPreviewCurrentRfcRollbackTool(
  server: McpServer,
  service: ProjectStatusService,
): void {
  server.registerTool(
    PREVIEW_CURRENT_RFC_ROLLBACK_TOOL_NAME,
    {
      title: "Preview Current RFC Rollback",
      description:
        "Reports one-step RFC rollback eligibility and restored state without changing persistence.",
      inputSchema: z.object({}).strict(),
      outputSchema: rollbackPreviewSchema,
    },
    async () => {
      try {
        const preview = await service.previewCurrentRfcRollback();

        return {
          content: [
            {
              type: "text" as const,
              text: preview.eligible
                ? `Rollback is eligible from ${preview.currentRfc} to ${preview.targetRfc}.`
                : `Rollback is not eligible: ${preview.blockingReason}`,
            },
          ],
          structuredContent: preview,
        };
      } catch (error: unknown) {
        const message = error instanceof Error
          ? error.message
          : "An unknown error occurred while previewing RFC rollback.";

        return {
          isError: true,
          content: [
            {
              type: "text" as const,
              text: `Failed to preview RFC rollback: ${message}`,
            },
          ],
        };
      }
    },
  );
}
