import {
  mkdtemp,
  readFile,
  rm,
  writeFile,
} from "node:fs/promises";
import { tmpdir } from "node:os";
import {
  dirname,
  join,
  resolve,
} from "node:path";
import {
  afterEach,
  describe,
  expect,
  it,
} from "vitest";
import type {
  ImplementationWorkOrder,
  ProcessExecutionResult,
} from "../../src/model/ImplementationOrchestration.js";
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

describe("LocalCodexWorkerAdapter result capture", () => {
  const directories: string[] = [];

  afterEach(async () => {
    await Promise.all(
      directories.splice(0).map(
        (path) => rm(
          path,
          {
            recursive: true,
            force: true,
          },
        ),
      ),
    );
  });

  it(
    "uses Codex CLI final-message capture with an identity-bound JSON schema",
    async () => {
      const root = await mkdtemp(
        join(tmpdir(), "docpilot-codex-result-"),
      );

      directories.push(root);

      const order = createWorkOrder(root);
      const runner = new CapturingRunner(order);
      const prompt = renderCodexImplementationPrompt(order);

      expect(prompt).toContain(
        "control-plane runtime artifact",
      );
      expect(prompt).toContain(
        "final assistant message must contain only the JSON object",
      );

      const execution =
        await new LocalCodexWorkerAdapter(runner).execute(
          order,
          prompt,
        );

      expect(execution).toMatchObject({
        status: "SUCCEEDED",
        exitCode: 0,
        resultFileFound: true,
        resultFile: order.resultContract.resultFile,
      });

      expect(runner.request?.stdin).toBe(prompt);
      expect(runner.request?.args.at(-1)).toBe("-");

      const outputIndex =
        runner.request?.args.indexOf(
          "--output-last-message",
        ) ?? -1;

      const schemaIndex =
        runner.request?.args.indexOf(
          "--output-schema",
        ) ?? -1;

      expect(outputIndex).toBeGreaterThan(0);
      expect(schemaIndex).toBeGreaterThan(outputIndex);
      expect(runner.capturedSchema).toMatchObject({
        type: "object",
        additionalProperties: false,
        properties: {
          schemaVersion: {
            const: "1.0",
          },
          rfcId: {
            const: "RFC-9001",
          },
          workOrderId: {
            const: "RFC-9001-abcdef123456",
          },
        },
      });
      expectEveryObjectPropertyToBeRequired(
        runner.capturedSchema,
      );

      const resultPath = join(
        root,
        ".docpilot",
        "results",
        "RFC-9001-abcdef123456.json",
      );

      const result = JSON.parse(
        await readFile(resultPath, "utf8"),
      ) as {
        workOrderId: string;
      };

      expect(result.workOrderId).toBe(order.id);
    },
  );

  it("uses read-only JSONL execution and external artifacts for analysis work orders", async () => {
    const root = await mkdtemp(join(tmpdir(), "docpilot-codex-analysis-"));
    const runtimeRoot = await mkdtemp(join(tmpdir(), "docpilot-codex-runtime-"));
    directories.push(root, runtimeRoot);
    const order = createWorkOrder(root);
    order.mode = "ANALYSIS";
    order.gitPolicy.allowCommit = false;
    order.execution.codexArguments = ["exec", "--sandbox", "read-only", "--cd", root];
    order.runtime = {
      rootPath: runtimeRoot,
      repositoryKey: "repository-key",
      lockDirectory: join(runtimeRoot, "locks", "repository-key", "orchestration-lock"),
      jsonlFile: join(runtimeRoot, "logs", "repository-key", `${order.id}.jsonl`),
      resultFile: join(runtimeRoot, "results", "repository-key", `${order.id}.json`),
      schemaFile: join(runtimeRoot, "schemas", "repository-key", `${order.id}.json`),
      diagnosticsFile: join(runtimeRoot, "diagnostics", "repository-key", `${order.id}.json`),
    };
    const runner = new CapturingRunner(order, '{"type":"thread.started"}\n{"type":"turn.completed"}\n');

    const execution = await new LocalCodexWorkerAdapter(runner).execute(
      order,
      renderCodexImplementationPrompt(order),
    );

    expect(runner.request?.args).toEqual(expect.arrayContaining(["--sandbox", "read-only", "--json", "--ephemeral"]));
    expect(execution).toMatchObject({
      status: "SUCCEEDED",
      resultFile: resolve(order.runtime.resultFile),
      resultFileFound: true,
      jsonlEventsSaved: true,
      jsonlFile: resolve(order.runtime.jsonlFile),
      schemaFile: resolve(order.runtime.schemaFile),
      diagnosticsFile: resolve(order.runtime.diagnosticsFile),
    });
    expect(await readFile(order.runtime.jsonlFile, "utf8")).toBe('{"type":"thread.started"}\n{"type":"turn.completed"}\n');
    expect(JSON.parse(await readFile(order.runtime.diagnosticsFile, "utf8"))).toMatchObject({
      workOrderId: order.id,
      outputTruncated: false,
      exitCode: 0,
    });
    await expect(readFile(join(root, ".docpilot", "results", `${order.id}.json`), "utf8")).rejects.toThrow();
  });
});

function expectEveryObjectPropertyToBeRequired(
  schema: unknown,
): void {
  if (typeof schema !== "object" || schema === null) {
    return;
  }

  const node = schema as Record<string, unknown>;
  const properties = node.properties;

  if (typeof properties === "object" && properties !== null) {
    expect(node.required).toEqual(
      Object.keys(properties as Record<string, unknown>),
    );
  }

  for (const value of Object.values(node)) {
    expectEveryObjectPropertyToBeRequired(value);
  }
}

class CapturingRunner implements ProcessRunner {
  public request: ProcessRequest | undefined;
  public capturedSchema: unknown;

  public constructor(
    private readonly order: ImplementationWorkOrder,
    private readonly stdout = "",
  ) {}

  public async execute(
    request: ProcessRequest,
  ): Promise<ProcessExecutionResult> {
    this.request = request;

    const outputIndex = request.args.indexOf(
      "--output-last-message",
    );

    const schemaIndex = request.args.indexOf(
      "--output-schema",
    );

    if (
      outputIndex < 0 ||
      schemaIndex < 0
    ) {
      throw new Error(
        "Codex result capture arguments are missing.",
      );
    }

    const outputPath = request.args[outputIndex + 1];
    const schemaPath = request.args[schemaIndex + 1];

    if (
      outputPath === undefined ||
      schemaPath === undefined
    ) {
      throw new Error(
        "Codex result capture paths are missing.",
      );
    }

    this.capturedSchema = JSON.parse(
      await readFile(schemaPath, "utf8"),
    );

    await writeFile(
      outputPath,
      JSON.stringify(
        {
          schemaVersion: "1.0",
          rfcId: this.order.rfcId,
          workOrderId: this.order.id,
          implementation: {
            status: "PASSED",
            summary: "Created the approved smoke document.",
            implemented: [
              "Created the approved smoke document.",
            ],
            notImplemented: [],
          },
          reportedFiles: {
            changed: [],
            created: [
              "tools/docpilot-mcp/docs/e2e-orchestration-smoke.md",
            ],
            deleted: [],
          },
          verification: {
            commandsAttempted: [],
            findings: [],
          },
          review: {
            findings: [],
            blockers: [],
            warnings: [],
            knownLimitations: [],
            unresolvedItems: [],
          },
          git: {
            commitCreated: false,
            pushPerformed: false,
          },
        },
        null,
        2,
      ),
      "utf8",
    );
    if (this.stdout !== "") this.request.onStdoutChunk?.(Buffer.from(this.stdout, "utf8"));

    return {
      status: "PASSED",
      exitCode: 0,
      stdout: this.stdout,
      stderr: "",
      outputTruncated: false,
      timedOut: false,
      cancelled: false,
      terminationSteps: [],
    };
  }
}

function createWorkOrder(
  root: string,
): ImplementationWorkOrder {
  return {
    schemaVersion: "1.0",
    id: "RFC-9001-abcdef123456",
    rfcId: "RFC-9001",
    repository: {
      rootPath: root,
      baselineBranch: "test/e2e",
      baselineCommit:
        "abcdef123456abcdef123456abcdef123456abcd",
      workingDirectory: root,
    },
    objective: {
      goal: "Create the approved smoke document.",
      approvedPlan: [
        "Create only the approved smoke document.",
      ],
      acceptanceCriteria: [
        "The approved smoke document exists.",
      ],
      alphaCriteria: [],
    },
    scope: {
      allowedPaths: [
        "tools/docpilot-mcp/docs/e2e-orchestration-smoke.md",
      ],
      forbiddenPaths: [],
      allowUntrackedFiles: true,
      allowDependencyChanges: false,
      allowBuildConfigurationChanges: false,
      allowPublicApiChanges: false,
    },
    execution: {
      codexCommand: "codex",
      codexArguments: [
        "exec",
        "-",
      ],
      timeoutSeconds: 60,
      maxOutputCharacters: 10000,
      environmentAllowlist: [
        "PATH",
      ],
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
      allowCommit: true,
      requireUserApprovalForPush: true,
      allowMainBranchPush: false,
      allowForcePush: false,
    },
    resultContract: {
      resultFile:
        ".docpilot/results/RFC-9001-abcdef123456.json",
      expectedSchemaVersion: "1.0",
    },
    warnings: [],
  };
}
