package io.docpilot.core.incremental.specification.review

import io.docpilot.core.incremental.specification.ChangeKind
import io.docpilot.core.incremental.specification.IncrementalUpdateAction
import io.docpilot.core.incremental.specification.IncrementalUpdatePlan
import io.docpilot.core.incremental.specification.IncrementalUpdateTarget
import io.docpilot.core.incremental.specification.ai.AiDocumentationPatch
import io.docpilot.core.incremental.specification.ai.AiIncrementalDocumentationGenerator
import io.docpilot.core.incremental.specification.ai.AiIncrementalGenerationRequest
import io.docpilot.core.incremental.specification.ai.AiIncrementalGenerationResult
import io.docpilot.core.incremental.specification.ai.AiIncrementalGenerationStatus
import io.docpilot.core.model.ApiSpecification
import io.docpilot.core.model.ComponentSpecification
import io.docpilot.core.model.ProjectDescriptor
import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.model.ai.AiModelId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiIncrementalDocumentationReviewWorkflowTest {
    @Test
    fun `prepares review without exposing generated merged document as approved output`() {
        val existing = existingDocumentation()
        val generator = object : AiIncrementalDocumentationGenerator {
            override fun generate(request: AiIncrementalGenerationRequest): AiIncrementalGenerationResult =
                AiIncrementalGenerationResult(
                    status = AiIncrementalGenerationStatus.SUCCEEDED,
                    mergedDocumentation = "premature merged output",
                    patches = listOf(AiDocumentationPatch("api:one", "### API One\nReviewed behavior.")),
                )
        }
        val workflow = DefaultAiIncrementalDocumentationReviewWorkflow(generator)

        val preparation = workflow.prepare(request(existing))

        assertEquals(AiIncrementalReviewPreparationStatus.READY_FOR_REVIEW, preparation.status)
        assertEquals(existing, preparation.existingDocumentation)
        assertEquals(DocumentationChangeKind.UPDATE, preparation.proposal!!.entries.single().documentationChangeKind)
        assertFalse(preparation.existingDocumentation.contains("premature merged output"))

        val applied = workflow.apply(
            preparation,
            listOf(DocumentationReviewDecision("api:one", DocumentationReviewDisposition.ACCEPTED)),
        )
        assertEquals(DocumentationReviewApplyStatus.APPLIED, applied.status)
        assertTrue(applied.mergedDocumentation.contains("Reviewed behavior."))
    }

    @Test
    fun `no change preparation cannot be applied`() {
        val workflow = DefaultAiIncrementalDocumentationReviewWorkflow(
            object : AiIncrementalDocumentationGenerator {
                override fun generate(request: AiIncrementalGenerationRequest): AiIncrementalGenerationResult =
                    AiIncrementalGenerationResult(
                        AiIncrementalGenerationStatus.NO_CHANGES,
                        request.existingDocumentation,
                    )
            },
        )
        val preparation = workflow.prepare(request(existingDocumentation()))

        assertEquals(AiIncrementalReviewPreparationStatus.NO_CHANGES, preparation.status)
        assertEquals(null, preparation.proposal)
    }

    private fun request(existing: String): AiIncrementalGenerationRequest {
        val previous = specification("old")
        val current = specification("new")
        return AiIncrementalGenerationRequest(
            previousSpecification = previous,
            currentSpecification = current,
            updatePlan = IncrementalUpdatePlan(
                actions = listOf(
                    IncrementalUpdateAction(IncrementalUpdateTarget.API, "api:one", "type:one", ChangeKind.MODIFIED),
                ),
            ),
            existingDocumentation = existing,
            modelId = AiModelId("test"),
        )
    }

    private fun specification(purpose: String): ProjectSpecification = ProjectSpecification(
        schemaVersion = "0.3",
        project = ProjectDescriptor("project", "Project"),
        components = listOf(
            ComponentSpecification(
                id = "type:one",
                name = "One",
                moduleId = "module:main",
                kind = "class",
                role = "service",
                apis = listOf(
                    ApiSpecification(
                        id = "api:one",
                        name = "run",
                        kind = "function",
                        purpose = purpose,
                    ),
                ),
            ),
        ),
    )

    private fun existingDocumentation(): String = """
        # Project

        ## AI Incremental Documentation

        <!-- DOCPILOT_AI_START id=api:one -->
        ### API One
        Old behavior.
        <!-- DOCPILOT_AI_END id=api:one -->
    """.trimIndent()
}
