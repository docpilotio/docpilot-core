import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { ProjectStatusService } from "../service/ProjectStatusService.js";
import { rfcHandoffSchema } from "./RfcHandoffSchemas.js";

export function registerGetPendingRfcHandoffTool(server: McpServer, service: ProjectStatusService): void {
  server.registerTool("getPendingRfcHandoff", {
    title: "Get Pending RFC Handoff",
    description: "Returns the current RFC Pending Handoff without changing state.",
    inputSchema: z.object({}).strict(),
    outputSchema: z.object({
      found: z.boolean(), rfcId: z.string(), handoff: rfcHandoffSchema.optional(), markdown: z.string().optional(),
    }),
  }, async () => {
    try {
      const result = await service.getPendingRfcHandoff();
      return { content: [{ type: "text" as const, text: result.found ? result.markdown! : `No Pending Handoff exists for ${result.rfcId}.` }], structuredContent: result };
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : "An unknown error occurred while reading Pending Handoff.";
      return { isError: true, content: [{ type: "text" as const, text: `Failed to read Pending Handoff: ${message}` }] };
    }
  });
}
