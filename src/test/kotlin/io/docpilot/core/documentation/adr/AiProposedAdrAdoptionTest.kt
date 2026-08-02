package io.docpilot.core.documentation.adr

import io.docpilot.core.api.AiProvider
import io.docpilot.core.document.DocumentRenderer
import io.docpilot.core.documentation.synthesis.SynthesisEngine
import io.docpilot.core.documentation.synthesis.SynthesisSource
import io.docpilot.core.generator.adr.AdrMetadata
import io.docpilot.core.generator.adr.AdrStatus
import io.docpilot.core.incremental.specification.review.DocumentationReviewDecision
import io.docpilot.core.incremental.specification.review.DocumentationReviewDisposition
import io.docpilot.core.model.ai.*
import io.docpilot.core.specification.claim.ClaimFixtures
import kotlin.test.*

class AiProposedAdrAdoptionTest {
    private val specification = ClaimFixtures.specification()

    private val wellFormed = """
        ## Title
        Adopt a repository pattern for Sample.

        ## Context
        Sample currently accesses data directly.

        ## Decision
        Introduce a repository layer.

        ## Consequences
        Improved testability, some added boilerplate.

        ## Alternatives
        Continue direct access; rejected due to testability concerns.
    """.trimIndent()

    private fun sources() = listOf(
        SynthesisSource("component:sample", "COMPONENT", evidenceRefs = listOf(ClaimFixtures.highEvidence.id)),
        SynthesisSource(ClaimFixtures.contract.id, "CONTRACT", evidenceRefs = listOf(ClaimFixtures.contract.id)),
    )

    private fun proposal(): AiProposedAdr {
        val request = AdrProposalRequestBuilder.request(sources(), "Canonical facts across sources.", "fixture", "fixture-model")
        val result = SynthesisEngine(StubProvider(wellFormed)).synthesize(specification, request)
        return AiProposedAdrBuilder.build(result)!!
    }

    @Test
    fun `decision not targeting this proposal is rejected`() {
        val other = DocumentationReviewDecision("finding:other", DocumentationReviewDisposition.ACCEPTED)
        assertFailsWith<IllegalArgumentException> { AiProposedAdrAdoption.adopt(proposal(), other) }
    }

    @Test
    fun `rejected disposition cannot adopt`() {
        val proposal = proposal()
        val decision = DocumentationReviewDecision(
            AdrProposalCurationBinding.decisionTargetId(proposal), DocumentationReviewDisposition.REJECTED,
        )
        assertFailsWith<IllegalArgumentException> { AiProposedAdrAdoption.adopt(proposal, decision) }
    }

    @Test
    fun `accepted decision adopts into a rendered Document`() {
        val proposal = proposal()
        val decision = DocumentationReviewDecision(
            AdrProposalCurationBinding.decisionTargetId(proposal), DocumentationReviewDisposition.ACCEPTED,
        )
        val document = AiProposedAdrAdoption.adopt(proposal, decision)

        assertEquals(proposal.title, document.title)
        assertEquals(AdrStatus.ACCEPTED.value, document.metadata?.attributes?.get(AdrMetadata.STATUS_KEY))
        assertEquals(proposal.title, document.metadata?.attributes?.get(AdrMetadata.TITLE_KEY))
        assertEquals("adr-ai-proposed", document.metadata?.attributes?.get(AdrMetadata.GENERATOR_KEY))

        val markdown = DocumentRenderer().render(document)
        assertTrue(markdown.startsWith("# ${proposal.title}"))
        assertTrue(markdown.contains("## Architecture Decision Record"))
        assertTrue(markdown.contains(proposal.context))
        assertTrue(markdown.contains(proposal.decision))
        assertTrue(markdown.contains(proposal.consequences))
        assertTrue(markdown.contains(proposal.alternatives))
    }

    @Test
    fun `binding target id matches proposal id and constructs a valid review decision`() {
        val proposal = proposal()
        val targetId = AdrProposalCurationBinding.decisionTargetId(proposal)
        assertEquals(proposal.proposalId, targetId)
        val decision = DocumentationReviewDecision(targetId, DocumentationReviewDisposition.ACCEPTED, "Adopted after review.")
        assertEquals(proposal.proposalId, decision.targetId)
    }

    private class StubProvider(private val text: String) : AiProvider {
        override val descriptor = descriptor("fixture")
        override fun generate(request: AiRequest): AiGenerationResult =
            AiGenerationResult.Success(AiResponse(descriptor.id, request.modelId, text, AiFinishReason.STOP))
    }

    companion object {
        private fun descriptor(id: String) = AiProviderDescriptor(
            AiProviderId(id), "Fixture", "1.0.0", AiExecutionLocation.LOCAL, setOf(AiCapability.TEXT_GENERATION),
        )
    }
}
