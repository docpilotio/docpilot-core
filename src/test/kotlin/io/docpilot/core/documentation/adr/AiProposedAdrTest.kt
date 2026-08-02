package io.docpilot.core.documentation.adr

import io.docpilot.core.api.AiProvider
import io.docpilot.core.documentation.synthesis.SynthesisEngine
import io.docpilot.core.documentation.synthesis.SynthesisSource
import io.docpilot.core.model.ai.*
import io.docpilot.core.specification.claim.ClaimFixtures
import kotlin.test.*

class AiProposedAdrTest {
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

    private fun request() = AdrProposalRequestBuilder.request(sources(), "Canonical facts across sources.", "fixture", "fixture-model")

    @Test
    fun `well-formed five-section content builds successfully`() {
        val provider = StubProvider(wellFormed)
        val result = SynthesisEngine(provider).synthesize(specification, request())
        val proposal = AiProposedAdrBuilder.build(result)

        assertNotNull(proposal)
        assertEquals("Adopt a repository pattern for Sample.", proposal.title)
        assertEquals("Sample currently accesses data directly.", proposal.context)
        assertEquals("Introduce a repository layer.", proposal.decision)
        assertEquals("Improved testability, some added boilerplate.", proposal.consequences)
        assertEquals("Continue direct access; rejected due to testability concerns.", proposal.alternatives)
        assertEquals(result.record.synthesisStableId, proposal.proposalId)
        assertEquals(result.record.sourceArtifactIds, proposal.citedFindingIds)
    }

    @Test
    fun `missing a required heading rejects the whole draft`() {
        val missingAlternatives = wellFormed.substringBefore("## Alternatives").trim()
        val provider = StubProvider(missingAlternatives)
        val result = SynthesisEngine(provider).synthesize(specification, request())
        assertNull(AiProposedAdrBuilder.build(result))
    }

    @Test
    fun `duplicated heading rejects the whole draft`() {
        val duplicated = "$wellFormed\n\n## Title\nA second title.\n"
        val provider = StubProvider(duplicated)
        val result = SynthesisEngine(provider).synthesize(specification, request())
        assertNull(AiProposedAdrBuilder.build(result))
    }

    @Test
    fun `blank section body rejects the whole draft`() {
        val blankDecision = wellFormed.replace("Introduce a repository layer.", "")
        val provider = StubProvider(blankDecision)
        val result = SynthesisEngine(provider).synthesize(specification, request())
        assertNull(AiProposedAdrBuilder.build(result))
    }

    @Test
    fun `null content from a rejected or fallback result yields null proposal`() {
        val provider = object : AiProvider {
            override val descriptor = descriptor("fixture")
            override fun generate(request: AiRequest) = AiGenerationResult.Failure(
                AiError(AiErrorCode.PROVIDER_FAILURE, "unavailable", false, descriptor.id),
            )
        }
        val result = SynthesisEngine(provider).synthesize(specification, request())
        assertNull(AiProposedAdrBuilder.build(result))
    }

    private class StubProvider(private val text: String) : AiProvider {
        var calls = 0
        override val descriptor = descriptor("fixture")
        override fun generate(request: AiRequest): AiGenerationResult {
            calls++
            return AiGenerationResult.Success(AiResponse(descriptor.id, request.modelId, text, AiFinishReason.STOP))
        }
    }

    companion object {
        private fun descriptor(id: String) = AiProviderDescriptor(
            AiProviderId(id), "Fixture", "1.0.0", AiExecutionLocation.LOCAL, setOf(AiCapability.TEXT_GENERATION),
        )
    }
}
