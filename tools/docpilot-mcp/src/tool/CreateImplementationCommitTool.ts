import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import type { ImplementationOrchestrationService } from "../service/ImplementationOrchestrationService.js";

export function registerCreateImplementationCommitTool(server: McpServer, service: ImplementationOrchestrationService): void {
  server.registerTool("createImplementationCommit", { title: "Create Implementation Commit", description: "After Alpha passes, stages only authorized evidence files and creates one commit. It never pushes.", inputSchema: z.object({ message: z.string().trim().min(1) }).strict() }, async ({ message }) => {
    try { const result = await service.createCommit(message); return { content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }], structuredContent: result }; }
    catch (error: unknown) { const text = error instanceof Error ? error.message : "Unknown commit error."; return { isError: true, content: [{ type: "text" as const, text: `Failed to create Implementation Commit: ${text}` }] }; }
  });
}
