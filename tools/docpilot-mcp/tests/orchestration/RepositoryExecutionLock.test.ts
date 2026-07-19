import { spawn } from "node:child_process";
import { mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { RepositoryExecutionLock } from "../../src/orchestration/RepositoryExecutionLock.js";

describe("RepositoryExecutionLock", () => {
  const roots: string[] = [];
  afterEach(async () => { await Promise.all(roots.splice(0).map((root) => rm(root, { recursive: true, force: true }))); });
  async function root() { const value = await mkdtemp(join(tmpdir(), "docpilot-lock-")); roots.push(value); return value; }

  it("atomically excludes a second owner and releases normally", async () => {
    const repository = await root(); const manager = new RepositoryExecutionLock();
    const first = await manager.acquire(repository, "RFC-0039-one", "RFC-0039");
    await expect(manager.acquire(repository, "RFC-0039-two", "RFC-0039")).rejects.toThrow("live process owns");
    expect((await manager.inspect(repository)).state).toBe("ACTIVE");
    await first.release(); expect((await manager.inspect(repository)).state).toBe("ABSENT");
  });

  it("uses independent repository locks", async () => {
    const one = await root(); const two = await root(); const manager = new RepositoryExecutionLock();
    const first = await manager.acquire(one, "one", "RFC-0039"); const second = await manager.acquire(two, "two", "RFC-0039");
    await first.release(); await second.release();
  });

  it("recovers only a demonstrably dead owner and records recovery", async () => {
    const repository = await root(); const runtime = join(repository, ".docpilot", "orchestration-lock"); await mkdir(runtime, { recursive: true });
    const now = new Date().toISOString();
    await writeFile(join(runtime, "lock.json"), JSON.stringify({ schemaVersion: "1.0", repositoryIdentity: await import("node:fs/promises").then(({ realpath }) => realpath(repository)), workOrderId: "old", rfcId: "RFC-0039", pid: 2147483647, processStartedAt: now, acquiredAt: now, hostname: "dead-host" }));
    const manager = new RepositoryExecutionLock(); expect((await manager.inspect(repository)).state).toBe("STALE");
    const acquired = await manager.acquire(repository, "new", "RFC-0039"); expect(acquired.staleLockRecovered).toBe(true); await acquired.release();
  });

  it("allows only one winner when stale-lock recovery races", async () => {
    const repository = await root(); const runtime = join(repository, ".docpilot", "orchestration-lock"); await mkdir(runtime, { recursive: true }); const now = new Date().toISOString();
    const canonical = await import("node:fs/promises").then(({ realpath }) => realpath(repository));
    await writeFile(join(runtime, "lock.json"), JSON.stringify({ schemaVersion: "1.0", repositoryIdentity: canonical, workOrderId: "old", rfcId: "RFC-0039", pid: 2147483647, processStartedAt: now, acquiredAt: now, hostname: "dead-host" }));
    const outcomes = await Promise.allSettled([new RepositoryExecutionLock().acquire(repository, "one", "RFC-0039"), new RepositoryExecutionLock().acquire(repository, "two", "RFC-0039")]);
    const winners = outcomes.filter((outcome): outcome is PromiseFulfilledResult<Awaited<ReturnType<RepositoryExecutionLock["acquire"]>>> => outcome.status === "fulfilled");
    expect(winners).toHaveLength(1); expect(outcomes.filter(({ status }) => status === "rejected")).toHaveLength(1); await winners[0]?.value.release();
  });

  it("blocks malformed metadata instead of overwriting it", async () => {
    const repository = await root(); const runtime = join(repository, ".docpilot", "orchestration-lock"); await mkdir(runtime, { recursive: true }); await writeFile(join(runtime, "lock.json"), "{truncated");
    const manager = new RepositoryExecutionLock(); expect(await manager.inspect(repository)).toMatchObject({ state: "RECOVERY_REQUIRED" });
    await expect(manager.acquire(repository, "new", "RFC-0039")).rejects.toThrow("malformed JSON");
    expect(await readFile(join(runtime, "lock.json"), "utf8")).toBe("{truncated");
  });

  it("enforces exclusion across real Node processes", async () => {
    const repository = await root(); const helper = resolve("tests/support/executionLockWorker.ts");
    const child = spawn(process.execPath, ["--import", "tsx", helper, repository], { cwd: resolve("."), stdio: ["pipe", "pipe", "pipe"], windowsHide: true });
    await new Promise<void>((resolveReady, reject) => { child.stdout.once("data", (chunk) => String(chunk).includes("LOCKED") ? resolveReady() : reject(new Error(String(chunk)))); child.once("error", reject); });
    const manager = new RepositoryExecutionLock(); await expect(manager.acquire(repository, "parent", "RFC-0039")).rejects.toThrow("live process owns");
    child.stdin.write("release"); child.stdin.end(); await new Promise<void>((resolveExit) => child.once("close", () => resolveExit()));
    const acquired = await manager.acquire(repository, "parent", "RFC-0039"); await acquired.release();
  }, 15_000);
});
