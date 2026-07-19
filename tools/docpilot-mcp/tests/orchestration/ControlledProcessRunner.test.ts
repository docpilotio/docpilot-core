import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { ControlledProcessRunner, maskSensitiveOutput } from "../../src/orchestration/ControlledProcessRunner.js";

describe("ControlledProcessRunner", () => {
  const directories: string[] = [];
  afterEach(async () => { await Promise.all(directories.splice(0).map((path) => rm(path, { recursive: true, force: true }))); });
  const directory = async () => { const path = await mkdtemp(join(tmpdir(), "docpilot-runner-")); directories.push(path); return path; };

  it("executes without a shell and captures structured output", async () => {
    const root = await directory();
    const result = await new ControlledProcessRunner().execute({
      executable: process.execPath, args: ["-e", "process.stdout.write('ok'); process.stderr.write('note')"],
      workingDirectory: root, repositoryRoot: root, timeoutSeconds: 5, maxOutputCharacters: 100, environmentAllowlist: [],
    });
    expect(result).toMatchObject({ status: "PASSED", exitCode: 0, stdout: "ok", stderr: "note", outputTruncated: false });
  });

  it("limits output and masks secrets", async () => {
    const root = await directory();
    const result = await new ControlledProcessRunner().execute({
      executable: process.execPath, args: ["-e", "process.stdout.write('token=secret-' + 'x'.repeat(100))"],
      workingDirectory: root, repositoryRoot: root, timeoutSeconds: 5, maxOutputCharacters: 30, environmentAllowlist: [],
    });
    expect(result.outputTruncated).toBe(true);
    expect(result.stdout).toContain("[REDACTED]");
    expect(result.stdout).not.toContain("secret");
  });

  it("times out and rejects working-directory escape", async () => {
    const root = await directory();
    const runner = new ControlledProcessRunner();
    const result = await runner.execute({ executable: process.execPath, args: ["-e", "setInterval(() => {}, 1000)"], workingDirectory: root, repositoryRoot: root, timeoutSeconds: 1, maxOutputCharacters: 100, environmentAllowlist: [] });
    expect(result.status).toBe("TIMED_OUT");
    expect(result).toMatchObject({ timedOut: true, cancelled: false });
    await expect(runner.execute({ executable: process.execPath, args: [], workingDirectory: resolve(root, ".."), repositoryRoot: root, timeoutSeconds: 1, maxOutputCharacters: 100, environmentAllowlist: [] })).rejects.toThrow("workingDirectory must be inside");
  });

  it("distinguishes cancellation and cleans its listeners", async () => {
    const root = await directory(); const controller = new AbortController();
    setTimeout(() => controller.abort(), 100);
    const result = await new ControlledProcessRunner().execute({ executable: process.execPath, args: ["-e", "setInterval(() => {}, 1000)"], workingDirectory: root, repositoryRoot: root, timeoutSeconds: 5, maxOutputCharacters: 100, environmentAllowlist: [] }, controller.signal);
    expect(result).toMatchObject({ status: "CANCELLED", timedOut: false, cancelled: true });
    expect(result.terminationSteps).toContain("GRACEFUL_REQUESTED");
  });

  it("terminates a spawned process tree on timeout", async () => {
    const root = await directory(); const marker = join(root, "orphan.txt");
    const descendant = `setTimeout(() => require('fs').writeFileSync(${JSON.stringify(marker)}, 'orphan'), 1800)`;
    const parent = `require('child_process').spawn(process.execPath, ['-e', ${JSON.stringify(descendant)}]); setInterval(() => {}, 1000)`;
    const result = await new ControlledProcessRunner().execute({ executable: process.execPath, args: ["-e", parent], workingDirectory: root, repositoryRoot: root, timeoutSeconds: 1, maxOutputCharacters: 100, environmentAllowlist: ["SystemRoot", "ComSpec", "PATH", "Path"] });
    expect(result.status).toBe("TIMED_OUT"); await new Promise((resolveWait) => setTimeout(resolveWait, 1200));
    await expect(import("node:fs/promises").then(({ access }) => access(marker))).rejects.toThrow();
  }, 10_000);

  it("masks common credential formats deterministically", () => {
    expect(maskSensitiveOutput("Authorization=abc Bearer xyz password: nope")).toBe("Authorization=[REDACTED] Bearer [REDACTED] password: [REDACTED]");
  });

  it.runIf(process.platform === "win32")("runs a constrained Windows command wrapper and rejects metacharacters", async () => {
    const root = await directory(); const script = join(root, "safe.cmd"); await writeFile(script, "@echo off\r\necho %1\r\n");
    const runner = new ControlledProcessRunner();
    const result = await runner.execute({ executable: script, args: ["safe-value"], workingDirectory: root, repositoryRoot: root, timeoutSeconds: 5, maxOutputCharacters: 100, environmentAllowlist: ["SystemRoot", "ComSpec"] });
    expect(result).toMatchObject({ status: "PASSED", exitCode: 0 }); expect(result.stdout.trim()).toBe("safe-value");
    await expect(runner.execute({ executable: script, args: ["unsafe&whoami"], workingDirectory: root, repositoryRoot: root, timeoutSeconds: 5, maxOutputCharacters: 100, environmentAllowlist: [] })).rejects.toThrow("shell metacharacters");
  });
});
