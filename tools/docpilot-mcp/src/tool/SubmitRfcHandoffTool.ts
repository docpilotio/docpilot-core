import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { ProjectStatusService } from "../service/ProjectStatusService.js";
import type { RfcHandoff } from "../model/RfcHandoff.js";
import { rfcHandoffSchema } from "./RfcHandoffSchemas.js";

export function registerSubmitRfcHandoffTool(server: McpServer, service: ProjectStatusService): void {
  server.registerTool("submitRfcHandoff", {
    title: "Submit RFC Handoff",
    description: "Persists one structured Pending Handoff for the current RFC.",
    inputSchema: z.object({ handoff: rfcHandoffSchema }).strict(),
    outputSchema: z.object({ handoff: rfcHandoffSchema, markdown: z.string() }),
  }, async ({ handoff }) => {
    try {
      const result = await service.submitRfcHandoff(handoff as RfcHandoff);
      return { content: [{ type: "text" as const, text: result.markdown }], structuredContent: result };
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : "An unknown error occurred while submitting RFC Handoff.";
      return { isError: true, content: [{ type: "text" as const, text: `Failed to submit RFC Handoff: ${message}` }] };
    }
  });
}
