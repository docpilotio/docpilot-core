package io.docpilot.release

import java.nio.file.Files
import java.nio.file.Path

public data class GitCandidateState(
    val commit: String,
    val branch: String,
    val clean: Boolean,
    val operationInProgress: Boolean,
    val changedPaths: List<String>,
)

public class GitCandidateInspector(
    private val commandRunner: CommandRunner = CommandRunner(),
) {
    public fun inspect(repository: Path): GitCandidateState {
        val root = repository.toAbsolutePath().normalize()
        require(Files.isDirectory(root.resolve(".git")) || Files.isRegularFile(root.resolve(".git"))) {
            "Not a Git worktree: $root"
        }
        val commit = git(root, "rev-parse", "HEAD").trim()
        require(commit.isCommit()) { "Git returned an invalid commit identity." }
        val branch = git(root, "branch", "--show-current").trim().ifBlank { "DETACHED" }
        val status = git(root, "status", "--porcelain=v1", "--untracked-files=all")
        val changed = status.lineSequence().filter(String::isNotBlank).map { line ->
            line.drop(3).substringAfter(" -> ").replace('\\', '/')
        }.sorted().toList()
        val gitDir = git(root, "rev-parse", "--git-dir").trim().let(root::resolve).normalize()
        val operations = listOf("MERGE_HEAD", "REBASE_HEAD", "CHERRY_PICK_HEAD", "BISECT_LOG")
            .any { Files.exists(gitDir.resolve(it)) } ||
            Files.exists(gitDir.resolve("rebase-merge")) ||
            Files.exists(gitDir.resolve("rebase-apply"))
        return GitCandidateState(commit, branch, changed.isEmpty(), operations, changed)
    }

    public fun diffPaths(repository: Path, comparisonCommit: String): List<String> {
        require(comparisonCommit.isCommit())
        return git(repository, "diff", "--name-only", "$comparisonCommit..HEAD")
            .lineSequence().filter(String::isNotBlank).map { it.replace('\\', '/') }.sorted().toList()
    }

    private fun git(root: Path, vararg arguments: String): String {
        val result = commandRunner.run(root, listOf("git", *arguments), captureOutput = true)
        require(result.exitCode == 0) { "Git command failed: ${arguments.joinToString(" ")}" }
        return result.stdout
    }
}
