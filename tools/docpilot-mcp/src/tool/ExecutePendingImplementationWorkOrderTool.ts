import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import type { ImplementationOrchestrationService } from "../service/ImplementationOrchestrationService.js";

export function registerExecutePendingImplementationWorkOrderTool(server: McpServer, service: ImplementationOrchestrationService): void {
  server.registerTool("executePendingImplementationWorkOrder", { title: "Execute Pending Implementation Work Order", description: "Runs preflight and optionally executes the controlled local Worker, verification, diff review, and Alpha Gate.", inputSchema: z.object({ dryRun: z.boolean().optional() }).strict() }, async ({ dryRun }) => {
    try { const result = await service.execute(dryRun ?? false); return { content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }], structuredContent: result }; }
    catch (error: unknown) { const message = error instanceof Error ? error.message : "Unknown execution error."; return { isError: true, content: [{ type: "text" as const, text: `Failed to execute Pending Implementation Work Order: ${message}` }] }; }
  });
}
