package io.docpilot.core.documentation.enrichment

import io.docpilot.core.api.AiProvider
import io.docpilot.core.model.ai.*
import kotlin.test.*

class DocumentationEnrichmentTest {
    @Test fun `applies bounded narrative and reuses identical identity`() {
        val provider = StubProvider("Useful bounded context.")
        val engine = DocumentationEnrichmentEngine(provider)
        val first = engine.enrich(request())
        val second = engine.enrich(request())
        assertEquals(DocumentationEnrichmentStatus.APPLIED, first.record.status)
        assertEquals(first.record.narrativeSha256, second.record.narrativeSha256)
        assertEquals(1, provider.calls)
        assertTrue(second.record.cached)
        assertFalse(second.record.providerInvoked)
    }

    @Test fun `rejects canonical field and markdown redefinition`() {
        val result = DocumentationEnrichmentEngine(StubProvider("## Evidence\n- fabricated" )).enrich(request())
        assertEquals(DocumentationEnrichmentStatus.REJECTED, result.record.status)
        assertNull(result.narrative)
    }

    @Test fun `provider failure preserves deterministic fallback`() {
        val provider = object : AiProvider {
            override val descriptor = descriptor("fixture")
            override fun generate(request: AiRequest) = AiGenerationResult.Failure(
                AiError(AiErrorCode.PROVIDER_FAILURE, "api_key=secret", false, descriptor.id))
        }
        val result = DocumentationEnrichmentEngine(provider).enrich(request())
        assertEquals(DocumentationEnrichmentStatus.FALLBACK, result.record.status)
        assertFalse(result.record.diagnostic.orEmpty().contains("secret"))
    }

    @Test fun `managed section does not alter canonical bytes`() {
        val canonical = "# Title\n\nBody\n\n## Evidence\n\n- source\n"
        val enriched = DocumentationEnrichmentSections.apply(canonical, "Context only.")
        val restored = enriched.replace(Regex("(?s)\\n*${Regex.escape(DocumentationEnrichmentSections.START)}.*?${Regex.escape(DocumentationEnrichmentSections.END)}\\n*"), "\n\n")
        assertEquals(canonical.trim(), restored.trim())
    }

    private fun request() = DocumentationEnrichmentRequest(
        DocumentationEnrichmentTarget("project:test", "architecture-description", "Test", "PROJECT_OVERVIEW"),
        "Canonical facts.", "fixture", "fixture-model")

    private class StubProvider(private val text: String) : AiProvider {
        var calls = 0
        override val descriptor = descriptor("fixture")
        override fun generate(request: AiRequest): AiGenerationResult { calls++
            return AiGenerationResult.Success(AiResponse(descriptor.id, request.modelId, text, AiFinishReason.STOP)) }
    }

    companion object {
        private fun descriptor(id: String) = AiProviderDescriptor(AiProviderId(id), "Fixture", "1.0.0",
            AiExecutionLocation.LOCAL, setOf(AiCapability.TEXT_GENERATION))
    }
}
