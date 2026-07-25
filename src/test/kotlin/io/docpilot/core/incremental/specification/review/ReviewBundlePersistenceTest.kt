package io.docpilot.core.incremental.specification.review

import io.docpilot.core.incremental.specification.ChangeKind
import io.docpilot.core.incremental.specification.IncrementalUpdateAction
import io.docpilot.core.incremental.specification.IncrementalUpdatePlan
import io.docpilot.core.incremental.specification.IncrementalUpdateTarget
import io.docpilot.core.incremental.specification.ai.AiDocumentationPatchOperation
import io.docpilot.core.incremental.specification.ai.AiIncrementalDocumentationGenerator
import io.docpilot.core.incremental.specification.ai.AiIncrementalGenerationRequest
import io.docpilot.core.incremental.specification.ai.AiIncrementalGenerationResult
import io.docpilot.core.incremental.specification.ai.AiIncrementalGenerationStatus
import io.docpilot.core.model.ApiSpecification
import io.docpilot.core.model.ComponentSpecification
import io.docpilot.core.model.ProjectDescriptor
import io.docpilot.core.model.ProjectSpecification
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ReviewBundlePersistenceTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    private val codec = JsonReviewBundleCodec()

    @Test
    fun `bundle round trip is deterministic and proposal identity ignores decisions`() {
        val base = codec.create(previous(), current(), proposal())
        val decided = codec.withDecisions(
            base,
            listOf(DocumentationReviewDecision("api:removed", DocumentationReviewDisposition.ACCEPTED, "verified")),
        )

        assertEquals(base.proposalId, decided.proposalId)
        assertNotEquals(base.integrity.payloadSha256, decided.integrity.payloadSha256)
        val encoded = codec.encode(decided)
        val decoded = assertIs<ReviewBundleLoadResult.Valid>(codec.decode(encoded, "project")).bundle
        assertEquals(decided, decoded)
        assertEquals(encoded, codec.encode(decoded))
    }

    @Test
    fun `tampered decision fails integrity validation`() {
        val decided = codec.withDecisions(
            codec.create(previous(), current(), proposal()),
            listOf(DocumentationReviewDecision("api:removed", DocumentationReviewDisposition.ACCEPTED)),
        )
        val tampered = codec.encode(decided).replace("\"ACCEPTED\"", "\"REJECTED\"")

        val result = codec.decode(tampered, "project")

        assertIs<ReviewBundleLoadResult.Invalid>(result)
        assertEquals(ReviewBundleValidationFailure.INTEGRITY_MISMATCH, result.reason)
    }

    @Test
    fun `repository prevents stale replacement`() {
        val repository = FileReviewBundleRepository(temporaryDirectory, codec)
        val original = codec.create(previous(), current(), proposal())
        assertIs<ReviewBundleSaveResult.Saved>(repository.saveNew(original))
        val first = codec.withDecisions(
            original,
            listOf(DocumentationReviewDecision("api:removed", DocumentationReviewDisposition.ACCEPTED)),
        )
        assertIs<ReviewBundleSaveResult.Saved>(
            repository.replace(first, original.integrity.payloadSha256),
        )
        val stale = codec.withDecisions(
            original,
            listOf(DocumentationReviewDecision("api:removed", DocumentationReviewDisposition.REJECTED)),
        )

        val conflict = repository.replace(stale, original.integrity.payloadSha256)

        assertIs<ReviewBundleSaveResult.Conflict>(conflict)
        assertEquals(first.integrity.payloadSha256, conflict.actualPayloadSha256)
    }

    @Test
    fun `decisions persist across workflow instances and resume removal`() {
        val repository = FileReviewBundleRepository(temporaryDirectory, codec)
        val original = codec.create(previous(), current(), proposal())
        assertIs<ReviewBundleSaveResult.Saved>(repository.saveNew(original))
        val firstProcess = workflow(repository)
        val updated = assertIs<PersistentReviewUpdateResult.Saved>(
            firstProcess.recordDecisions(
                "project",
                original.proposalId,
                original.integrity.payloadSha256,
                listOf(DocumentationReviewDecision("api:removed", DocumentationReviewDisposition.ACCEPTED)),
            ),
        ).bundle

        val secondProcess = workflow(FileReviewBundleRepository(temporaryDirectory, codec))
        val applied = assertIs<ResumableReviewApplyResult.Applied>(
            secondProcess.resumeApply(
                ResumableReviewApplyRequest(
                    "project",
                    original.proposalId,
                    existingDocumentation(),
                    updated.integrity.payloadSha256,
                ),
            ),
        )

        assertTrue("api:removed" !in applied.mergedDocumentation)
        assertEquals(listOf("api:removed"), applied.acceptedTargetIds)
    }

    @Test
    fun `resume blocks stale documentation and stale bundle identity`() {
        val repository = FileReviewBundleRepository(temporaryDirectory, codec)
        val original = codec.create(previous(), current(), proposal())
        repository.saveNew(original)
        val updated = assertIs<PersistentReviewUpdateResult.Saved>(
            workflow(repository).recordDecisions(
                "project",
                original.proposalId,
                original.integrity.payloadSha256,
                listOf(DocumentationReviewDecision("api:removed", DocumentationReviewDisposition.ACCEPTED)),
            ),
        ).bundle
        val restarted = workflow(FileReviewBundleRepository(temporaryDirectory, codec))

        val bundleConflict = restarted.resumeApply(
            ResumableReviewApplyRequest(
                "project",
                original.proposalId,
                existingDocumentation(),
                original.integrity.payloadSha256,
            ),
        )
        val documentConflict = restarted.resumeApply(
            ResumableReviewApplyRequest(
                "project",
                original.proposalId,
                existingDocumentation() + "\nchanged",
                updated.integrity.payloadSha256,
            ),
        )

        assertEquals(
            ResumableReviewConflict.BUNDLE_CHANGED,
            assertIs<ResumableReviewApplyResult.Conflict>(bundleConflict).reason,
        )
        assertEquals(
            ResumableReviewConflict.STALE_DOCUMENTATION,
            assertIs<ResumableReviewApplyResult.Conflict>(documentConflict).reason,
        )
    }

    private fun workflow(repository: ReviewBundleRepository) =
        DefaultPersistentDocumentationReviewWorkflow(UnusedGenerator, repository, codec)

    private fun proposal() = DocumentationReviewProposal(
        entries = listOf(
            DocumentationReviewEntry(
                targetId = "api:removed",
                parentId = "type:one",
                target = IncrementalUpdateTarget.API,
                specificationChangeKind = ChangeKind.REMOVED,
                documentationChangeKind = DocumentationChangeKind.REMOVE,
                operation = AiDocumentationPatchOperation.REMOVE,
                existingMarkdown = "Old generated behavior.",
                proposedMarkdown = "",
                evidenceIds = listOf("evidence:removed"),
            ),
        ),
        reviewedDocumentationSha256 = sha256(existingDocumentation()),
    )

    private fun previous() = specification(
        listOf(
            ApiSpecification(
                id = "api:removed",
                name = "removed",
                kind = "function",
                signature = "removed(): Unit",
                purpose = "old",
                evidenceRefs = setOf("evidence:removed"),
            ),
        ),
    )

    private fun current() = specification(emptyList())

    private fun specification(apis: List<ApiSpecification>) = ProjectSpecification(
        schemaVersion = "0.3",
        project = ProjectDescriptor("project", "Project"),
        components = listOf(
            ComponentSpecification(
                id = "type:one",
                name = "One",
                moduleId = "module:main",
                kind = "class",
                role = "service",
                apis = apis,
            ),
        ),
    )

    private fun existingDocumentation() = """
        # Project

        ## AI Incremental Documentation

        <!-- DOCPILOT_AI_START id=api:removed -->
        Old generated behavior.
        <!-- DOCPILOT_AI_END id=api:removed -->
    """.trimIndent()

    private fun sha256(value: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private object UnusedGenerator : AiIncrementalDocumentationGenerator {
        override fun generate(request: AiIncrementalGenerationRequest) = AiIncrementalGenerationResult(
            AiIncrementalGenerationStatus.FAILED,
            request.existingDocumentation,
            errorMessage = "unused",
        )
    }
}
