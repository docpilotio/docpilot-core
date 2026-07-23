import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import type { ImplementationWorkOrder } from "../../src/model/ImplementationOrchestration.js";
import {
  LocalCodexWorkerAdapter,
} from "../../src/orchestration/CodexWorkerAdapter.js";
import type {
  ProcessRequest,
  ProcessRunner,
} from "../../src/orchestration/ControlledProcessRunner.js";
import {
  renderCodexImplementationPrompt,
} from "../../src/service/ImplementationOrchestrationService.js";

describe("LocalCodexWorkerAdapter", () => {
  const directories: string[] = [];

  afterEach(async () => {
    await Promise.all(
      directories.splice(0).map((path) =>
        rm(path, { recursive: true, force: true })
      )
    );
  });

  it("passes the deterministic multiline prompt through stdin", async () => {
    const root = await mkdtemp(join(tmpdir(), "docpilot-codex-adapter-"));
    directories.push(root);

    const resultFile = "worker-result.json";
    await writeFile(join(root, resultFile), "{}\n", "utf8");

    let captured: ProcessRequest | undefined;

    const runner: ProcessRunner = {
      execute: async (request) => {
        captured = request;
        const outputIndex = request.args.indexOf("--output-last-message");
        const outputPath = request.args[outputIndex + 1];

        if (outputIndex < 0 || outputPath === undefined) {
          throw new Error(
            "Fake Codex runner requires --output-last-message <path>.",
          );
        }

        await writeFile(outputPath, "{}\n", "utf8");

        return {
          status: "PASSED",
          exitCode: 0,
          stdout: "",
          stderr: "",
          outputTruncated: false,
          timedOut: false,
          cancelled: false,
          terminationSteps: [],
        };
      },
    };

    const order: ImplementationWorkOrder = {
      schemaVersion: "1.0",
      id: "RFC-9001-b3434741c2a1",
      rfcId: "RFC-9001",
      repository: {
        rootPath: root,
        baselineBranch: "test/mcp-orchestration-e2e",
        baselineCommit: "b3434741c2a1f969fd1ad48c4e4fb1e3fd510298",
        workingDirectory: root,
      },
      objective: {
        goal: "Verify prompt transport.",
        approvedPlan: [],
        acceptanceCriteria: [],
        alphaCriteria: [],
      },
      scope: {
        allowedPaths: ["worker-result.json"],
        forbiddenPaths: [],
        allowUntrackedFiles: true,
        allowDependencyChanges: false,
        allowBuildConfigurationChanges: false,
        allowPublicApiChanges: false,
      },
      execution: {
        codexCommand: process.execPath,
        codexArguments: ["codex.js", "exec"],
        timeoutSeconds: 60,
        maxOutputCharacters: 1_000,
        environmentAllowlist: [],
      },
      verification: {
        targetedCommands: [],
        moduleCommands: [],
        buildCommands: [],
        regressionCommands: [],
        smokeCommands: [],
      },
      gitPolicy: {
        requireCleanWorkingTree: true,
        allowCommit: false,
        requireUserApprovalForPush: true,
        allowMainBranchPush: false,
        allowForcePush: false,
      },
      resultContract: {
        resultFile,
        expectedSchemaVersion: "1.0",
      },
      warnings: [],
    };

    const prompt = renderCodexImplementationPrompt(order);

    expect(prompt).toContain("\n");
    expect(prompt).toContain(
      "Do not run git add, git commit",
    );
    expect(prompt).toContain(
      "Only MCP may create the implementation commit after Alpha passes.",
    );

    const result = await new LocalCodexWorkerAdapter(runner).execute(
      order,
      prompt
    );

    expect(captured).toBeDefined();
    expect(captured?.args.slice(0, 2)).toEqual([
      "codex.js",
      "exec",
    ]);
    expect(captured?.args.at(-1)).toBe("-");

    const outputIndex =
      captured?.args.indexOf("--output-last-message") ?? -1;
    const schemaIndex =
      captured?.args.indexOf("--output-schema") ?? -1;

    expect(outputIndex).toBeGreaterThan(1);
    expect(schemaIndex).toBeGreaterThan(outputIndex);
    expect(captured?.args[outputIndex + 1]).toBeDefined();
    expect(captured?.args[schemaIndex + 1]).toBeDefined();
    expect(captured?.stdin).toBe(prompt);
    expect(result).toMatchObject({
      status: "SUCCEEDED",
      resultFileFound: true,
      resultFile,
    });
  });
});
