import { spawn } from "node:child_process";
import { access, realpath } from "node:fs/promises";
import { dirname, isAbsolute, relative, resolve } from "node:path";
import type { ProcessExecutionResult } from "../model/ImplementationOrchestration.js";

export type ProcessRequest = {
  executable: string;
  args: readonly string[];
  stdin?: string;
  workingDirectory: string;
  repositoryRoot: string;
  timeoutSeconds: number;
  maxOutputCharacters: number;
  environmentAllowlist: readonly string[];
  onStdoutChunk?: (chunk: Buffer) => void;
};

export interface ProcessRunner {
  execute(request: ProcessRequest, signal?: AbortSignal): Promise<ProcessExecutionResult>;
}

const SECRET_PATTERN = /((?:api[_-]?key|token|password|authorization)\s*[:=]\s*)([^\s]+)/gi;
const MAX_STDIN_CHARACTERS = 1_000_000;

export function maskSensitiveOutput(value: string): string {
  return value.replace(SECRET_PATTERN, "$1[REDACTED]").replace(/Bearer\s+[^\s]+/gi, "Bearer [REDACTED]");
}

export async function assertPathInside(root: string, candidate: string, label: string): Promise<string> {
  const canonicalRoot = await realpath(root);
  const resolvedCandidate = resolve(candidate);
  const canonicalCandidate = await canonicalizePotentialPath(resolvedCandidate);
  const relation = relative(canonicalRoot, canonicalCandidate);
  if (relation === ".." || relation.startsWith(`..${process.platform === "win32" ? "\\" : "/"}`) || isAbsolute(relation)) {
    throw new Error(`${label} must be inside the repository root.`);
  }
  return canonicalCandidate;
}

export class ControlledProcessRunner implements ProcessRunner {
  public async execute(request: ProcessRequest, signal?: AbortSignal): Promise<ProcessExecutionResult> {
    const cwd = await assertPathInside(request.repositoryRoot, request.workingDirectory, "workingDirectory");
    if (!Number.isInteger(request.timeoutSeconds) || request.timeoutSeconds <= 0) throw new Error("timeoutSeconds must be a positive integer.");
    if (!Number.isInteger(request.maxOutputCharacters) || request.maxOutputCharacters <= 0) throw new Error("maxOutputCharacters must be a positive integer.");
    if (/[\r\n\0]/.test(request.executable) || request.args.some((arg) => /[\r\n\0]/.test(arg))) throw new Error("Process arguments contain prohibited control characters.");
    if (request.stdin !== undefined) {
      if (request.stdin.includes("\0")) throw new Error("Process stdin contains prohibited NUL characters.");
      if (request.stdin.length > MAX_STDIN_CHARACTERS) throw new Error(`Process stdin exceeds the ${MAX_STDIN_CHARACTERS}-character limit.`);
    }
    const environment: NodeJS.ProcessEnv = {};
    for (const name of [...new Set(request.environmentAllowlist)].sort()) {
      const value = process.env[name];
      if (value !== undefined) environment[name] = value;
    }

    const executable = await resolveExecutableForSpawn(
      request.executable,
      environment,
    );

    const windowsScript =
      process.platform === "win32" &&
      /\.(cmd|bat)$/i.test(executable);

    if (
      windowsScript &&
      [executable, ...request.args].some((part) =>
        /["&|<>^%\r\n]/.test(part)
      )
    ) {
      throw new Error(
        "Windows command wrapper rejected shell metacharacters.",
      );
    }

    return new Promise((completion) => {
      let stdout = "";
      let stderr = "";
      let outputTruncated = false;
      let settled = false;
      let terminationKind: "TIMED_OUT" | "CANCELLED" | undefined;
      let inputFailed = false;
      const terminationSteps: string[] = [];
      const append = (current: string, chunk: Buffer): string => {
        const remaining = request.maxOutputCharacters - current.length;
        if (remaining <= 0) { outputTruncated = true; return current; }
        const text = chunk.toString("utf8");
        if (text.length <= remaining) return current + text;
        outputTruncated = true;
        return current + text.slice(0, remaining);
      };
      const commandInterpreter =
        environment.ComSpec ??
        environment.COMSPEC ??
        process.env.ComSpec ??
        process.env.COMSPEC ??
        "cmd.exe";

      const child = spawn(
        windowsScript ? commandInterpreter : executable,
        windowsScript
          ? ["/d", "/c", executable, ...request.args]
          : [...request.args],
        {
          cwd,
          env: environment,
          shell: false,
          windowsHide: true,
          detached: process.platform !== "win32",
        },
      );
      child.stdout.on("data", (chunk: Buffer) => {
        request.onStdoutChunk?.(chunk);
        stdout = append(stdout, chunk);
      });
      child.stderr.on("data", (chunk: Buffer) => { stderr = append(stderr, chunk); });
      const finish = (result: Omit<ProcessExecutionResult, "timedOut" | "cancelled" | "terminationSteps">): void => {
        if (settled) return;
        settled = true;
        clearTimeout(timeout);
        clearTimeout(forceTimer);
        clearTimeout(finalTimer);
        signal?.removeEventListener("abort", abort);
        child.stdin.removeAllListeners(); child.stdout.removeAllListeners(); child.stderr.removeAllListeners();
        child.stdin.destroy(); child.stdout.destroy(); child.stderr.destroy();
        completion({ ...result, stdout: maskSensitiveOutput(stdout), stderr: maskSensitiveOutput(stderr), outputTruncated, timedOut: result.status === "TIMED_OUT", cancelled: result.status === "CANCELLED", terminationSteps: [...terminationSteps] });
      };
      let forceTimer: NodeJS.Timeout | undefined;
      let finalTimer: NodeJS.Timeout | undefined;
      const terminate = (kind: "TIMED_OUT" | "CANCELLED"): void => {
        if (terminationKind !== undefined || settled) return;
        terminationKind = kind;
        terminationSteps.push("GRACEFUL_REQUESTED");
        if (process.platform === "win32") terminateTree(child.pid, true, terminationSteps);
        try { child.kill("SIGTERM"); terminationSteps.push("DIRECT_SIGTERM"); } catch (error: unknown) { terminationSteps.push(`DIRECT_TERMINATION_ERROR:${error instanceof Error ? maskSensitiveOutput(error.message) : "unknown"}`); }
        if (process.platform !== "win32") terminateTree(child.pid, false, terminationSteps);
        forceTimer = setTimeout(() => { terminationSteps.push("FORCE_REQUESTED"); try { child.kill("SIGKILL"); terminationSteps.push("DIRECT_SIGKILL"); } catch (error: unknown) { terminationSteps.push(`DIRECT_FORCE_ERROR:${error instanceof Error ? maskSensitiveOutput(error.message) : "unknown"}`); } terminateTree(child.pid, true, terminationSteps); finalTimer = setTimeout(() => finish({ status: kind, stdout: "", stderr: "", outputTruncated }), 1000); }, 500);
      };
      const abort = (): void => terminate("CANCELLED");
      signal?.addEventListener("abort", abort, { once: true });
      const timeout = setTimeout(() => terminate("TIMED_OUT"), request.timeoutSeconds * 1000);
      child.stdin.on("error", (error: NodeJS.ErrnoException) => {
        if (settled || error.code === "EPIPE") return;
        inputFailed = true;
        stderr = append(stderr, Buffer.from(error.message));
        try { child.kill("SIGTERM"); } catch { /* close/error handlers complete the result */ }
      });
      child.stdin.end(request.stdin);
      child.on("error", (error) => { stderr = append(stderr, Buffer.from(error.message)); finish({ status: "FAILED", stdout: "", stderr: "", outputTruncated }); });
      child.on("close", (code, signalName) => {
        const result = { status: terminationKind ?? (code === 0 && !inputFailed ? "PASSED" as const : "FAILED" as const), ...(code === null ? {} : { exitCode: code }), ...(signalName === null ? {} : { signal: signalName }), stdout: "", stderr: "", outputTruncated };
        if (terminationKind !== undefined) { terminateTree(child.pid, true, terminationSteps); setTimeout(() => finish(result), 150); }
        else finish(result);
      });
    });
  }
}

async function resolveExecutableForSpawn(
  executable: string,
  environment: NodeJS.ProcessEnv,
): Promise<string> {
  if (
    process.platform !== "win32" ||
    isAbsolute(executable) ||
    /[\\/]/.test(executable) ||
    /\.[^\\/]+$/.test(executable)
  ) {
    return executable;
  }

  const pathValue =
    environment.PATH ??
    environment.Path ??
    process.env.PATH ??
    process.env.Path ??
    "";

  const pathExtensions = (
    environment.PATHEXT ??
    process.env.PATHEXT ??
    ".COM;.EXE;.BAT;.CMD"
  )
    .split(";")
    .map((extension) => extension.trim())
    .filter((extension) => extension !== "")
    .map((extension) =>
      extension.startsWith(".") ? extension : `.${extension}`
    );

  const candidateNames = [
    ...pathExtensions.map(
      (extension) => `${executable}${extension}`
    ),
    executable,
  ];

  for (const rawDirectory of pathValue.split(";")) {
    const directory = rawDirectory
      .trim()
      .replace(/^"(.*)"$/, "$1");

    if (directory === "") continue;

    for (const candidateName of candidateNames) {
      const candidate = resolve(directory, candidateName);

      if (
        await access(candidate).then(
          () => true,
          () => false,
        )
      ) {
        return candidate;
      }
    }
  }

  return executable;
}

async function canonicalizePotentialPath(candidate: string): Promise<string> {
  let existing = candidate; const missing: string[] = [];
  while (true) {
    try { return resolve(await realpath(existing), ...missing.reverse()); }
    catch {
      const parent = dirname(existing);
      if (parent === existing) return candidate;
      missing.push(candidateSegment(existing)); existing = parent;
    }
  }
}

function candidateSegment(path: string): string { const parent = dirname(path); return path.slice(parent.length).replace(/^[/\\]/, ""); }

function terminateTree(pid: number | undefined, force: boolean, diagnostics: string[]): void {
  if (pid === undefined) { diagnostics.push("PROCESS_ID_UNAVAILABLE"); return; }
  try {
    if (process.platform === "win32") {
      const child = spawn("taskkill.exe", ["/PID", String(pid), "/T", ...(force ? ["/F"] : [])], { shell: false, windowsHide: true, stdio: "ignore" });
      child.unref();
      diagnostics.push(force ? "WINDOWS_TREE_FORCE" : "WINDOWS_TREE_GRACEFUL");
    } else {
      process.kill(-pid, force ? "SIGKILL" : "SIGTERM");
      diagnostics.push(force ? "POSIX_GROUP_SIGKILL" : "POSIX_GROUP_SIGTERM");
    }
  } catch (error: unknown) {
    const message = error instanceof Error ? maskSensitiveOutput(error.message) : "unknown termination error";
    diagnostics.push(`TERMINATION_ERROR:${message}`);
  }
}
