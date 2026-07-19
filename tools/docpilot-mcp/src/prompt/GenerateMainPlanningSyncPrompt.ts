import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";

import { ProjectStatusService } from "../service/ProjectStatusService.js";

export function registerGenerateMainPlanningSyncPrompt(
  server: McpServer,
  service: ProjectStatusService,
): void {
  server.registerPrompt(
    "generateMainPlanningSync",
    {
      title: "Generate Main Planning Sync",
      description:
        "Creates a prompt for synchronizing the current DocPilot status with Main Planning.",
			  argsSchema: {
		  completedWork: z
			.string()
			.optional()
			.describe("Summary of the work completed in the current RFC"),
		  nextWork: z
			.string()
			.optional()
			.describe("Summary of the next planned work"),
		},
    },
    async ({ completedWork, nextWork }) => {
      const status = await service.getProjectStatus();

      const promptText = [
        "다음 DocPilot 프로젝트 상태를 Main Planning에 동기화해 주세요.",
        "",
        "## 현재 프로젝트 상태",
        `- Project: ${status.project}`,
        `- Phase: ${status.phase}`,
        `- Current RFC: ${status.currentRfc}`,
        `- Release: ${status.release}`,
        "",
        "## 완료한 작업",
        completedWork ?? "별도로 입력된 완료 작업이 없습니다.",
        "",
        "## 다음 작업",
        nextWork ?? "별도로 입력된 다음 작업이 없습니다.",
        "",
        "## 동기화 요청",
        "- Current Phase를 갱신해 주세요.",
        "- Completed RFC와 Current RFC를 갱신해 주세요.",
        "- Release 상태를 갱신해 주세요.",
        "- Roadmap 및 다음 RFC 시작 정보를 정리해 주세요.",
        "- 기술 부채나 후속 작업이 있으면 함께 기록해 주세요.",
      ].join("\n");

      return {
        description: "DocPilot Main Planning synchronization prompt",
        messages: [
          {
            role: "user" as const,
            content: {
              type: "text" as const,
              text: promptText,
            },
          },
        ],
      };
    },
  );
}