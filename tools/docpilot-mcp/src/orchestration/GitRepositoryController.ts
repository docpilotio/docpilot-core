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
    return (await this.required(root, ["status", "--porcelain=v1"], "Unable to inspect working tree.")) === "";
  }

  public async collectEvidence(root: string, baselineCommit: string): Promise<RepositoryEvidence> {
    const [branch, headCommit, statusText] = await Promise.all([
      this.resolveBranch(root), this.resolveHead(root),
      this.required(root, ["status", "--porcelain=v1", "--untracked-files=all"], "Unable to collect repository evidence."),
    ]);
    const changedFiles: string[] = [];
    const createdFiles: string[] = [];
    const deletedFiles: string[] = [];
    const renamedFiles: string[] = [];
    const stagedFiles: string[] = [];
    const untrackedFiles: string[] = [];
    for (const line of statusText.split(/\r?\n/).filter(Boolean)) {
      const index = line[0] ?? " ";
      const worktree = line[1] ?? " ";
      const rawPath = line.slice(3);
      const path = normalizeGitPath(rawPath.includes(" -> ") ? rawPath.split(" -> ")[1] ?? rawPath : rawPath);
      if (index !== " " && index !== "?") stagedFiles.push(path);
      if (index === "?" && worktree === "?") untrackedFiles.push(path);
      else if (index === "A" || worktree === "A") createdFiles.push(path);
      else if (index === "D" || worktree === "D") deletedFiles.push(path);
      else if (index === "R" || worktree === "R") renamedFiles.push(path);
      else changedFiles.push(path);
    }
    return {
      schemaVersion: "1.0", branch, baselineCommit, headCommit,
      changedFiles: stable(changedFiles), createdFiles: stable(createdFiles), deletedFiles: stable(deletedFiles),
      renamedFiles: stable(renamedFiles), stagedFiles: stable(stagedFiles), untrackedFiles: stable(untrackedFiles), warnings: [],
    };
  }

  public async createCommit(root: string, files: readonly string[], message: string): Promise<string> {
    if (message.trim() === "") throw new Error("Commit message must not be empty.");
    if (files.length === 0) throw new Error("No authorized files are available to commit.");
    const add = await this.git(root, ["add", "--", ...stable(files)]);
    if (add.status !== "PASSED") throw new Error(`Unable to stage authorized files: ${add.stderr.trim()}`);
    const check = await this.git(root, ["diff", "--cached", "--check"]);
    if (check.status !== "PASSED") throw new Error(`Staged diff validation failed: ${check.stderr.trim()}`);
    const commit = await this.git(root, ["commit", "-m", message]);
    if (commit.status !== "PASSED") throw new Error(`Commit creation failed: ${commit.stderr.trim()}`);
    return this.resolveHead(root);
  }

  public async repositoryExists(root: string): Promise<boolean> {
    return access(resolve(root, ".git")).then(() => true, () => false);
  }

  private async required(root: string, args: string[], message: string): Promise<string> {
    const result = await this.git(root, args);
    if (result.status !== "PASSED") throw new Error(`${message}${result.stderr.trim() === "" ? "" : ` ${result.stderr.trim()}`}`);
    return result.stdout.trim();
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
