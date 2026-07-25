package io.docpilot.cli

import io.docpilot.cli.command.review.ReviewCommand
import io.docpilot.core.incremental.specification.ChangeKind
import io.docpilot.core.incremental.specification.IncrementalUpdateTarget
import io.docpilot.core.incremental.specification.ai.AiDocumentationPatchOperation
import io.docpilot.core.incremental.specification.review.*
import io.docpilot.core.model.ApiSpecification
import io.docpilot.core.model.ComponentSpecification
import io.docpilot.core.model.ProjectDescriptor
import io.docpilot.core.model.ProjectSpecification
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReviewCommandWorkflowTest {
    @TempDir
    lateinit var projectRoot: Path

    @Test
    fun `status decide and apply operate persisted bundle across command instances`() {
        val fixture = fixture()

        assertEquals(3, ReviewCommand().execute(statusArguments(fixture)))
        assertEquals(
            0,
            ReviewCommand().execute(
                listOf(
                    "decide", "--project", projectRoot.toString(), "--bundle", fixture.bundlePath.toString(),
                    "--target", "api:removed", "--accept", "--comment", "verified",
                ),
            ),
        )
        assertEquals(0, ReviewCommand().execute(statusArguments(fixture)))
        assertEquals(
            0,
            ReviewCommand().execute(
                listOf(
                    "apply", "--project", projectRoot.toString(), "--bundle", fixture.bundlePath.toString(),
                    "--documentation", fixture.documentationPath.toString(),
                ),
            ),
        )

        val applied = Files.readString(fixture.documentationPath, StandardCharsets.UTF_8)
        assertFalse(applied.contains("api:removed"))
    }

    @Test
    fun `json status exposes stable identity fields`() {
        val fixture = fixture()
        val output = ByteArrayOutputStream()
        val original = System.out
        val exit = try {
            System.setOut(PrintStream(output, true, StandardCharsets.UTF_8))
            ReviewCommand().execute(statusArguments(fixture) + "--json")
        } finally {
            System.setOut(original)
        }

        val json = output.toString(StandardCharsets.UTF_8)
        assertEquals(3, exit)
        assertTrue(json.contains("\"outputFormatVersion\":1"))
        assertTrue(json.contains("\"proposalId\":\"review:"))
        assertTrue(json.contains("\"bundlePath\":"))
        assertTrue(json.contains("\"payloadSha256\":"))
        assertTrue(json.contains("\"status\":\"PENDING_REVIEW\""))
    }

    @Test
    fun `decision comments support utf8 comment files and stale checksum conflicts`() {
        val fixture = fixture()
        val comment = projectRoot.resolve("comment.txt")
        Files.writeString(comment, "검토 완료\n", StandardCharsets.UTF_8)

        val exit = ReviewCommand().execute(
            listOf(
                "decide", "--project", projectRoot.toString(), "--bundle", fixture.bundlePath.toString(),
                "--target", "api:removed", "--reject", "--comment-file", comment.toString(),
                "--payload-sha256", "0".repeat(64), "--json",
            ),
        )

        assertEquals(4, exit)
        val loaded = assertTrueLoad(fixture)
        assertTrue(loaded.decisions.isEmpty())
    }

    @Test
    fun `lifecycle status and verify expose Core aggregate through json`() {
        val fixture = fixture()
        initializeLifecycle(fixture)
        val statusOutput = ByteArrayOutputStream()
        val original = System.out
        val statusExit = try {
            System.setOut(PrintStream(statusOutput, true, StandardCharsets.UTF_8))
            ReviewCommand().execute(
                lifecycleArguments("status", fixture) + listOf(
                    "--documentation", fixture.documentationPath.toString(), "--json",
                ),
            )
        } finally {
            System.setOut(original)
        }

        val statusJson = statusOutput.toString(StandardCharsets.UTF_8)
        assertEquals(0, statusExit)
        assertTrue(statusJson.contains("\"status\":\"LIFECYCLE_STATUS\""))
        assertTrue(statusJson.contains("\"lifecycleState\":\"ACTIVE\""))
        assertTrue(statusJson.contains("\"lifecycleGeneration\":1"))
        assertEquals(
            0,
            ReviewCommand().execute(lifecycleArguments("verify", fixture) + "--json"),
        )
    }

    @Test
    fun `archive defaults to dry run and requires explicit confirm`() {
        val fixture = fixture()
        initializeLifecycle(fixture)
        val dryOutput = ByteArrayOutputStream()
        val original = System.out
        val dryExit = try {
            System.setOut(PrintStream(dryOutput, true, StandardCharsets.UTF_8))
            ReviewCommand().execute(lifecycleArguments("archive", fixture) + "--json")
        } finally {
            System.setOut(original)
        }
        val dryJson = dryOutput.toString(StandardCharsets.UTF_8)
        val planSha = Regex("\"planSha256\":\"([0-9a-f]{64})\"").find(dryJson)!!.groupValues[1]

        assertEquals(0, dryExit)
        assertTrue(dryJson.contains("\"status\":\"DRY_RUN_READY\""))
        assertEquals(ReviewLifecycleState.ACTIVE, lifecycle(fixture).metadata.state)

        val confirmExit = ReviewCommand().execute(
            lifecycleArguments("archive", fixture) + listOf("--confirm", "--plan-sha256", planSha, "--json"),
        )

        assertEquals(0, confirmExit)
        assertEquals(ReviewLifecycleState.ARCHIVED, lifecycle(fixture).metadata.state)
    }

    @Test
    fun `lifecycle mutation mode rejects ambiguous approval and stale plan`() {
        val fixture = fixture()
        initializeLifecycle(fixture)

        assertEquals(
            2,
            ReviewCommand().execute(lifecycleArguments("archive", fixture) + listOf("--dry-run", "--confirm")),
        )
        assertEquals(
            4,
            ReviewCommand().execute(
                lifecycleArguments("archive", fixture) +
                    listOf("--confirm", "--plan-sha256", "0".repeat(64), "--json"),
            ),
        )
        assertEquals(ReviewLifecycleState.ACTIVE, lifecycle(fixture).metadata.state)
    }

    @Test
    fun `supersede dry run and confirm use Core operation plan`() {
        val fixture = fixture()
        initializeLifecycle(fixture)
        val replacement = "review:${"d".repeat(64)}"
        val dryOutput = ByteArrayOutputStream()
        val original = System.out
        try {
            System.setOut(PrintStream(dryOutput, true, StandardCharsets.UTF_8))
            assertEquals(
                0,
                ReviewCommand().execute(
                    lifecycleArguments("supersede", fixture) +
                        listOf("--replacement-proposal", replacement, "--json"),
                ),
            )
        } finally {
            System.setOut(original)
        }
        val planSha = Regex("\"planSha256\":\"([0-9a-f]{64})\"")
            .find(dryOutput.toString(StandardCharsets.UTF_8))!!.groupValues[1]
        assertEquals(ReviewLifecycleState.ACTIVE, lifecycle(fixture).metadata.state)

        assertEquals(
            0,
            ReviewCommand().execute(
                lifecycleArguments("supersede", fixture) +
                    listOf(
                        "--replacement-proposal", replacement,
                        "--confirm", "--plan-sha256", planSha, "--json",
                    ),
            ),
        )
        assertEquals(ReviewLifecycleState.SUPERSEDED, lifecycle(fixture).metadata.state)
    }

    @Test
    fun `recover dry run and confirm roll forward incomplete apply`() {
        val fixture = fixture()
        val bundle = assertTrueLoad(fixture)
        val lifecycleRepository = FileReviewLifecycleRepository(projectRoot)
        val initial = assertTrue(
            lifecycleRepository.initialize(bundle) is ReviewLifecycleResult.Success<ReviewLifecycleAggregate>,
        ).let { lifecycle(fixture).metadata }
        val lifecycleCodec = ReviewLifecycleCodec()
        val resultDocumentation = "recovered result"
        val resultSha = sha256(resultDocumentation)
        val receipt = lifecycleCodec.receipt(bundle, resultSha)
        val transaction = lifecycleCodec.transaction(
            bundle, initial.generation, resultSha, receipt.receiptId, ReviewApplyTransactionPhase.PREPARED,
        )
        assertTrue(
            lifecycleRepository.beginApply(initial, transaction, receipt) is
                ReviewLifecycleResult.Success<ReviewLifecycleAggregate>,
        )
        Files.writeString(fixture.documentationPath, resultDocumentation, StandardCharsets.UTF_8)
        val dryOutput = ByteArrayOutputStream()
        val original = System.out
        try {
            System.setOut(PrintStream(dryOutput, true, StandardCharsets.UTF_8))
            assertEquals(
                0,
                ReviewCommand().execute(
                    lifecycleArguments("recover", fixture) +
                        listOf("--documentation", fixture.documentationPath.toString(), "--json"),
                ),
            )
        } finally {
            System.setOut(original)
        }
        val dryJson = dryOutput.toString(StandardCharsets.UTF_8)
        assertTrue(dryJson.contains("\"recoveryDisposition\":\"ROLL_FORWARD_APPLIED\""))
        val planSha = Regex("\"planSha256\":\"([0-9a-f]{64})\"").find(dryJson)!!.groupValues[1]
        assertEquals(ReviewLifecycleState.APPLYING, lifecycle(fixture).metadata.state)

        assertEquals(
            0,
            ReviewCommand().execute(
                lifecycleArguments("recover", fixture) +
                    listOf(
                        "--documentation", fixture.documentationPath.toString(),
                        "--confirm", "--plan-sha256", planSha, "--json",
                    ),
            ),
        )
        assertEquals(ReviewLifecycleState.APPLIED, lifecycle(fixture).metadata.state)
    }

    private fun statusArguments(fixture: Fixture) = listOf(
        "status", "--project", projectRoot.toString(), "--bundle", fixture.bundlePath.toString(),
        "--documentation", fixture.documentationPath.toString(),
    )

    private fun lifecycleArguments(subcommand: String, fixture: Fixture) = listOf(
        "lifecycle", subcommand,
        "--project", projectRoot.toString(),
        "--bundle", fixture.bundlePath.toString(),
    )

    private fun initializeLifecycle(fixture: Fixture) {
        val bundle = assertTrueLoad(fixture)
        assertTrue(
            FileReviewLifecycleRepository(projectRoot).initialize(bundle) is
                ReviewLifecycleResult.Success<ReviewLifecycleAggregate>,
        )
    }

    private fun lifecycle(fixture: Fixture): ReviewLifecycleAggregate {
        val loaded = FileReviewLifecycleRepository(projectRoot)
            .load(projectRoot.fileName.toString().lowercase(), fixture.proposalId)
        assertTrue(loaded is ReviewLifecycleResult.Success<ReviewLifecycleAggregate>)
        return loaded.value
    }

    private fun fixture(): Fixture {
        val projectId = projectRoot.fileName.toString().lowercase()
        val documentation = """
            # Project

            ## AI Incremental Documentation

            <!-- DOCPILOT_AI_START id=api:removed -->
            Old behavior.
            <!-- DOCPILOT_AI_END id=api:removed -->
        """.trimIndent()
        val documentationPath = projectRoot.resolve("documentation.md")
        Files.writeString(documentationPath, documentation, StandardCharsets.UTF_8)
        val proposal = DocumentationReviewProposal(
            entries = listOf(
                DocumentationReviewEntry(
                    targetId = "api:removed",
                    parentId = "type:one",
                    target = IncrementalUpdateTarget.API,
                    specificationChangeKind = ChangeKind.REMOVED,
                    documentationChangeKind = DocumentationChangeKind.REMOVE,
                    operation = AiDocumentationPatchOperation.REMOVE,
                    existingMarkdown = "Old behavior.",
                    proposedMarkdown = "",
                ),
            ),
            reviewedDocumentationSha256 = sha256(documentation),
        )
        val codec = JsonReviewBundleCodec()
        val bundle = codec.create(specification(projectId, true), specification(projectId, false), proposal)
        val bundlePath = projectRoot.resolve("custom-review.json")
        val repository = FileReviewBundleRepository.atPath(bundlePath, codec)
        assertTrue(repository.saveNew(bundle) is ReviewBundleSaveResult.Saved)
        return Fixture(bundlePath, documentationPath, bundle.proposalId)
    }

    private fun assertTrueLoad(fixture: Fixture): StoredReviewBundle {
        val projectId = projectRoot.fileName.toString().lowercase()
        val loaded = FileReviewBundleRepository.atPath(fixture.bundlePath)
            .load(projectId, fixture.proposalId)
        assertTrue(loaded is ReviewBundleLoadResult.Valid)
        return loaded.bundle
    }

    private fun specification(projectId: String, includeApi: Boolean) = ProjectSpecification(
        schemaVersion = "0.3",
        project = ProjectDescriptor(projectId, projectId),
        components = listOf(
            ComponentSpecification(
                id = "type:one",
                name = "One",
                moduleId = "module:main",
                kind = "class",
                role = "service",
                apis = if (includeApi) listOf(
                    ApiSpecification("api:removed", "removed", "function", "removed(): Unit", purpose = "old"),
                ) else emptyList(),
            ),
        ),
    )

    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private data class Fixture(
        val bundlePath: Path,
        val documentationPath: Path,
        val proposalId: String,
    )
}
