package io.docpilot.release

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReleaseCollectionCoordinatorTest {
    @Test
    fun `collects passing evidence against one clean commit`() {
        val root = gitRepository()
        val evidence = root.resolve("evidence")
        Files.createDirectories(evidence)
        val artifactPlans = REQUIRED_ARTIFACT_KINDS.sortedBy { it.name }.mapIndexed { index, kind ->
            val relative = "evidence/artifact-$index.txt"
            Files.writeString(root.resolve(relative), kind.name)
            ArtifactPlan("artifact-$index", kind, relative, true, REQUIRED_EXECUTIONS.first())
        }
        Files.createDirectories(root.resolve("test-results"))
        Files.writeString(
            root.resolve("test-results/TEST-release.xml"),
            """<testsuite tests="1" failures="0" errors="0" skipped="0"/>""",
        )
        run(root, "git", "add", ".")
        run(root, "git", "commit", "-m", "evidence inputs")
        Files.setLastModifiedTime(
            root.resolve("test-results/TEST-release.xml"),
            FileTime.fromMillis(System.currentTimeMillis() + 60_000),
        )
        val commit = run(root, "git", "rev-parse", "HEAD").trim()
        val commands = REQUIRED_EXECUTIONS.map { id ->
            ExecutionPlan(
                id, id.uppercase().replace('-', '_'), listOf("git", "--version"),
                cleanTest = id == "core-clean-test",
            )
        }
        val checks = REQUIRED_COMPATIBILITY_CHECKS.map {
            ReleaseCheck(it, "PASS", "PASS", EvidenceResult.PASS)
        }
        val output = root.resolve("build/release-evidence/v0.5.0-rc.1")
        val result = ReleaseCollectionCoordinator().collect(
            ReleaseCollectionRequest(
                root, output, "v0.5.0-rc.1", commit, "0.12.3",
                ReleaseContracts("0.3", 1, 1, 1, 1),
                commands, listOf("test-results"), artifactPlans, checks, true,
            ),
        )

        val saved = assertIs<ReleaseCollectionResult.Saved>(result)
        assertEquals(EvidenceResult.PASS, saved.manifest.gate.result)
        assertTrue(Files.isRegularFile(saved.manifestPath))
    }

    @Test
    fun `dirty candidate fails before executing collection`() {
        val root = gitRepository()
        Files.writeString(root.resolve("dirty.txt"), "dirty")
        val commit = run(root, "git", "rev-parse", "HEAD").trim()

        val result = ReleaseCollectionCoordinator().collect(
            ReleaseCollectionRequest(
                root, root.resolve("build/out"), "v0.5.0-rc.1", commit, "0.12.3",
                ReleaseContracts("0.3", 1, 1, 1, 1), emptyList(), emptyList(),
                emptyList(), emptyList(), false,
            ),
        )

        assertEquals(GateFailure.DIRTY_REPOSITORY, assertIs<ReleaseCollectionResult.Failed>(result).reason)
    }

    private fun gitRepository(): Path {
        val root = Files.createTempDirectory("release-git")
        run(root, "git", "init")
        run(root, "git", "config", "user.email", "release@example.invalid")
        run(root, "git", "config", "user.name", "Release Test")
        Files.writeString(root.resolve(".gitignore"), "build/\n")
        Files.writeString(root.resolve("README.md"), "fixture\n")
        run(root, "git", "add", ".")
        run(root, "git", "commit", "-m", "initial")
        return root
    }

    private fun run(root: Path, vararg command: String): String {
        val process = ProcessBuilder(command.toList()).directory(root.toFile()).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { output }
        return output
    }
}
