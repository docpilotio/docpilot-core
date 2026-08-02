package io.docpilot.core.documentation.synthesis

import io.docpilot.core.api.AiProvider
import io.docpilot.core.documentation.enrichment.DocumentationEnrichmentStatus
import io.docpilot.core.model.ai.*
import io.docpilot.core.specification.claim.ClaimFixtures
import kotlin.test.*

class DocumentationSynthesisTest {
    private val specification = ClaimFixtures.specification()

    @Test
    fun `single source request is rejected at construction`() {
        assertFailsWith<IllegalArgumentException> {
            SynthesisRequest("EXECUTIVE_SUMMARY", listOf(source("component:sample")), "Facts.", "fixture", "fixture-model")
        }
    }

    @Test
    fun `duplicate source artifact ids are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            SynthesisRequest(
                "EXECUTIVE_SUMMARY",
                listOf(source("component:sample"), source("component:sample")),
                "Facts.", "fixture", "fixture-model",
            )
        }
    }

    @Test
    fun `valid multi-source request applies and reuses identical identity`() {
        val provider = StubProvider("# Advisory Summary\n\n- Point one\n- Point two\n")
        val engine = SynthesisEngine(provider)
        val first = engine.synthesize(specification, request())
        val second = engine.synthesize(specification, request())

        assertEquals(DocumentationEnrichmentStatus.APPLIED, first.record.status)
        assertEquals(DocumentationTier.ADVISORY, first.record.tier)
        assertNotNull(first.record.contentSha256)
        assertEquals(1, provider.calls)
        assertTrue(second.record.cached)
        assertFalse(second.record.providerInvoked)
    }

    @Test
    fun `source evidenceRef missing from specification fails closed before invoking provider`() {
        val provider = StubProvider("content")
        val engine = SynthesisEngine(provider)
        val bad = request().copy(
            sources = listOf(
                source("component:sample", evidenceRefs = listOf("evidence:missing")),
                source(ClaimFixtures.contract.id),
            ),
        )
        assertFailsWith<IllegalArgumentException> { engine.synthesize(specification, bad) }
        assertEquals(0, provider.calls)
    }

    @Test
    fun `contract-only source ref succeeds`() {
        val provider = StubProvider("# Advisory\n\nBody.\n")
        val engine = SynthesisEngine(provider)
        val withContractSource = request().copy(
            sources = listOf(
                source("component:sample", evidenceRefs = listOf(ClaimFixtures.highEvidence.id)),
                source(ClaimFixtures.contract.id, evidenceRefs = listOf(ClaimFixtures.contract.id)),
            ),
        )
        val result = engine.synthesize(specification, withContractSource)
        assertEquals(DocumentationEnrichmentStatus.APPLIED, result.record.status)
    }

    @Test
    fun `provider failure preserves deterministic fallback with redacted diagnostic`() {
        val provider = object : AiProvider {
            override val descriptor = descriptor("fixture")
            override fun generate(request: AiRequest) = AiGenerationResult.Failure(
                AiError(AiErrorCode.PROVIDER_FAILURE, "api_key=secret", false, descriptor.id),
            )
        }
        val result = SynthesisEngine(provider).synthesize(specification, request())
        assertEquals(DocumentationEnrichmentStatus.FALLBACK, result.record.status)
        assertFalse(result.record.diagnostic.orEmpty().contains("secret"))
    }

    @Test
    fun `overlong content is rejected`() {
        val provider = StubProvider("x".repeat(20_001))
        val result = SynthesisEngine(provider).synthesize(specification, request())
        assertEquals(DocumentationEnrichmentStatus.REJECTED, result.record.status)
        assertNull(result.content)
    }

    @Test
    fun `markdown structure in content is allowed unlike enrichment narrative`() {
        val provider = StubProvider("## Heading\n\n- item one\n- item two\n\n```kotlin\nval x = 1\n```\n")
        val result = SynthesisEngine(provider).synthesize(specification, request())
        assertEquals(DocumentationEnrichmentStatus.APPLIED, result.record.status)
    }

    private fun request() = SynthesisRequest(
        "EXECUTIVE_SUMMARY",
        listOf(
            source("component:sample", evidenceRefs = listOf(ClaimFixtures.highEvidence.id)),
            source(ClaimFixtures.contract.id, sourceKind = "CONTRACT", evidenceRefs = listOf(ClaimFixtures.contract.id)),
        ),
        "Canonical facts across sources.", "fixture", "fixture-model",
    )

    private fun source(artifactId: String, sourceKind: String = "COMPONENT", evidenceRefs: List<String> = emptyList()) =
        SynthesisSource(artifactId, sourceKind, evidenceRefs = evidenceRefs)

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
