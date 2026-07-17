package io.docpilot.core.document.service

import io.docpilot.core.document.DocumentFormat
import io.docpilot.core.generation.GenerationPipeline
import io.docpilot.core.generation.GenerationRequest
import io.docpilot.core.generation.GenerationResult
import io.docpilot.core.model.ai.AiError
import io.docpilot.core.model.ai.AiErrorCode
import io.docpilot.core.model.ai.AiFinishReason
import io.docpilot.core.model.ai.AiGenerationResult
import io.docpilot.core.model.ai.AiModelId
import io.docpilot.core.model.ai.AiProviderId
import io.docpilot.core.model.ai.AiResponse
import io.docpilot.core.model.evidence.EvidenceCollection
import io.docpilot.core.model.knowledge.KnowledgeBuildResult
import io.docpilot.core.model.knowledge.KnowledgeGraph
import io.docpilot.core.model.knowledge.KnowledgeQuery
import io.docpilot.core.model.knowledge.KnowledgeResult
import io.docpilot.core.model.prompt.PromptTemplate
import io.docpilot.core.model.prompt.PromptVariables
import io.docpilot.core.model.prompt.RenderedPrompt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class DefaultDocumentServiceTest {

    @Test
    fun `converts successful generation into document`() {
        val generationRequest = request()
        var capturedRequest: GenerationRequest? = null
        val pipeline = GenerationPipeline { actual ->
            capturedRequest = actual
            successResult("Generated architecture body")
        }

        val document = DefaultDocumentService(pipeline).generate(
            DocumentRequest(
                title = "Architecture",
                generation = generationRequest,
                sectionId = "overview",
                sectionTitle = "Overview",
                format = DocumentFormat.PLAIN_TEXT,
                metadata = mapOf("project" to "docpilot-core"),
            ),
        )

        assertSame(generationRequest, capturedRequest)
        assertEquals("Architecture", document.title)
        assertEquals(DocumentFormat.PLAIN_TEXT, document.format)
        assertEquals("overview", document.sections.single().id)
        assertEquals("Overview", document.sections.single().title)
        assertEquals("Generated architecture body", document.sections.single().content)
        assertEquals("ai-generated-document", document.metadata?.type)
        assertEquals(
            mapOf(
                "project" to "docpilot-core",
                "ai.providerId" to "test",
                "ai.modelId" to "test-model",
                "ai.finishReason" to "STOP",
            ),
            document.metadata?.attributes,
        )
    }

    @Test
    fun `converts provider failure into document generation exception`() {
        val error = AiError(
            code = AiErrorCode.UNAVAILABLE,
            message = "Provider unavailable",
            retryable = true,
            providerId = AiProviderId("test"),
        )
        val pipeline = GenerationPipeline {
            GenerationResult(
                knowledge = emptyKnowledgeResult(),
                prompt = RenderedPrompt(
                    templateName = "document",
                    content = "prompt",
                ),
                ai = AiGenerationResult.Failure(error),
            )
        }

        val exception = assertFailsWith<DocumentGenerationException> {
            DefaultDocumentService(pipeline).generate(
                DocumentRequest(
                    title = "Architecture",
                    generation = request(),
                ),
            )
        }

        assertSame(error, exception.error)
        assertEquals(
            "Document generation failed [UNAVAILABLE]: Provider unavailable",
            exception.message,
        )
    }

    @Test
    fun `rejects reserved generation metadata overrides`() {
        assertFailsWith<IllegalArgumentException> {
            DocumentRequest(
                title = "Architecture",
                generation = request(),
                metadata = mapOf("ai.providerId" to "caller-value"),
            )
        }
    }

    private fun successResult(content: String): GenerationResult =
        GenerationResult(
            knowledge = emptyKnowledgeResult(),
            prompt = RenderedPrompt(
                templateName = "document",
                content = "prompt",
            ),
            ai = AiGenerationResult.Success(
                AiResponse(
                    providerId = AiProviderId("test"),
                    modelId = AiModelId("test-model"),
                    content = content,
                    finishReason = AiFinishReason.STOP,
                ),
            ),
        )

    private fun emptyKnowledgeResult(): KnowledgeResult =
        KnowledgeResult(
            nodes = emptyList(),
            edges = emptyList(),
            evidence = emptyList(),
        )

    private fun request(): GenerationRequest =
        GenerationRequest(
            knowledge = KnowledgeBuildResult(
                graph = KnowledgeGraph(
                    nodes = emptyList(),
                    edges = emptyList(),
                ),
                evidence = EvidenceCollection(emptyList()),
            ),
            query = KnowledgeQuery(name = "architecture"),
            template = PromptTemplate(
                name = "document",
                content = "{{knowledge}}",
            ),
            variables = PromptVariables.EMPTY,
            modelId = AiModelId("test-model"),
        )
}
