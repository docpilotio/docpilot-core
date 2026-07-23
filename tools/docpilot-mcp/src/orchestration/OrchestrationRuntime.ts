import { createHash } from "node:crypto";
import { mkdir, realpath } from "node:fs/promises";
import { isAbsolute, relative, resolve } from "node:path";
import type { OrchestrationRuntimeArtifacts } from "../model/ImplementationOrchestration.js";

export const RUNTIME_ROOT_ENVIRONMENT_VARIABLE = "DOCPILOT_MCP_RUNTIME_ROOT";

export class OrchestrationRuntime {
  public constructor(public readonly rootPath?: string) {}

  public static fromEnvironment(): OrchestrationRuntime {
    const configured = process.env[RUNTIME_ROOT_ENVIRONMENT_VARIABLE]?.trim();
    return new OrchestrationRuntime(configured === undefined || configured === "" ? undefined : resolve(configured));
  }

  public async stateFilePath(): Promise<string | undefined> {
    if (this.rootPath === undefined) return undefined;
    const root = await this.ensureRoot();
    const state = resolve(root, "state");
    await mkdir(state, { recursive: true });
    return resolve(state, "project-state.json");
  }

  public async createArtifacts(repositoryRoot: string, workOrderId: string): Promise<OrchestrationRuntimeArtifacts | undefined> {
    if (this.rootPath === undefined) return undefined;
    const root = await this.ensureRoot();
    const canonicalRepository = await realpath(repositoryRoot);
    const repositoryKey = createHash("sha256").update(canonicalRepository.toLowerCase()).digest("hex").slice(0, 16);
    const base = resolve(root, repositoryKey);
    const directories = {
      lockDirectory: resolve(root, "locks", repositoryKey, "orchestration-lock"),
      logs: resolve(root, "logs", repositoryKey),
      results: resolve(root, "results", repositoryKey),
      schemas: resolve(root, "schemas", repositoryKey),
      diagnostics: resolve(root, "diagnostics", repositoryKey),
    };
    await Promise.all(
      [
        resolve(root, "state"),
        resolve(root, "locks"),
        resolve(root, "logs"),
        resolve(root, "results"),
        resolve(root, "schemas"),
        resolve(root, "diagnostics"),
        resolve(root, "handoffs"),
        resolve(root, "locks", repositoryKey),
        directories.logs,
        directories.results,
        directories.schemas,
        directories.diagnostics,
      ].map((directory) => mkdir(directory, { recursive: true }))
    );
    return {
      rootPath: root,
      repositoryKey,
      lockDirectory: directories.lockDirectory,
      jsonlFile: resolve(directories.logs, `${workOrderId}.jsonl`),
      resultFile: resolve(directories.results, `${workOrderId}.json`),
      schemaFile: resolve(directories.schemas, `${workOrderId}.output-schema.json`),
      diagnosticsFile: resolve(directories.diagnostics, `${workOrderId}.json`),
    };
  }

  private async ensureRoot(): Promise<string> {
    if (this.rootPath === undefined || !isAbsolute(this.rootPath)) throw new Error("DocPilot runtime root must be an absolute path.");
    await mkdir(this.rootPath, { recursive: true });
    const canonical = await realpath(this.rootPath);
    const relation = relative(canonical, resolve(canonical));
    if (relation !== "") throw new Error("DocPilot runtime root could not be canonicalized.");
    return canonical;
  }
}
