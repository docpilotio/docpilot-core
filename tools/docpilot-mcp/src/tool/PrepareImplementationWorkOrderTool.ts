import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import type { ImplementationOrchestrationService, PrepareImplementationWorkOrderInput } from "../service/ImplementationOrchestrationService.js";
import { prepareWorkOrderInputSchema } from "./ImplementationOrchestrationSchemas.js";

export function registerPrepareImplementationWorkOrderTool(server: McpServer, service: ImplementationOrchestrationService): void {
  server.registerTool("prepareImplementationWorkOrder", { title: "Prepare Implementation Work Order", description: "Creates and persists one controlled Work Order for the current RFC without executing code.", inputSchema: prepareWorkOrderInputSchema }, async (input) => {
    try { const result = await service.prepareWorkOrder(input as PrepareImplementationWorkOrderInput); return { content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }], structuredContent: result }; }
    catch (error: unknown) { const message = error instanceof Error ? error.message : "Unknown Work Order error."; return { isError: true, content: [{ type: "text" as const, text: `Failed to prepare Implementation Work Order: ${message}` }] }; }
  });
}
