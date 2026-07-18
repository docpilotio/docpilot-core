package io.docpilot.core.incremental.specification.review

import io.docpilot.core.incremental.specification.ChangeKind
import io.docpilot.core.incremental.specification.IncrementalUpdateAction
import io.docpilot.core.incremental.specification.IncrementalUpdatePlan
import io.docpilot.core.incremental.specification.IncrementalUpdateTarget
import io.docpilot.core.incremental.specification.ai.AiDocumentationPatch
import io.docpilot.core.model.ApiSpecification
import io.docpilot.core.model.ComponentSpecification
import io.docpilot.core.model.ProjectDescriptor
import io.docpilot.core.model.ProjectSpecification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocumentationDiffReviewerTest {
    private val reviewer = DefaultDocumentationDiffReviewer()

    @Test
    fun `proposal compares managed blocks and preserves evidence in deterministic order`() {
        val request = DocumentationReviewRequest(
            previousSpecification = specification("old", setOf("evidence:2")),
            currentSpecification = specification("new", setOf("evidence:1", "evidence:2")),
            updatePlan = IncrementalUpdatePlan(
                actions = listOf(
                    IncrementalUpdateAction(IncrementalUpdateTarget.API, "api:second", "type:one", ChangeKind.ADDED),
                    IncrementalUpdateAction(IncrementalUpdateTarget.API, "api:one", "type:one", ChangeKind.MODIFIED),
                ),
            ),
            existingDocumentation = existingDocumentation(),
            patches = listOf(
                AiDocumentationPatch("api:second", "### Second API\nNew block."),
                AiDocumentationPatch("api:one", "### API One\nNew behavior."),
            ),
        )

        val proposal = reviewer.propose(request)

        assertTrue(proposal.isComplete)
        assertEquals(listOf("api:one", "api:second"), proposal.entries.map { it.targetId })
        assertEquals(DocumentationChangeKind.UPDATE, proposal.entries[0].documentationChangeKind)
        assertEquals(listOf("evidence:1", "evidence:2"), proposal.entries[0].evidenceIds)
        assertEquals(DocumentationChangeKind.CREATE, proposal.entries[1].documentationChangeKind)
        assertEquals(null, proposal.entries[1].existingMarkdown)
    }

    @Test
    fun `same normalized markdown is reported as no change`() {
        val request = request(
            patches = listOf(AiDocumentationPatch("api:one", "### API One\r\nOld behavior.")),
        )

        val proposal = reviewer.propose(request)

        assertEquals(DocumentationChangeKind.NO_CHANGE, proposal.entries.single().documentationChangeKind)
    }

    @Test
    fun `missing patch keeps proposal incomplete and prevents merge`() {
        val request = DocumentationReviewRequest(
            previousSpecification = specification("old"),
            currentSpecification = specification("new"),
            updatePlan = IncrementalUpdatePlan(
                actions = listOf(
                    IncrementalUpdateAction(IncrementalUpdateTarget.API, "api:one", "type:one", ChangeKind.MODIFIED),
                    IncrementalUpdateAction(IncrementalUpdateTarget.API, "api:second", "type:one", ChangeKind.ADDED),
                ),
            ),
            existingDocumentation = existingDocumentation(),
            patches = listOf(AiDocumentationPatch("api:one", "### API One\nNew behavior.")),
        )

        val proposal = reviewer.propose(request)
        val result = reviewer.apply(
            request.existingDocumentation,
            proposal,
            listOf(DocumentationReviewDecision("api:one", DocumentationReviewDisposition.ACCEPTED)),
        )

        assertFalse(proposal.isComplete)
        assertEquals(listOf("api:second"), proposal.missingPatchTargetIds)
        assertEquals(DocumentationReviewApplyStatus.PENDING_REVIEW, result.status)
        assertEquals(request.existingDocumentation, result.mergedDocumentation)
    }

    @Test
    fun `partial decisions do not merge accepted patches`() {
        val request = DocumentationReviewRequest(
            previousSpecification = specification("old"),
            currentSpecification = specification("new"),
            updatePlan = IncrementalUpdatePlan(
                actions = listOf(
                    IncrementalUpdateAction(IncrementalUpdateTarget.API, "api:one", "type:one", ChangeKind.MODIFIED),
                    IncrementalUpdateAction(IncrementalUpdateTarget.API, "api:second", "type:one", ChangeKind.ADDED),
                ),
            ),
            existingDocumentation = existingDocumentation(),
            patches = listOf(
                AiDocumentationPatch("api:one", "### API One\nNew behavior."),
                AiDocumentationPatch("api:second", "### Second API\nNew block."),
            ),
        )
        val proposal = reviewer.propose(request)

        val result = reviewer.apply(
            request.existingDocumentation,
            proposal,
            listOf(DocumentationReviewDecision("api:one", DocumentationReviewDisposition.ACCEPTED)),
        )

        assertEquals(DocumentationReviewApplyStatus.PENDING_REVIEW, result.status)
        assertEquals(listOf("api:second"), result.pendingTargetIds)
        assertEquals(request.existingDocumentation, result.mergedDocumentation)
    }

    @Test
    fun `all decisions merge accepted patches and retain rejected blocks`() {
        val request = DocumentationReviewRequest(
            previousSpecification = specification("old"),
            currentSpecification = specification("new"),
            updatePlan = IncrementalUpdatePlan(
                actions = listOf(
                    IncrementalUpdateAction(IncrementalUpdateTarget.API, "api:one", "type:one", ChangeKind.MODIFIED),
                    IncrementalUpdateAction(IncrementalUpdateTarget.API, "api:second", "type:one", ChangeKind.ADDED),
                ),
            ),
            existingDocumentation = existingDocumentation(),
            patches = listOf(
                AiDocumentationPatch("api:one", "### API One\nNew behavior."),
                AiDocumentationPatch("api:second", "### Second API\nNew block."),
            ),
        )
        val proposal = reviewer.propose(request)

        val result = reviewer.apply(
            request.existingDocumentation,
            proposal,
            listOf(
                DocumentationReviewDecision("api:second", DocumentationReviewDisposition.REJECTED, "Not ready"),
                DocumentationReviewDecision("api:one", DocumentationReviewDisposition.ACCEPTED),
            ),
        )

        assertEquals(DocumentationReviewApplyStatus.APPLIED, result.status)
        assertEquals(listOf("api:one"), result.acceptedTargetIds)
        assertEquals(listOf("api:second"), result.rejectedTargetIds)
        assertTrue(result.mergedDocumentation.contains("New behavior."))
        assertFalse(result.mergedDocumentation.contains("Second API"))
        assertEquals(1, result.mergedDocumentation.split("DOCPILOT_AI_START id=api:one").size - 1)
    }

    @Test
    fun `patch outside update plan is rejected`() {
        val error = assertFailsWith<IllegalArgumentException> {
            reviewer.propose(
                request(patches = listOf(AiDocumentationPatch("api:other", "Unexpected"))),
            )
        }

        assertTrue(error.message!!.contains("outside the incremental update plan"))
    }

    @Test
    fun `unknown and duplicate decisions are rejected`() {
        val request = request(
            patches = listOf(AiDocumentationPatch("api:one", "### API One\nNew behavior.")),
        )
        val proposal = reviewer.propose(request)

        assertFailsWith<IllegalArgumentException> {
            reviewer.apply(
                request.existingDocumentation,
                proposal,
                listOf(DocumentationReviewDecision("api:other", DocumentationReviewDisposition.ACCEPTED)),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            reviewer.apply(
                request.existingDocumentation,
                proposal,
                listOf(
                    DocumentationReviewDecision("api:one", DocumentationReviewDisposition.ACCEPTED),
                    DocumentationReviewDecision("api:one", DocumentationReviewDisposition.REJECTED),
                ),
            )
        }
    }

    private fun request(patches: List<AiDocumentationPatch>): DocumentationReviewRequest = DocumentationReviewRequest(
        previousSpecification = specification("old"),
        currentSpecification = specification("new"),
        updatePlan = IncrementalUpdatePlan(
            actions = listOf(
                IncrementalUpdateAction(IncrementalUpdateTarget.API, "api:one", "type:one", ChangeKind.MODIFIED),
            ),
        ),
        existingDocumentation = existingDocumentation(),
        patches = patches,
    )

    private fun specification(
        purpose: String,
        evidenceRefs: Set<String> = emptySet(),
    ): ProjectSpecification = ProjectSpecification(
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
                        signature = "run(): Unit",
                        purpose = purpose,
                        evidenceRefs = evidenceRefs,
                    ),
                    ApiSpecification(
                        id = "api:second",
                        name = "second",
                        kind = "function",
                        signature = "second(): Unit",
                        purpose = "second",
                        evidenceRefs = setOf("evidence:3"),
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
