package io.docpilot.core.generation

import io.docpilot.core.model.ai.AiModelId
import io.docpilot.core.model.ai.AiResponseFormat
import io.docpilot.core.model.knowledge.KnowledgeBuildResult
import io.docpilot.core.model.knowledge.KnowledgeQuery
import io.docpilot.core.model.prompt.PromptTemplate
import io.docpilot.core.model.prompt.PromptVariables

data class GenerationRequest(
    val knowledge: KnowledgeBuildResult,
    val query: KnowledgeQuery,
    val template: PromptTemplate,
    val variables: PromptVariables,
    val modelId: AiModelId,
    val temperature: Double? = null,
    val maxOutputTokens: Int? = null,
    val responseFormat: AiResponseFormat = AiResponseFormat.TEXT,
    val metadata: Map<String, String> = emptyMap(),
) {
    init {
        require(KNOWLEDGE_VARIABLE !in variables.values) {
            "Prompt variable '$KNOWLEDGE_VARIABLE' is reserved by the generation pipeline."
        }
    }

    companion object {
        const val KNOWLEDGE_VARIABLE: String = "knowledge"
    }
}
