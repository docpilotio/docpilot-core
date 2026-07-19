import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { ProjectStatusService } from "../service/ProjectStatusService.js";
import { completionReadinessSchema } from "./CompletionReadinessSchema.js";

export function registerEvaluateRfcCompletionReadinessTool(
  server: McpServer,
  service: ProjectStatusService,
): void {
  server.registerTool("evaluateRfcCompletionReadiness", {
    title: "Evaluate RFC Completion Readiness",
    description: "Evaluates deterministic alpha gates for the current RFC Pending Handoff.",
    inputSchema: z.object({ rfcId: z.string().optional() }).strict(),
    outputSchema: completionReadinessSchema,
  }, async ({ rfcId }) => {
    try {
      const result = await service.evaluateRfcCompletionReadiness(rfcId);
      return {
        content: [{ type: "text" as const, text: `RFC ${result.rfcId} completion readiness: ${result.status}.` }],
        structuredContent: result,
      };
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : "An unknown readiness evaluation error occurred.";
      return { isError: true, content: [{ type: "text" as const, text: `Failed to evaluate RFC Completion Readiness: ${message}` }] };
    }
  });
}
