import { hostname } from "node:os";
import { mkdir, readFile, realpath, rename, rm, writeFile } from "node:fs/promises";
import { relative, resolve } from "node:path";

export const EXECUTION_LOCK_SCHEMA_VERSION = "1.0";
const PROCESS_STARTED_AT = new Date(Date.now() - Math.floor(process.uptime() * 1000)).toISOString();

export type ExecutionLockMetadata = {
  schemaVersion: string;
  repositoryIdentity: string;
  workOrderId: string;
  rfcId: string;
  pid: number;
  processStartedAt: string;
  acquiredAt: string;
  hostname: string;
};

export type ExecutionLockInspection = {
  state: "ABSENT" | "ACTIVE" | "STALE" | "RECOVERY_REQUIRED";
  reason: string;
  metadata?: ExecutionLockMetadata;
};

export type AcquiredExecutionLock = {
  metadata: ExecutionLockMetadata;
  staleLockRecovered: boolean;
  release(): Promise<void>;
};

export class RepositoryExecutionLock {
  public async acquire(repositoryRoot: string, workOrderId: string, rfcId: string): Promise<AcquiredExecutionLock> {
    const paths = await this.paths(repositoryRoot);
    let staleLockRecovered = false;
    for (let attempt = 0; attempt < 3; attempt += 1) {
      try {
        await mkdir(paths.lockDirectory);
        const metadata = this.currentMetadata(paths.root, workOrderId, rfcId);
        try { await writeFile(paths.metadataFile, `${JSON.stringify(metadata, null, 2)}\n`, { encoding: "utf8", flag: "wx" }); }
        catch (error) { await rm(paths.lockDirectory, { recursive: true, force: true }); throw error; }
        return { metadata, staleLockRecovered, release: () => this.release(paths.lockDirectory, paths.metadataFile, metadata) };
      } catch (error: unknown) {
        if (!isAlreadyExists(error)) throw error;
        const inspection = await this.inspect(repositoryRoot);
        if (inspection.state !== "STALE") throw new Error(`Implementation execution lock is unavailable: ${inspection.reason}`);
        const metadata = inspection.metadata;
        if (metadata === undefined) throw new Error("Stale lock recovery requires validated metadata.");
        const quarantine = resolve(dirnameOf(paths.lockDirectory), `orchestration-lock.stale-${metadata.pid}-${metadata.acquiredAt.replace(/[:.]/g, "-")}`);
        try { await rename(paths.lockDirectory, quarantine); }
        catch (renameError: unknown) { if (isMissing(renameError)) continue; throw new Error("Stale lock could not be quarantined safely; manual recovery is required."); }
        await rm(quarantine, { recursive: true, force: false });
        staleLockRecovered = true;
      }
    }
    throw new Error("Implementation execution lock could not be acquired after stale-lock recovery.");
  }

  public async inspect(repositoryRoot: string): Promise<ExecutionLockInspection> {
    const root = await realpath(repositoryRoot);
    const runtimePath = resolve(root, ".docpilot");
    const canonicalRuntime = await realpath(runtimePath).catch((error: unknown) => {
      if (isMissing(error)) return undefined;
      throw error;
    });
    if (canonicalRuntime === undefined) return { state: "ABSENT", reason: "No execution lock is present." };
    const relation = relative(root, canonicalRuntime);
    if (relation === ".." || relation.startsWith(`..${process.platform === "win32" ? "\\" : "/"}`)) return { state: "RECOVERY_REQUIRED", reason: "DocPilot runtime path escapes the repository root." };
    const paths = { root, lockDirectory: resolve(canonicalRuntime, "orchestration-lock"), metadataFile: resolve(canonicalRuntime, "orchestration-lock", "lock.json") };
    let raw: string;
    try { raw = await readFile(paths.metadataFile, "utf8"); }
    catch (error: unknown) {
      if (isMissing(error)) {
        const lockExists = await realpath(paths.lockDirectory).then(() => true, () => false);
        return lockExists
          ? { state: "RECOVERY_REQUIRED", reason: "Execution lock directory exists without readable metadata." }
          : { state: "ABSENT", reason: "No execution lock is present." };
      }
      return { state: "RECOVERY_REQUIRED", reason: "Execution lock metadata cannot be read safely." };
    }
    let value: unknown;
    try { value = JSON.parse(raw); }
    catch { return { state: "RECOVERY_REQUIRED", reason: "Execution lock metadata is malformed JSON." }; }
    const metadata = validateMetadata(value, paths.root);
    if (metadata instanceof Error) return { state: "RECOVERY_REQUIRED", reason: metadata.message };
    const alive = processIsAlive(metadata.pid);
    if (alive === false) return { state: "STALE", reason: "The recorded lock owner process is no longer alive.", metadata };
    if (alive === undefined) return { state: "RECOVERY_REQUIRED", reason: "The lock owner process state could not be determined safely.", metadata };
    if (metadata.pid === process.pid && metadata.processStartedAt !== currentProcessStartedAt()) {
      return { state: "RECOVERY_REQUIRED", reason: "The lock PID is active but its process identity does not match.", metadata };
    }
    return { state: "ACTIVE", reason: "A live process owns the execution lock.", metadata };
  }

  private async paths(repositoryRoot: string) {
    const root = await realpath(repositoryRoot);
    const runtime = resolve(root, ".docpilot");
    await mkdir(runtime, { recursive: true });
    const canonicalRuntime = await realpath(runtime);
    const relation = relative(root, canonicalRuntime);
    if (relation === ".." || relation.startsWith(`..${process.platform === "win32" ? "\\" : "/"}`)) throw new Error("DocPilot runtime path escapes the repository root.");
    const lockDirectory = resolve(canonicalRuntime, "orchestration-lock");
    return { root, lockDirectory, metadataFile: resolve(lockDirectory, "lock.json") };
  }

  private currentMetadata(repositoryIdentity: string, workOrderId: string, rfcId: string): ExecutionLockMetadata {
    return { schemaVersion: EXECUTION_LOCK_SCHEMA_VERSION, repositoryIdentity, workOrderId, rfcId, pid: process.pid, processStartedAt: currentProcessStartedAt(), acquiredAt: new Date().toISOString(), hostname: hostname() };
  }

  private async release(lockDirectory: string, metadataFile: string, owner: ExecutionLockMetadata): Promise<void> {
    let current: unknown;
    try { current = JSON.parse(await readFile(metadataFile, "utf8")); }
    catch (error: unknown) { if (isMissing(error)) return; throw new Error("Execution lock release requires manual recovery because metadata is unreadable."); }
    const validated = validateMetadata(current, owner.repositoryIdentity);
    if (validated instanceof Error || validated.pid !== owner.pid || validated.workOrderId !== owner.workOrderId || validated.acquiredAt !== owner.acquiredAt) throw new Error("Execution lock ownership changed; refusing to remove another owner's lock.");
    await rm(lockDirectory, { recursive: true, force: false });
  }
}

function validateMetadata(value: unknown, repositoryIdentity: string): ExecutionLockMetadata | Error {
  if (typeof value !== "object" || value === null || Array.isArray(value)) return new Error("Execution lock metadata must be an object.");
  const item = value as Record<string, unknown>;
  const keys = Object.keys(item).sort();
  const expected = ["acquiredAt", "hostname", "pid", "processStartedAt", "repositoryIdentity", "rfcId", "schemaVersion", "workOrderId"];
  if (keys.length !== expected.length || keys.some((key, index) => key !== expected[index])) return new Error("Execution lock metadata contains missing or unknown fields.");
  if (item.schemaVersion !== EXECUTION_LOCK_SCHEMA_VERSION) return new Error("Execution lock schemaVersion is unsupported.");
  if (item.repositoryIdentity !== repositoryIdentity) return new Error("Execution lock repository identity does not match the canonical repository root.");
  if (typeof item.workOrderId !== "string" || item.workOrderId === "" || typeof item.rfcId !== "string" || !/^RFC-[0-9]{4}$/.test(item.rfcId)) return new Error("Execution lock identity is invalid.");
  if (typeof item.pid !== "number" || !Number.isSafeInteger(item.pid) || item.pid <= 0) return new Error("Execution lock PID is invalid.");
  for (const field of ["processStartedAt", "acquiredAt"] as const) if (typeof item[field] !== "string" || !isCanonicalIsoTimestamp(item[field])) return new Error(`Execution lock ${field} is invalid.`);
  if (Date.parse(item.acquiredAt as string) > Date.now() + 60_000) return new Error("Execution lock acquisition time is in the future.");
  if (typeof item.hostname !== "string" || item.hostname === "") return new Error("Execution lock hostname is invalid.");
  return item as ExecutionLockMetadata;
}

function currentProcessStartedAt(): string { return PROCESS_STARTED_AT; }
function isCanonicalIsoTimestamp(value: string): boolean { const time = Date.parse(value); return Number.isFinite(time) && new Date(time).toISOString() === value; }
function processIsAlive(pid: number): boolean | undefined { try { process.kill(pid, 0); return true; } catch (error: unknown) { const code = typeof error === "object" && error !== null && "code" in error ? String(error.code) : ""; return code === "ESRCH" ? false : code === "EPERM" ? true : undefined; } }
function isAlreadyExists(error: unknown): boolean { return typeof error === "object" && error !== null && "code" in error && error.code === "EEXIST"; }
function isMissing(error: unknown): boolean { return typeof error === "object" && error !== null && "code" in error && error.code === "ENOENT"; }
function dirnameOf(path: string): string { return resolve(path, ".."); }
