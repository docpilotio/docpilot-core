package io.docpilot.core.incremental.specification.review

import io.docpilot.core.incremental.specification.ChangeKind
import io.docpilot.core.incremental.specification.IncrementalUpdateAction
import io.docpilot.core.incremental.specification.IncrementalUpdatePlan
import io.docpilot.core.incremental.specification.IncrementalUpdateTarget
import io.docpilot.core.incremental.specification.ai.AiDocumentationPatch
import io.docpilot.core.incremental.specification.ai.AiDocumentationPatchOperation
import io.docpilot.core.incremental.specification.ai.ManagedBlockAiDocumentationMerger
import io.docpilot.core.incremental.specification.ai.MarkerAiDocumentationPatchCodec
import io.docpilot.core.model.ApiSpecification
import io.docpilot.core.model.ComponentSpecification
import io.docpilot.core.model.ProjectDescriptor
import io.docpilot.core.model.ProjectSpecification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManagedBlockRemovalReviewTest {
    private val reviewer = DefaultDocumentationDiffReviewer()

    @Test
    fun `codec decodes explicit remove and rejects duplicate operation targets`() {
        val codec = MarkerAiDocumentationPatchCodec()

        val operations = codec.decode(
            """
            <<<DOCPILOT_REMOVE id=api:removed>>>
            <<<DOCPILOT_PATCH id=api:updated>>>
            Updated.
            <<<END_DOCPILOT_PATCH>>>
            """.trimIndent(),
        )

        assertEquals(listOf("api:removed", "api:updated"), operations.map { it.targetId })
        assertEquals(AiDocumentationPatchOperation.REMOVE, operations[0].operation)
        assertEquals("", operations[0].markdown)
        assertFailsWith<IllegalArgumentException> {
            codec.decode(
                """
                <<<DOCPILOT_REMOVE id=api:same>>>
                <<<DOCPILOT_PATCH id=api:same>>>Updated.<<<END_DOCPILOT_PATCH>>>
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `accepted removal deletes managed block after complete review`() {
        val request = removalRequest()
        val proposal = reviewer.propose(request)

        assertTrue(proposal.isComplete)
        assertEquals(DocumentationChangeKind.REMOVE, proposal.entries.single().documentationChangeKind)
        assertEquals(AiDocumentationPatchOperation.REMOVE, proposal.entries.single().operation)
        assertEquals(listOf("evidence:removed"), proposal.entries.single().evidenceIds)

        val result = reviewer.apply(
            request.existingDocumentation,
            proposal,
            listOf(DocumentationReviewDecision("api:removed", DocumentationReviewDisposition.ACCEPTED)),
        )

        assertEquals(DocumentationReviewApplyStatus.APPLIED, result.status)
        assertFalse(result.mergedDocumentation.contains("api:removed"))
        assertTrue(result.mergedDocumentation.contains("api:updated"))
        assertTrue(result.mergedDocumentation.contains("AI Incremental Documentation"))
        assertTrue(result.mergedDocumentation.contains("# Project"))
    }

    @Test
    fun `rejected removal preserves exact documentation`() {
        val request = removalRequest()
        val proposal = reviewer.propose(request)

        val result = reviewer.apply(
            request.existingDocumentation,
            proposal,
            listOf(DocumentationReviewDecision("api:removed", DocumentationReviewDisposition.REJECTED)),
        )

        assertEquals(DocumentationReviewApplyStatus.APPLIED, result.status)
        assertEquals(request.existingDocumentation, result.mergedDocumentation)
    }

    @Test
    fun `reviewed base conflict fails before removal`() {
        val request = removalRequest()
        val proposal = reviewer.propose(request)

        val error = assertFailsWith<IllegalArgumentException> {
            reviewer.apply(
                request.existingDocumentation + "\nhandwritten change",
                proposal,
                listOf(DocumentationReviewDecision("api:removed", DocumentationReviewDisposition.ACCEPTED)),
            )
        }

        assertTrue(error.message!!.contains("reviewed-base conflict"))
    }

    @Test
    fun `remove is rejected for modified target and missing block`() {
        val remove = AiDocumentationPatch("api:removed", "", AiDocumentationPatchOperation.REMOVE)
        val modifiedPlan = IncrementalUpdatePlan(
            actions = listOf(
                IncrementalUpdateAction(
                    IncrementalUpdateTarget.API,
                    "api:removed",
                    "type:one",
                    ChangeKind.MODIFIED,
                ),
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            reviewer.propose(removalRequest().copy(updatePlan = modifiedPlan, currentSpecification = previous()))
        }
        assertFailsWith<IllegalArgumentException> {
            reviewer.propose(removalRequest().copy(existingDocumentation = "# Project", patches = listOf(remove)))
        }
    }

    @Test
    fun `mixed operations validate all removals before returning a result`() {
        val merger = ManagedBlockAiDocumentationMerger()
        val original = existingDocumentation()
        val operations = listOf(
            AiDocumentationPatch("api:updated", "Updated"),
            AiDocumentationPatch("api:missing", "", AiDocumentationPatchOperation.REMOVE),
        )

        assertFailsWith<IllegalArgumentException> {
            merger.merge(original, operations)
        }
        assertTrue(original.contains("Old removed behavior."))
        assertTrue(original.contains("Old updated behavior."))
    }

    @Test
    fun `report renders explicit removal outcome and reviewed base`() {
        val proposal = reviewer.propose(removalRequest())
        val report = MarkdownDocumentationReviewReportRenderer().render(proposal)

        assertTrue(report.contains("Patch operation: REMOVE"))
        assertTrue(report.contains("Documentation change: REMOVE"))
        assertTrue(report.contains("Managed block will be removed."))
        assertTrue(report.contains("Reviewed base SHA-256: ${proposal.reviewedDocumentationSha256}"))
    }

    private fun removalRequest(): DocumentationReviewRequest = DocumentationReviewRequest(
        previousSpecification = previous(),
        currentSpecification = current(),
        updatePlan = IncrementalUpdatePlan(
            actions = listOf(
                IncrementalUpdateAction(
                    IncrementalUpdateTarget.API,
                    "api:removed",
                    "type:one",
                    ChangeKind.REMOVED,
                ),
            ),
        ),
        existingDocumentation = existingDocumentation(),
        patches = listOf(
            AiDocumentationPatch("api:removed", "", AiDocumentationPatchOperation.REMOVE),
        ),
    )

    private fun previous(): ProjectSpecification = specification(
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

    private fun current(): ProjectSpecification = specification(emptyList())

    private fun specification(apis: List<ApiSpecification>): ProjectSpecification = ProjectSpecification(
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

    private fun existingDocumentation(): String = """
        # Project

        ## AI Incremental Documentation

        <!-- DOCPILOT_AI_START id=api:removed -->
        Old removed behavior.
        <!-- DOCPILOT_AI_END id=api:removed -->

        <!-- DOCPILOT_AI_START id=api:updated -->
        Old updated behavior.
        <!-- DOCPILOT_AI_END id=api:updated -->
    """.trimIndent()
}
