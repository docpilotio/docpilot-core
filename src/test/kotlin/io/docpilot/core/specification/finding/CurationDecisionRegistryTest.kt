package io.docpilot.core.specification.finding

import io.docpilot.core.incremental.specification.review.DocumentationReviewDecision
import io.docpilot.core.incremental.specification.review.DocumentationReviewDisposition
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class CurationDecisionRegistryTest {
    @Test
    fun `load is NotFound before any merge`() {
        val repository = FileCurationDecisionRegistryRepository(createTempDirectory("docpilot-decision-registry"))

        assertEquals(CurationDecisionRegistryLoadResult.NotFound, repository.load("project-a"))
    }

    @Test
    fun `merge persists and round-trips including a null comment`() {
        val root = createTempDirectory("docpilot-decision-registry")
        val repository = FileCurationDecisionRegistryRepository(root)

        repository.merge("project-a", listOf(DocumentationReviewDecision("finding:1", DocumentationReviewDisposition.ACCEPTED, "looks right")))
        repository.merge("project-a", listOf(DocumentationReviewDecision("finding:2", DocumentationReviewDisposition.REJECTED, null)))
        val loaded = repository.load("project-a")

        assertIs<CurationDecisionRegistryLoadResult.Valid>(loaded)
        val byTarget = loaded.registry.decisions.associateBy { it.targetId }
        assertEquals(DocumentationReviewDisposition.ACCEPTED, byTarget.getValue("finding:1").disposition)
        assertEquals("looks right", byTarget.getValue("finding:1").comment)
        assertEquals(DocumentationReviewDisposition.REJECTED, byTarget.getValue("finding:2").disposition)
        assertNull(byTarget.getValue("finding:2").comment)
    }

    @Test
    fun `a later decision for the same target overwrites the earlier one`() {
        val root = createTempDirectory("docpilot-decision-registry")
        val repository = FileCurationDecisionRegistryRepository(root)

        repository.merge("project-a", listOf(DocumentationReviewDecision("finding:1", DocumentationReviewDisposition.REJECTED)))
        val merged = repository.merge("project-a", listOf(DocumentationReviewDecision("finding:1", DocumentationReviewDisposition.ACCEPTED, "changed my mind")))

        assertEquals(1, merged.size)
        assertEquals(DocumentationReviewDisposition.ACCEPTED, merged.single().disposition)
        assertEquals("changed my mind", merged.single().comment)
    }

    @Test
    fun `roadmap and adr-adopt targetId schemes coexist without collision`() {
        val root = createTempDirectory("docpilot-decision-registry")
        val repository = FileCurationDecisionRegistryRepository(root)

        repository.merge("project-a", listOf(DocumentationReviewDecision("finding:abc", DocumentationReviewDisposition.ACCEPTED)))
        val merged = repository.merge("project-a", listOf(DocumentationReviewDecision("documentation-synthesis:abc", DocumentationReviewDisposition.ACCEPTED)))

        assertEquals(2, merged.size)
        assertEquals(setOf("finding:abc", "documentation-synthesis:abc"), merged.map { it.targetId }.toSet())
    }

    @Test
    fun `load reports a project id mismatch`() {
        val root = createTempDirectory("docpilot-decision-registry")
        FileCurationDecisionRegistryRepository(root).merge("project-a", listOf(DocumentationReviewDecision("finding:1", DocumentationReviewDisposition.ACCEPTED)))

        val loaded = FileCurationDecisionRegistryRepository(root).load("project-b")

        assertIs<CurationDecisionRegistryLoadResult.Invalid>(loaded)
        assertEquals(CurationDecisionRegistryValidationFailure.PROJECT_MISMATCH, loaded.reason)
    }
}
