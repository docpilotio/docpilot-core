package io.docpilot.core.documentation.advisory

import io.docpilot.core.api.AiProvider
import io.docpilot.core.documentation.synthesis.SynthesisEngine
import io.docpilot.core.documentation.synthesis.SynthesisSource
import io.docpilot.core.model.ai.*
import io.docpilot.core.specification.claim.ClaimFixtures
import kotlin.test.*

class ExecutiveSummaryTest {
    private val specification = ClaimFixtures.specification()

    @Test
    fun `well-formed AS-IS TO-BE content builds successfully with correct evidence split`() {
        val provider = StubProvider("AS-IS: Sample has one Contract.\nTO-BE: Consider adding a second Contract.\n")
        val engine = SynthesisEngine(provider)
        val result = engine.synthesize(specification, request())
        val document = ExecutiveSummaryBuilder.build(result)

        assertNotNull(document)
        assertEquals(2, document.statements.size)
        val asIs = document.statements.single { it.label == StatementLabel.AS_IS }
        val toBe = document.statements.single { it.label == StatementLabel.TO_BE }
        assertEquals("Sample has one Contract.", asIs.text)
        assertEquals(result.record.evidenceRefs.toSet(), asIs.evidenceRefs)
        assertEquals("Consider adding a second Contract.", toBe.text)
        assertTrue(toBe.evidenceRefs.isEmpty())
    }

    @Test
    fun `line missing a valid prefix rejects the whole document`() {
        val provider = StubProvider("AS-IS: Sample has one Contract.\nThis line has no prefix.\n")
        val result = SynthesisEngine(provider).synthesize(specification, request())
        assertNull(ExecutiveSummaryBuilder.build(result))
    }

    @Test
    fun `null content from a rejected or fallback result yields null document`() {
        val provider = object : AiProvider {
            override val descriptor = descriptor("fixture")
            override fun generate(request: AiRequest) = AiGenerationResult.Failure(
                AiError(AiErrorCode.PROVIDER_FAILURE, "unavailable", false, descriptor.id),
            )
        }
        val result = SynthesisEngine(provider).synthesize(specification, request())
        assertNull(ExecutiveSummaryBuilder.build(result))
    }

    @Test
    fun `request builder end-to-end through SynthesisEngine produces a valid document`() {
        val provider = StubProvider("AS-IS: Facts hold across both sources.\nTO-BE: Recommend a follow-up review.\n")
        val engine = SynthesisEngine(provider)
        val request = ExecutiveSummaryRequestBuilder.request(sources(), "Canonical facts across sources.", "fixture", "fixture-model")
        val result = engine.synthesize(specification, request)
        val document = ExecutiveSummaryBuilder.build(result)
        assertNotNull(document)
        assertEquals(2, document.sourceArtifactIds.size)
    }

    @Test
    fun `render emits both AS-IS and TO-BE tags`() {
        val provider = StubProvider("AS-IS: Sample has one Contract.\nTO-BE: Consider adding a second Contract.\n")
        val result = SynthesisEngine(provider).synthesize(specification, request())
        val document = ExecutiveSummaryBuilder.build(result)
        assertNotNull(document)
        val markdown = ExecutiveSummaryMarkdownRenderer.render(document)
        assertTrue(markdown.contains("**[AS-IS]**"))
        assertTrue(markdown.contains("**[TO-BE]**"))
    }

    private fun sources() = listOf(
        SynthesisSource("component:sample", "COMPONENT", evidenceRefs = listOf(ClaimFixtures.highEvidence.id)),
        SynthesisSource(ClaimFixtures.contract.id, "CONTRACT", evidenceRefs = listOf(ClaimFixtures.contract.id)),
    )

    private fun request() = ExecutiveSummaryRequestBuilder.request(sources(), "Canonical facts across sources.", "fixture", "fixture-model")

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
