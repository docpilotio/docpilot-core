import { mkdtemp, realpath, rm, stat } from "node:fs/promises";
import { tmpdir } from "node:os";
import { isAbsolute, join } from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { OrchestrationRuntime } from "../../src/orchestration/OrchestrationRuntime.js";

describe("OrchestrationRuntime", () => {
  const roots: string[] = [];

  afterEach(async () => {
    await Promise.all(roots.splice(0).map((root) => rm(root, { recursive: true, force: true })));
  });

  it("isolates repository and work-order artifacts below an explicit external root", async () => {
    const repository = await mkdtemp(join(tmpdir(), "docpilot-runtime-repository-"));
    const runtimeRoot = await mkdtemp(join(tmpdir(), "docpilot-runtime-root-"));
    roots.push(repository, runtimeRoot);
    const runtime = new OrchestrationRuntime(runtimeRoot);
    const first = await runtime.createArtifacts(repository, "RFC-0044-first");
    const second = await runtime.createArtifacts(repository, "RFC-0044-second");

    expect(first).toBeDefined();
    expect(first?.repositoryKey).toBe(second?.repositoryKey);
    expect(first?.jsonlFile).not.toBe(second?.jsonlFile);
    expect(first?.rootPath).toBe(await realpath(runtimeRoot));
    expect(isAbsolute(first?.resultFile ?? "")).toBe(true);
    expect(first?.resultFile.startsWith(await realpath(runtimeRoot))).toBe(true);
    for (const directory of ["state", "locks", "logs", "results", "schemas", "diagnostics", "handoffs"]) {
      expect((await stat(join(runtimeRoot, directory))).isDirectory()).toBe(true);
    }
  });

  it("preserves the legacy fallback when no runtime root is configured", async () => {
    const repository = await mkdtemp(join(tmpdir(), "docpilot-runtime-legacy-"));
    roots.push(repository);
    const runtime = new OrchestrationRuntime();
    expect(await runtime.stateFilePath()).toBeUndefined();
    expect(await runtime.createArtifacts(repository, "legacy")).toBeUndefined();
  });

  it("rejects a relative runtime root", async () => {
    await expect(new OrchestrationRuntime("relative-runtime").stateFilePath()).rejects.toThrow("absolute path");
  });
});
