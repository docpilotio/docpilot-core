import { access } from "node:fs/promises";
import { resolve } from "node:path";
import type { RepositoryEvidence } from "../model/ImplementationOrchestration.js";
import type { ProcessRunner } from "./ControlledProcessRunner.js";

const ENVIRONMENT = ["PATH", "Path", "SystemRoot", "ComSpec", "PATHEXT", "TEMP", "TMP"];

export class GitRepositoryController {
  public constructor(private readonly runner: ProcessRunner) {}

  public async isRepository(root: string): Promise<boolean> {
    return (await this.git(root, ["rev-parse", "--is-inside-work-tree"])).stdout.trim() === "true";
  }

  public async resolveHead(root: string): Promise<string> {
    return this.required(root, ["rev-parse", "HEAD"], "Unable to resolve repository HEAD.");
  }

  public async resolveBranch(root: string): Promise<string> {
    return this.required(root, ["branch", "--show-current"], "Unable to resolve repository branch.");
  }

  public async commitExists(root: string, commit: string): Promise<boolean> {
    return (await this.git(root, ["cat-file", "-e", `${commit}^{commit}`])).status === "PASSED";
  }

  public async isClean(root: string): Promise<boolean> {
    const evidence = await this.collectEvidence(root, await this.resolveHead(root));
    return allFiles(evidence).every(isRuntimePath) && evidence.stagedFiles.length === 0;
  }

  public async collectEvidence(root: string, baselineCommit: string): Promise<RepositoryEvidence> {
    const [branch, headCommit, statusText] = await Promise.all([
      this.resolveBranch(root), this.resolveHead(root),
      this.required(root, ["status", "--porcelain=v1", "-z", "--untracked-files=all"], "Unable to collect repository evidence.", false),
    ]);
    const changedFiles: string[] = [];
    const createdFiles: string[] = [];
    const deletedFiles: string[] = [];
    const renamedFiles: string[] = [];
    const stagedFiles: string[] = [];
    const untrackedFiles: string[] = [];
    const typeChangedFiles: string[] = [];
    const entries = statusText.split("\0");
    for (let position = 0; position < entries.length; position += 1) {
      const line = entries[position];
      if (line === undefined || line === "") continue;
      const index = line[0] ?? " "; const worktree = line[1] ?? " ";
      const path = normalizeGitPath(line.slice(3));
      if (index !== " " && index !== "?") stagedFiles.push(path);
      if (index === "?" && worktree === "?") untrackedFiles.push(path);
      else if (index === "A" || worktree === "A") createdFiles.push(path);
      else if (index === "D" || worktree === "D") deletedFiles.push(path);
      else if (index === "R" || worktree === "R") { renamedFiles.push(path); position += 1; }
      else if (index === "T" || worktree === "T") typeChangedFiles.push(path);
      else changedFiles.push(path);
    }
    return {
      schemaVersion: "1.0", branch, baselineCommit, headCommit,
      changedFiles: stable(changedFiles), createdFiles: stable(createdFiles), deletedFiles: stable(deletedFiles),
      renamedFiles: stable(renamedFiles), stagedFiles: stable(stagedFiles), untrackedFiles: stable(untrackedFiles), typeChangedFiles: stable(typeChangedFiles), warnings: [],
    };
  }

  public async createCommit(root: string, files: readonly string[], message: string, expectedHead: string): Promise<string> {
    if (message.trim() === "") throw new Error("Commit message must not be empty.");
    if (files.length === 0) throw new Error("No authorized files are available to commit.");
    const before = await this.collectEvidence(root, expectedHead);
    if (before.headCommit !== expectedHead) throw new Error("Repository HEAD changed before commit creation; possible prior commit requires recovery review.");
    if (before.stagedFiles.length > 0) throw new Error("Pre-existing staged files block Implementation Commit creation.");
    const candidates = stable(files);
    let stagedByOperation = false;
    try {
      const add = await this.git(root, ["add", "--", ...candidates]);
      stagedByOperation = true;
      if (add.status !== "PASSED") throw new Error(`Unable to stage authorized files: ${add.stderr.trim()}`);
      const cachedFiles = stable((await this.required(root, ["diff", "--cached", "--name-only", "-z"], "Unable to inspect staged files.", false)).split("\0").filter(Boolean).map(normalizeGitPath));
      if (!same(candidates, cachedFiles)) throw new Error("Staged files do not exactly match the authorized Commit Candidate.");
      const check = await this.git(root, ["diff", "--cached", "--check"]);
      if (check.status !== "PASSED") throw new Error(`Staged diff validation failed: ${check.stderr.trim()}`);
      const commit = await this.git(root, ["commit", "-m", message]);
      if (commit.status !== "PASSED") throw new Error(`Commit creation failed: ${commit.stderr.trim()}`);
      stagedByOperation = false;
    } catch (error: unknown) {
      if (stagedByOperation) {
        const restore = await this.git(root, ["restore", "--staged", "--", ...candidates]);
        if (restore.status !== "PASSED") throw new Error(`${error instanceof Error ? error.message : "Commit creation failed."} Index recovery also failed; manual review is required.`);
      }
      throw error;
    }
    const resultingHead = await this.resolveHead(root);
    if (resultingHead === expectedHead || !await this.commitExists(root, resultingHead)) throw new Error("Commit command completed without a verifiable new commit object.");
    return resultingHead;
  }

  public async repositoryExists(root: string): Promise<boolean> {
    return access(resolve(root, ".git")).then(() => true, () => false);
  }

  private async required(root: string, args: string[], message: string, trim = true): Promise<string> {
    const result = await this.git(root, args);
    if (result.status !== "PASSED") throw new Error(`${message}${result.stderr.trim() === "" ? "" : ` ${result.stderr.trim()}`}`);
    return trim ? result.stdout.trim() : result.stdout;
  }

  private git(root: string, args: string[]) {
    return this.runner.execute({ executable: "git", args, workingDirectory: root, repositoryRoot: root, timeoutSeconds: 30, maxOutputCharacters: 100_000, environmentAllowlist: ENVIRONMENT });
  }
}

export function normalizeGitPath(value: string): string {
  return value.replace(/^"|"$/g, "").replaceAll("\\", "/").replace(/^\.\//, "");
}

function stable(values: readonly string[]): string[] {
  return [...new Set(values)].sort((left, right) => left < right ? -1 : left > right ? 1 : 0);
}

function allFiles(evidence: RepositoryEvidence): string[] { return stable([...evidence.changedFiles, ...evidence.createdFiles, ...evidence.deletedFiles, ...evidence.renamedFiles, ...(evidence.typeChangedFiles ?? []), ...evidence.untrackedFiles]); }
function isRuntimePath(path: string): boolean { return path === "project-state.json" || path.startsWith(".docpilot/"); }
function same(left: readonly string[], right: readonly string[]): boolean { return left.length === right.length && left.every((value, index) => value === right[index]); }
