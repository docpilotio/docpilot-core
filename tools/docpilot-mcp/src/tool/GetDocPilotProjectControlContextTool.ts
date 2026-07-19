import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { ProjectStatusService } from "../service/ProjectStatusService.js";

export function registerGetDocPilotProjectControlContextTool(
  server: McpServer,
  service: ProjectStatusService,
): void {
  server.registerTool("getDocPilotProjectControlContext", {
    title: "Get DocPilot Project Control Context",
    description: "Returns the official read-only Project Control integration boundary.",
    inputSchema: z.object({}).strict(),
  }, async () => {
    try {
      const result = await service.getDocPilotProjectControlContext();
      return {
        content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }],
        structuredContent: result,
      };
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : "An unknown Project Control Context error occurred.";
      return { isError: true, content: [{ type: "text" as const, text: `Failed to load Project Control Context: ${message}` }] };
    }
  });
}
