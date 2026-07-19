import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";

import { ProjectStatusService } from "../service/ProjectStatusService.js";

export const PLANNING_SYNCHRONIZATION_STATUS_TOOL_NAME =
  "getPlanningSynchronizationStatus";

export const planningSynchronizationStatusSchema = z.object({
  state: z.enum(["neverSynced", "current", "stale"]),
  synchronized: z.boolean(),
  currentRfc: z.string(),
  lastPlanningSyncEventId: z.string().optional(),
  lastPlanningSyncRfc: z.string().optional(),
  latestRelevantEventId: z.string().optional(),
  latestRelevantEventType: z.enum([
    "started",
    "completed",
    "planningSynced",
    "rollbackCompleted",
  ]).optional(),
  reason: z.string(),
  recommendedAction: z.enum(["none", "generateMainPlanningSync"]),
  expectedDocumentationSync: z.enum(["pending", "passed"]),
  documentationSyncConsistent: z.boolean(),
  documentationSyncReason: z.string().optional(),
});

export function registerGetPlanningSynchronizationStatusTool(
  server: McpServer,
  service: ProjectStatusService,
): void {
  server.registerTool(
    PLANNING_SYNCHRONIZATION_STATUS_TOOL_NAME,
    {
      title: "Get Planning Synchronization Status",
      description:
        "Evaluates whether Main Planning reflects the latest RFC lifecycle state.",
      inputSchema: z.object({}).strict(),
      outputSchema: planningSynchronizationStatusSchema,
    },
    async () => {
      try {
        const status = await service.getPlanningSynchronizationStatus();

        return {
          content: [{
            type: "text" as const,
            text: `Main Planning synchronization status: ${status.state}.`,
          }],
          structuredContent: status,
        };
      } catch (error: unknown) {
        const message = error instanceof Error
          ? error.message
          : "An unknown error occurred while evaluating Main Planning synchronization.";

        return {
          isError: true,
          content: [{
            type: "text" as const,
            text: `Failed to evaluate Main Planning synchronization: ${message}`,
          }],
        };
      }
    },
  );
}
