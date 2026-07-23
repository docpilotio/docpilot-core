import {
  createWriteStream,
} from "node:fs";
import {
  mkdir,
  readFile,
  rename,
  rm,
  writeFile,
} from "node:fs/promises";
import { finished } from "node:stream/promises";
import {
  dirname,
  resolve,
} from "node:path";
import type {
  CodexImplementationResult,
  CodexWorkerExecution,
  ImplementationWorkOrder,
} from "../model/ImplementationOrchestration.js";
import {
  assertPathInside,
  type ProcessRunner,
} from "./ControlledProcessRunner.js";

export interface CodexWorkerAdapter {
  execute(
    workOrder: ImplementationWorkOrder,
    prompt: string,
    signal?: AbortSignal,
  ): Promise<CodexWorkerExecution>;
}

export class LocalCodexWorkerAdapter implements CodexWorkerAdapter {
  public constructor(private readonly runner: ProcessRunner) {}

  public async execute(
    workOrder: ImplementationWorkOrder,
    prompt: string,
    signal?: AbortSignal,
  ): Promise<CodexWorkerExecution> {
    const resultPath = workOrder.runtime?.resultFile ?? await assertPathInside(
      workOrder.repository.rootPath,
      resolve(
        workOrder.repository.rootPath,
        workOrder.resultContract.resultFile,
      ),
      "resultFile",
    );

    const resultDirectory = dirname(resultPath);
    const schemaPath = workOrder.runtime?.schemaFile ?? await assertPathInside(
      workOrder.repository.rootPath,
      resolve(
        resultDirectory,
        `${workOrder.id}.output-schema.json`,
      ),
      "resultSchema",
    );
    const jsonlPath = workOrder.runtime?.jsonlFile;
    const diagnosticsPath = workOrder.runtime?.diagnosticsFile;

    await mkdir(resultDirectory, { recursive: true });
    if (jsonlPath !== undefined) await mkdir(dirname(jsonlPath), { recursive: true });
    if (diagnosticsPath !== undefined) await mkdir(dirname(diagnosticsPath), { recursive: true });
    await mkdir(dirname(schemaPath), { recursive: true });
    await rm(resultPath, { force: true });
    await writeFile(
      schemaPath,
      `${JSON.stringify(
        createCodexImplementationResultSchema(workOrder),
        null,
        2,
      )}\n`,
      "utf8",
    );

    let processResult;
    let streamedJsonl = false;
    const jsonlStream = jsonlPath === undefined ? undefined : createWriteStream(jsonlPath, { flags: "w" });

    try {
      processResult = await this.runner.execute(
        {
          executable: workOrder.execution.codexCommand,
          args: buildCodexResultCaptureArguments(
            workOrder.execution.codexArguments,
            resultPath,
            schemaPath,
          ),
          stdin: prompt,
          workingDirectory: workOrder.repository.workingDirectory,
          repositoryRoot: workOrder.repository.rootPath,
          timeoutSeconds: workOrder.execution.timeoutSeconds,
          maxOutputCharacters:
            workOrder.execution.maxOutputCharacters,
          environmentAllowlist:
            workOrder.execution.environmentAllowlist,
          ...(jsonlStream === undefined ? {} : {
            onStdoutChunk: (chunk: Buffer) => {
              streamedJsonl = true;
              jsonlStream.write(chunk);
            },
          }),
        },
        signal,
      );
    } finally {
      if (jsonlStream !== undefined) {
        jsonlStream.end();
        await finished(jsonlStream);
      }
      if (workOrder.runtime === undefined) await rm(schemaPath, { force: true });
    }

    const resultFileFound = await readFile(resultPath, "utf8").then(
      () => true,
      () => false,
    );

    if (jsonlPath !== undefined && !streamedJsonl) await atomicWrite(jsonlPath, processResult.stdout);
    const jsonlContent = jsonlPath === undefined ? processResult.stdout : await readFile(jsonlPath, "utf8");
    const jsonl = validateJsonLines(jsonlContent);
    if (diagnosticsPath !== undefined) await atomicWrite(diagnosticsPath, `${JSON.stringify({
      schemaVersion: "1.0", workOrderId: workOrder.id, status: processResult.status,
      exitCode: processResult.exitCode, signal: processResult.signal,
      stderr: processResult.stderr, outputTruncated: processResult.outputTruncated,
      timedOut: processResult.timedOut, cancelled: processResult.cancelled,
      terminationSteps: processResult.terminationSteps, invalidJsonlLines: jsonl.invalidLines,
    }, null, 2)}\n`);

    return {
      schemaVersion: "1.0",
      rfcId: workOrder.rfcId,
      workOrderId: workOrder.id,
      status:
        processResult.status === "PASSED"
          ? "SUCCEEDED"
          : processResult.status === "TIMED_OUT"
            ? "TIMED_OUT"
            : processResult.status === "CANCELLED"
              ? "CANCELLED"
              : "FAILED",
      ...(processResult.exitCode === undefined
        ? {}
        : { exitCode: processResult.exitCode }),
      stdout: processResult.stdout,
      stderr: processResult.stderr,
      outputTruncated: processResult.outputTruncated,
      resultFileFound,
      ...(resultFileFound
        ? { resultFile: workOrder.runtime === undefined ? workOrder.resultContract.resultFile : resultPath }
        : {}),
      ...(jsonlPath === undefined ? {} : { jsonlEventsSaved: jsonl.valid && jsonl.eventCount > 0, jsonlFile: jsonlPath }),
      ...(workOrder.runtime === undefined ? {} : { schemaFile: schemaPath }),
      ...(diagnosticsPath === undefined ? {} : { diagnosticsFile: diagnosticsPath }),
      warnings: resultFileFound
        ? []
        : [
            "Codex CLI did not capture the required final result.",
          ],
      errors: [],
    };
  }
}

export function buildCodexResultCaptureArguments(
  configuredArguments: readonly string[],
  resultPath: string,
  schemaPath: string,
): string[] {
  const promptIndexes = configuredArguments
    .map((argument, index) => argument === "-" ? index : -1)
    .filter((index) => index >= 0);

  if (
    promptIndexes.length > 1 ||
    (
      promptIndexes.length === 1 &&
      promptIndexes[0] !== configuredArguments.length - 1
    )
  ) {
    throw new Error(
      "Codex stdin prompt marker must appear at most once and only as the final argument.",
    );
  }

  if (
    configuredArguments.some(
      (argument) =>
        argument === "--output-last-message" ||
        argument === "-o" ||
        argument === "--output-schema" ||
        argument === "--json" ||
        argument === "--ephemeral",
    )
  ) {
    throw new Error(
      "Work Order codexArguments must not override MCP result capture options.",
    );
  }

  const baseArguments =
    configuredArguments.at(-1) === "-"
      ? configuredArguments.slice(0, -1)
      : [...configuredArguments];

  return [
    ...baseArguments,
    "--json",
    "--ephemeral",
    "--output-last-message",
    resultPath,
    "--output-schema",
    schemaPath,
    "-",
  ];
}

function validateJsonLines(value: string): { valid: boolean; eventCount: number; invalidLines: number[] } {
  const lines = value.split(/\r?\n/).filter((line) => line.trim() !== "");
  const invalidLines: number[] = [];
  for (const [index, line] of lines.entries()) {
    try {
      const event: unknown = JSON.parse(line);
      if (typeof event !== "object" || event === null || Array.isArray(event)) invalidLines.push(index + 1);
    } catch {
      invalidLines.push(index + 1);
    }
  }
  return { valid: invalidLines.length === 0, eventCount: lines.length, invalidLines };
}

async function atomicWrite(path: string, content: string): Promise<void> {
  const temporary = `${path}.tmp-${process.pid}`;
  await writeFile(temporary, content, "utf8");
  await rename(temporary, path);
}

export function createCodexImplementationResultSchema(
  workOrder: ImplementationWorkOrder,
): Record<string, unknown> {
  const stringArray = {
    type: "array",
    items: { type: "string" },
  };

  return {
    $schema: "https://json-schema.org/draft/2020-12/schema",
    type: "object",
    additionalProperties: false,
    required: [
      "schemaVersion",
      "rfcId",
      "workOrderId",
      "implementation",
      "reportedFiles",
      "verification",
      "review",
      "git",
    ],
    properties: {
      schemaVersion: {
        type: "string",
        const: workOrder.resultContract.expectedSchemaVersion,
      },
      rfcId: {
        type: "string",
        const: workOrder.rfcId,
      },
      workOrderId: {
        type: "string",
        const: workOrder.id,
      },
      implementation: {
        type: "object",
        additionalProperties: false,
        required: [
          "status",
          "summary",
          "implemented",
          "notImplemented",
        ],
        properties: {
          status: {
            type: "string",
            enum: [
              "FAILED",
              "BLOCKED",
              "PASSED_WITH_LIMITATIONS",
              "PASSED",
            ],
          },
          summary: { type: "string" },
          implemented: stringArray,
          notImplemented: stringArray,
        },
      },
      reportedFiles: {
        type: "object",
        additionalProperties: false,
        required: [
          "changed",
          "created",
          "deleted",
        ],
        properties: {
          changed: stringArray,
          created: stringArray,
          deleted: stringArray,
        },
      },
      verification: {
        type: "object",
        additionalProperties: false,
        required: [
          "commandsAttempted",
          "findings",
        ],
        properties: {
          commandsAttempted: stringArray,
          findings: stringArray,
        },
      },
      review: {
        type: "object",
        additionalProperties: false,
        required: [
          "findings",
          "blockers",
          "warnings",
          "knownLimitations",
          "unresolvedItems",
        ],
        properties: {
          findings: stringArray,
          blockers: stringArray,
          warnings: stringArray,
          knownLimitations: stringArray,
          unresolvedItems: stringArray,
        },
      },
      git: {
        type: "object",
        additionalProperties: false,
        required: [
          "commitCreated",
          "pushPerformed",
        ],
        properties: {
          commitCreated: { type: "boolean" },
          pushPerformed: { type: "boolean" },
        },
      },
    },
  };
}

export function validateCodexImplementationResult(
  value: unknown,
  workOrder: ImplementationWorkOrder,
): CodexImplementationResult {
  if (containsDangerousKey(value)) {
    throw new Error(
      "Codex result contains a prohibited object key.",
    );
  }

  if (
    typeof value !== "object" ||
    value === null
  ) {
    throw new Error(
      "Codex result must be a JSON object.",
    );
  }

  const result = value as Partial<CodexImplementationResult>;

  if (
    result.schemaVersion !==
    workOrder.resultContract.expectedSchemaVersion
  ) {
    throw new Error(
      "Codex result schemaVersion does not match the Work Order.",
    );
  }

  if (result.rfcId !== workOrder.rfcId) {
    throw new Error(
      "Codex result rfcId does not match the Work Order.",
    );
  }

  if (result.workOrderId !== workOrder.id) {
    throw new Error(
      "Codex result workOrderId does not match the Work Order.",
    );
  }

  if (
    result.implementation === undefined ||
    result.reportedFiles === undefined ||
    result.verification === undefined ||
    result.review === undefined ||
    result.git === undefined
  ) {
    throw new Error(
      "Codex result is missing required sections.",
    );
  }

  if (
    ![
      "FAILED",
      "BLOCKED",
      "PASSED_WITH_LIMITATIONS",
      "PASSED",
    ].includes(result.implementation.status)
  ) {
    throw new Error(
      "Codex result contains an invalid implementation status.",
    );
  }

  for (
    const list of [
      result.implementation.implemented,
      result.implementation.notImplemented,
      result.reportedFiles.changed,
      result.reportedFiles.created,
      result.reportedFiles.deleted,
      result.verification.commandsAttempted,
      result.verification.findings,
      result.review.findings,
      result.review.blockers,
      result.review.warnings,
      result.review.knownLimitations,
      result.review.unresolvedItems,
    ]
  ) {
    if (
      !Array.isArray(list) ||
      !list.every((item) => typeof item === "string")
    ) {
      throw new Error(
        "Codex result contains an invalid string array.",
      );
    }
  }

  if (
    typeof result.implementation.summary !== "string" ||
    typeof result.git.commitCreated !== "boolean" ||
    typeof result.git.pushPerformed !== "boolean"
  ) {
    throw new Error(
      "Codex result contains invalid scalar fields.",
    );
  }

  return result as CodexImplementationResult;
}

function containsDangerousKey(value: unknown): boolean {
  if (Array.isArray(value)) {
    return value.some(containsDangerousKey);
  }

  if (
    typeof value !== "object" ||
    value === null
  ) {
    return false;
  }

  const entries = Object.entries(
    value as Record<string, unknown>,
  );

  return entries.some(
    ([key, child]) =>
      [
        "__proto__",
        "prototype",
        "constructor",
      ].includes(key) ||
      containsDangerousKey(child),
  );
}
