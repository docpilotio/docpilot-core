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

    private fun statusArguments(fixture: Fixture) = listOf(
        "status", "--project", projectRoot.toString(), "--bundle", fixture.bundlePath.toString(),
        "--documentation", fixture.documentationPath.toString(),
    )

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
