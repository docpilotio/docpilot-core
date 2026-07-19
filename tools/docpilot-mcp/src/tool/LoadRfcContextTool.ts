import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { ProjectStatusService } from "../service/ProjectStatusService.js";

export function registerLoadRfcContextTool(server: McpServer, service: ProjectStatusService): void {
  server.registerTool("loadRfcContext", {
    title: "Load RFC Context",
    description: "Loads deterministic execution context for the current RFC.",
    inputSchema: z.object({ rfcId: z.string().optional() }).strict(),
  }, async ({ rfcId }) => {
    try {
      const context = await service.loadRfcContext(rfcId);
      return { content: [{ type: "text" as const, text: JSON.stringify(context, null, 2) }], structuredContent: context };
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : "An unknown error occurred while loading RFC Context.";
      return { isError: true, content: [{ type: "text" as const, text: `Failed to load RFC Context: ${message}` }] };
    }
  });
}
