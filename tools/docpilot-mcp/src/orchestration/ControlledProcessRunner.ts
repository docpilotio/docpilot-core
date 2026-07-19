import { spawn } from "node:child_process";
import { realpath } from "node:fs/promises";
import { isAbsolute, relative, resolve } from "node:path";
import type { ProcessExecutionResult } from "../model/ImplementationOrchestration.js";

export type ProcessRequest = {
  executable: string;
  args: readonly string[];
  workingDirectory: string;
  repositoryRoot: string;
  timeoutSeconds: number;
  maxOutputCharacters: number;
  environmentAllowlist: readonly string[];
};

export interface ProcessRunner {
  execute(request: ProcessRequest, signal?: AbortSignal): Promise<ProcessExecutionResult>;
}

const SECRET_PATTERN = /((?:api[_-]?key|token|password|authorization)\s*[:=]\s*)([^\s]+)/gi;

export function maskSensitiveOutput(value: string): string {
  return value.replace(SECRET_PATTERN, "$1[REDACTED]").replace(/Bearer\s+[^\s]+/gi, "Bearer [REDACTED]");
}

export async function assertPathInside(root: string, candidate: string, label: string): Promise<string> {
  const canonicalRoot = await realpath(root);
  const resolvedCandidate = resolve(candidate);
  const canonicalCandidate = await realpath(resolvedCandidate).catch(() => resolvedCandidate);
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
    if (/[\r\n\0]/.test(request.executable) || request.args.some((arg) => /[\0]/.test(arg))) throw new Error("Process arguments contain prohibited control characters.");
    const windowsScript = process.platform === "win32" && /\.(cmd|bat)$/i.test(request.executable);
    if (windowsScript && [request.executable, ...request.args].some((part) => /["&|<>^%\r\n]/.test(part))) throw new Error("Windows command wrapper rejected shell metacharacters.");

    const environment: NodeJS.ProcessEnv = {};
    for (const name of [...new Set(request.environmentAllowlist)].sort()) {
      const value = process.env[name];
      if (value !== undefined) environment[name] = value;
    }

    return new Promise((completion) => {
      let stdout = "";
      let stderr = "";
      let outputTruncated = false;
      let settled = false;
      const append = (current: string, chunk: Buffer): string => {
        const combined = current + chunk.toString("utf8");
        if (combined.length <= request.maxOutputCharacters) return combined;
        outputTruncated = true;
        return combined.slice(0, request.maxOutputCharacters);
      };
      const child = spawn(windowsScript ? process.env.ComSpec ?? "cmd.exe" : request.executable, windowsScript ? ["/d", "/c", request.executable, ...request.args] : [...request.args], { cwd, env: environment, shell: false, windowsHide: true });
      child.stdout.on("data", (chunk: Buffer) => { stdout = append(stdout, chunk); });
      child.stderr.on("data", (chunk: Buffer) => { stderr = append(stderr, chunk); });
      const finish = (result: ProcessExecutionResult): void => {
        if (settled) return;
        settled = true;
        clearTimeout(timeout);
        signal?.removeEventListener("abort", abort);
        completion({ ...result, stdout: maskSensitiveOutput(stdout), stderr: maskSensitiveOutput(stderr), outputTruncated });
      };
      const abort = (): void => { child.kill(); finish({ status: "CANCELLED", stdout: "", stderr: "", outputTruncated }); };
      signal?.addEventListener("abort", abort, { once: true });
      const timeout = setTimeout(() => { child.kill(); finish({ status: "TIMED_OUT", stdout: "", stderr: "", outputTruncated }); }, request.timeoutSeconds * 1000);
      child.on("error", (error) => { stderr = append(stderr, Buffer.from(error.message)); finish({ status: "FAILED", stdout: "", stderr: "", outputTruncated }); });
      child.on("close", (code) => finish({ status: code === 0 ? "PASSED" : "FAILED", ...(code === null ? {} : { exitCode: code }), stdout: "", stderr: "", outputTruncated }));
    });
  }
}
