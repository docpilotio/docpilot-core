package io.docpilot.core.generation

import io.docpilot.core.api.AiProvider
import io.docpilot.core.knowledge.DefaultKnowledgeRetriever
import io.docpilot.core.model.ai.AiCapability
import io.docpilot.core.model.ai.AiExecutionLocation
import io.docpilot.core.model.ai.AiFinishReason
import io.docpilot.core.model.ai.AiGenerationResult
import io.docpilot.core.model.ai.AiMessageRole
import io.docpilot.core.model.ai.AiModelId
import io.docpilot.core.model.ai.AiProviderDescriptor
import io.docpilot.core.model.ai.AiProviderId
import io.docpilot.core.model.ai.AiRequest
import io.docpilot.core.model.ai.AiResponse
import io.docpilot.core.model.evidence.Evidence
import io.docpilot.core.model.evidence.EvidenceCollection
import io.docpilot.core.model.evidence.EvidenceId
import io.docpilot.core.model.evidence.EvidenceLocation
import io.docpilot.core.model.evidence.EvidenceType
import io.docpilot.core.model.knowledge.KnowledgeBuildResult
import io.docpilot.core.model.knowledge.KnowledgeGraph
import io.docpilot.core.model.knowledge.KnowledgeNode
import io.docpilot.core.model.knowledge.KnowledgeNodeKind
import io.docpilot.core.model.knowledge.KnowledgeQuery
import io.docpilot.core.model.prompt.PromptTemplate
import io.docpilot.core.model.prompt.PromptVariables
import io.docpilot.core.prompt.DefaultPromptRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultGenerationPipelineTest {

    @Test
    fun `retrieves renders and calls injected provider`() {
        var capturedRequest: AiRequest? = null
        val provider = provider { request ->
            capturedRequest = request
            AiGenerationResult.Success(
                response = AiResponse(
                    providerId = AiProviderId("test"),
                    modelId = request.modelId,
                    content = "Generated document",
                    finishReason = AiFinishReason.STOP,
                ),
            )
        }

        val result = DefaultGenerationPipeline(
            knowledgeRetriever = DefaultKnowledgeRetriever(),
            promptRenderer = DefaultPromptRenderer(),
            aiProvider = provider,
        ).generate(
            GenerationRequest(
                knowledge = knowledge(),
                query = KnowledgeQuery(name = "repository"),
                template = PromptTemplate(
                    name = "architecture",
                    content = "# {{title}}\n\n{{knowledge}}",
                ),
                variables = PromptVariables(
                    mapOf("title" to "Architecture"),
                ),
                modelId = AiModelId("test-model"),
                maxOutputTokens = 400,
            ),
        )

        assertEquals(
            listOf("symbol:TaskRepository"),
            result.knowledge.nodes.map { it.id },
        )
        assertTrue(result.prompt.content.contains("# Architecture"))
        assertTrue(result.prompt.content.contains("symbol:TaskRepository"))

        val request = requireNotNull(capturedRequest)
        assertEquals(AiMessageRole.USER, request.messages.single().role)
        assertEquals(result.prompt.content, request.messages.single().content)
        assertEquals(400, request.maxOutputTokens)
        assertIs<AiGenerationResult.Success>(result.ai)
    }

    @Test
    fun `preserves provider failure as generation result`() {
        val provider = provider {
            AiGenerationResult.Failure(
                error = io.docpilot.core.model.ai.AiError(
                    code = io.docpilot.core.model.ai.AiErrorCode.UNAVAILABLE,
                    message = "Provider unavailable",
                    retryable = true,
                    providerId = AiProviderId("test"),
                ),
            )
        }

        val result = DefaultGenerationPipeline(
            knowledgeRetriever = DefaultKnowledgeRetriever(),
            promptRenderer = DefaultPromptRenderer(),
            aiProvider = provider,
        ).generate(request())

        assertIs<AiGenerationResult.Failure>(result.ai)
    }

    @Test
    fun `rejects caller supplied reserved knowledge variable`() {
        assertFailsWith<IllegalArgumentException> {
            request(
                variables = PromptVariables(
                    mapOf("knowledge" to "caller value"),
                ),
            )
        }
    }

    private fun request(
        variables: PromptVariables = PromptVariables.EMPTY,
    ): GenerationRequest =
        GenerationRequest(
            knowledge = knowledge(),
            query = KnowledgeQuery(name = "repository"),
            template = PromptTemplate(
                name = "simple",
                content = "{{knowledge}}",
            ),
            variables = variables,
            modelId = AiModelId("test-model"),
        )

    private fun provider(
        generate: (AiRequest) -> AiGenerationResult,
    ): AiProvider =
        object : AiProvider {
            override val descriptor = AiProviderDescriptor(
                id = AiProviderId("test"),
                displayName = "Test Provider",
                version = "0.1.0",
                executionLocation = AiExecutionLocation.LOCAL,
                capabilities = setOf(AiCapability.TEXT_GENERATION),
            )

            override fun generate(
                request: AiRequest,
            ): AiGenerationResult = generate(request)
        }

    private fun knowledge(): KnowledgeBuildResult {
        val evidence = Evidence(
            id = EvidenceId("evidence:repository"),
            type = EvidenceType.SYMBOL_DECLARATION,
            location = EvidenceLocation(
                relativePath = "core/data/TaskRepository.kt",
            ),
            summary = "TaskRepository declaration",
        )
        val node = KnowledgeNode(
            id = "symbol:TaskRepository",
            name = "TaskRepository",
            kind = KnowledgeNodeKind.CLASS,
            evidenceRefs = setOf(evidence.id.value),
        )

        return KnowledgeBuildResult(
            graph = KnowledgeGraph(
                nodes = listOf(node),
                edges = emptyList(),
            ),
            evidence = EvidenceCollection(
                items = listOf(evidence),
            ),
        )
    }
}
