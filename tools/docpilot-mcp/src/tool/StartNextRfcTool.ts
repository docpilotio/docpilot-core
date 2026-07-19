import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";

import type { StartNextRfcRequest } from "../service/ProjectStatusService.js";
import { ProjectStatusService } from "../service/ProjectStatusService.js";

const readinessStateSchema = z.enum(["pending", "passed", "failed"]);

export function registerStartNextRfcTool(
  server: McpServer,
  service: ProjectStatusService,
): void {
  server.registerTool(
    "startNextRfc",
    {
      title: "Start Next RFC",
      description:
        "Starts a completed RFC's next RFC and resets Release Readiness.",
      inputSchema: z
        .object({
          nextRfc: z
            .string()
            .regex(
              /^RFC-[0-9]{4}$/,
              "nextRfc must use the exact format RFC-0000.",
            ),
          phase: z.string().optional(),
          release: z.string().optional(),
        })
        .strict(),
      outputSchema: {
        project: z.string(),
        phase: z.string(),
        currentRfc: z.string(),
        release: z.string(),
        completedRfcs: z.array(z.string()),
        releaseReadiness: z.object({
          coreBuild: readinessStateSchema,
          coreTests: readinessStateSchema,
          cli: readinessStateSchema,
          incremental: readinessStateSchema,
          reviewWorkflow: readinessStateSchema,
          architectureSamplesValidation: readinessStateSchema,
          documentationSync: readinessStateSchema,
          releaseCandidate: readinessStateSchema,
        }),
      },
    },
    async ({ nextRfc, phase, release }) => {
      try {
        const input: StartNextRfcRequest = {
          nextRfc,
          ...(phase !== undefined ? { phase } : {}),
          ...(release !== undefined ? { release } : {}),
        };
        const result = await service.startNextRfc(input);

        return {
          content: [
            {
              type: "text" as const,
              text: `Started ${result.currentRfc}.`,
            },
          ],
          structuredContent: result,
        };
      } catch (error: unknown) {
        const message =
          error instanceof Error
            ? error.message
            : "An unknown error occurred while starting the next RFC.";

        return {
          isError: true,
          content: [
            {
              type: "text" as const,
              text: `Failed to start the next RFC: ${message}`,
            },
          ],
        };
      }
    },
  );
}
