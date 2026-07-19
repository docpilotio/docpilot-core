import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import type { ImplementationOrchestrationService } from "../service/ImplementationOrchestrationService.js";

export function registerGetPendingImplementationWorkOrderTool(server: McpServer, service: ImplementationOrchestrationService): void {
  server.registerTool("getPendingImplementationWorkOrder", { title: "Get Pending Implementation Work Order", description: "Returns the current RFC Pending Work Order without side effects.", inputSchema: z.object({}).strict() }, async () => {
    try { const result = await service.getPendingWorkOrder(); return { content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }], structuredContent: result }; }
    catch (error: unknown) { const message = error instanceof Error ? error.message : "Unknown Pending Work Order error."; return { isError: true, content: [{ type: "text" as const, text: `Failed to get Pending Implementation Work Order: ${message}` }] }; }
  });
}
