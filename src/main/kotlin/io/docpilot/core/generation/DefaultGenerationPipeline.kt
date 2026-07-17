package io.docpilot.core.generation

import io.docpilot.core.api.AiProvider
import io.docpilot.core.api.KnowledgeRetriever
import io.docpilot.core.api.PromptRenderer
import io.docpilot.core.generation.context.DefaultKnowledgeContextRenderer
import io.docpilot.core.generation.context.KnowledgeContextPolicy
import io.docpilot.core.generation.context.KnowledgeContextRenderer
import io.docpilot.core.model.ai.AiMessage
import io.docpilot.core.model.ai.AiMessageRole
import io.docpilot.core.model.ai.AiRequest
import io.docpilot.core.model.prompt.PromptVariables

/**
 * Connects deterministic knowledge retrieval, bounded context rendering,
 * prompt rendering, and one explicitly supplied AI provider.
 */
class DefaultGenerationPipeline(
    private val knowledgeRetriever: KnowledgeRetriever,
    private val promptRenderer: PromptRenderer,
    private val aiProvider: AiProvider,
    private val knowledgeContextRenderer: KnowledgeContextRenderer =
        DefaultKnowledgeContextRenderer(),
    private val knowledgeContextPolicy: KnowledgeContextPolicy =
        KnowledgeContextPolicy(),
) : GenerationPipeline {

    override fun generate(
        request: GenerationRequest,
    ): GenerationResult {
        val retrievedKnowledge = knowledgeRetriever.retrieve(
            knowledge = request.knowledge,
            query = request.query,
        )

        val knowledgeContext = knowledgeContextRenderer.render(
            knowledge = retrievedKnowledge,
            policy = knowledgeContextPolicy,
        )

        val variables = PromptVariables(
            request.variables.values + mapOf(
                GenerationRequest.KNOWLEDGE_VARIABLE to
                    knowledgeContext.content,
            ),
        )

        val prompt = promptRenderer.render(
            template = request.template,
            variables = variables,
        )

        val aiResult = aiProvider.generate(
            AiRequest(
                modelId = request.modelId,
                messages = listOf(
                    AiMessage(
                        role = AiMessageRole.USER,
                        content = prompt.content,
                    ),
                ),
                temperature = request.temperature,
                maxOutputTokens = request.maxOutputTokens,
                responseFormat = request.responseFormat,
                metadata = request.metadata,
            ),
        )

        return GenerationResult(
            knowledge = retrievedKnowledge,
            prompt = prompt,
            ai = aiResult,
        )
    }
}
