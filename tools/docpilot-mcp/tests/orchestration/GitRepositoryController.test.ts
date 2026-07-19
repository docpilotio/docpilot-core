import { execFile } from "node:child_process";
import { mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { promisify } from "node:util";
import { afterEach, describe, expect, it } from "vitest";
import { ControlledProcessRunner } from "../../src/orchestration/ControlledProcessRunner.js";
import { GitRepositoryController } from "../../src/orchestration/GitRepositoryController.js";

const exec = promisify(execFile);
describe("GitRepositoryController stabilization", () => {
  const roots: string[] = [];
  afterEach(async () => { await Promise.all(roots.splice(0).map((root) => rm(root, { recursive: true, force: true }))); });
  async function repository(configure = true) {
    const root = await mkdtemp(join(tmpdir(), "docpilot-git-")); roots.push(root); await exec("git", ["init", "-b", "feature/test", root]);
    if (configure) { await exec("git", ["-C", root, "config", "user.email", "test@example.com"]); await exec("git", ["-C", root, "config", "user.name", "Test"]); }
    else { await exec("git", ["-C", root, "-c", "user.email=test@example.com", "-c", "user.name=Test", "commit", "--allow-empty", "-m", "initial"]); await exec("git", ["-C", root, "config", "user.email", ""]); await exec("git", ["-C", root, "config", "user.name", ""]); }
    if (configure) { await writeFile(join(root, "README.md"), "base\n"); await writeFile(join(root, "BASE.md"), "base\n"); await exec("git", ["-C", root, "add", "README.md", "BASE.md"]); await exec("git", ["-C", root, "commit", "-m", "initial"]); }
    return root;
  }
  const controller = () => new GitRepositoryController(new ControlledProcessRunner());

  it("preserves exact staged, unstaged, untracked, deleted, and renamed evidence", async () => {
    const root = await repository(); await mkdir(join(root, "docs")); await writeFile(join(root, "docs", "new.md"), "new");
    await writeFile(join(root, "BASE.md"), "changed\n");
    await exec("git", ["-C", root, "mv", "README.md", "RENAMED.md"]);
    const evidence = await controller().collectEvidence(root, await controller().resolveHead(root));
    expect(evidence.stagedFiles).toContain("RENAMED.md"); expect(evidence.renamedFiles).toContain("RENAMED.md"); expect(evidence.changedFiles).toContain("BASE.md"); expect(evidence.untrackedFiles).toContain("docs/new.md");
  });

  it("blocks pre-existing staged content", async () => {
    const root = await repository(); const git = controller(); const head = await git.resolveHead(root);
    await writeFile(join(root, "README.md"), "staged\n"); await exec("git", ["-C", root, "add", "README.md"]);
    await expect(git.createCommit(root, ["README.md"], "candidate", head)).rejects.toThrow("Pre-existing staged files");
    expect((await exec("git", ["-C", root, "diff", "--cached", "--name-only"])).stdout.trim()).toBe("README.md");
  });

  it("restores its own staged candidate when commit fails", async () => {
    const root = await repository(false); const git = controller(); const head = await git.resolveHead(root); await writeFile(join(root, "candidate.md"), "candidate\n");
    await expect(git.createCommit(root, ["candidate.md"], "candidate", head)).rejects.toThrow("Commit creation failed");
    expect((await exec("git", ["-C", root, "diff", "--cached", "--name-only"])).stdout.trim()).toBe("");
    expect((await exec("git", ["-C", root, "status", "--porcelain"])).stdout).toContain("?? candidate.md");
    expect(await git.resolveHead(root)).toBe(head);
  });
});
