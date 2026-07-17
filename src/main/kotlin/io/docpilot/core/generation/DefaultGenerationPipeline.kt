package io.docpilot.core.generation

import io.docpilot.core.api.AiProvider
import io.docpilot.core.api.KnowledgeRetriever
import io.docpilot.core.api.PromptRenderer
import io.docpilot.core.model.ai.AiMessage
import io.docpilot.core.model.ai.AiMessageRole
import io.docpilot.core.model.ai.AiRequest
import io.docpilot.core.model.knowledge.KnowledgeResult
import io.docpilot.core.model.prompt.PromptVariables

/**
 * Connects deterministic knowledge retrieval, prompt rendering, and one
 * explicitly supplied AI provider.
 */
class DefaultGenerationPipeline(
    private val knowledgeRetriever: KnowledgeRetriever,
    private val promptRenderer: PromptRenderer,
    private val aiProvider: AiProvider,
) : GenerationPipeline {

    override fun generate(
        request: GenerationRequest,
    ): GenerationResult {
        val retrievedKnowledge = knowledgeRetriever.retrieve(
            knowledge = request.knowledge,
            query = request.query,
        )

        val variables = PromptVariables(
            request.variables.values + mapOf(
                GenerationRequest.KNOWLEDGE_VARIABLE to
                    renderKnowledge(retrievedKnowledge),
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

    private fun renderKnowledge(
        knowledge: KnowledgeResult,
    ): String = buildString {
        appendLine("## Nodes")
        if (knowledge.nodes.isEmpty()) {
            appendLine("- None")
        } else {
            knowledge.nodes.forEach { node ->
                append("- ")
                append(node.id)
                append(" | ")
                append(node.kind)
                append(" | ")
                appendLine(node.name)
            }
        }

        appendLine()
        appendLine("## Relationships")
        if (knowledge.edges.isEmpty()) {
            appendLine("- None")
        } else {
            knowledge.edges.forEach { edge ->
                append("- ")
                append(edge.id)
                append(" | ")
                append(edge.sourceNodeId)
                append(" --")
                append(edge.relationship)
                append("--> ")
                appendLine(edge.targetNodeId)
            }
        }

        appendLine()
        appendLine("## Evidence")
        if (knowledge.evidence.isEmpty()) {
            appendLine("- None")
        } else {
            knowledge.evidence.forEach { evidence ->
                append("- ")
                append(evidence.id.value)
                append(" | ")
                append(evidence.location.relativePath)
                append(" | ")
                appendLine(evidence.summary)
            }
        }
    }.trimEnd()
}
