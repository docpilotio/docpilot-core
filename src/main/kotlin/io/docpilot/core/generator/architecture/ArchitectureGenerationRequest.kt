package io.docpilot.core.generator.architecture

import io.docpilot.core.document.DocumentFormat
import io.docpilot.core.model.ai.AiModelId
import io.docpilot.core.model.ai.AiResponseFormat
import io.docpilot.core.model.knowledge.KnowledgeBuildResult
import io.docpilot.core.model.knowledge.KnowledgeQuery
import io.docpilot.core.model.prompt.PromptVariables

/**
 * Input required to generate one architecture document.
 */
data class ArchitectureGenerationRequest(
    val knowledge: KnowledgeBuildResult,
    val modelId: AiModelId,
    val title: String = DEFAULT_TITLE,
    val query: KnowledgeQuery = KnowledgeQuery(name = DEFAULT_QUERY_NAME),
    val variables: PromptVariables = PromptVariables.EMPTY,
    val format: DocumentFormat = DocumentFormat.MARKDOWN,
    val temperature: Double? = null,
    val maxOutputTokens: Int? = DEFAULT_MAX_OUTPUT_TOKENS,
    val responseFormat: AiResponseFormat = AiResponseFormat.TEXT,
    val generationMetadata: Map<String, String> = emptyMap(),
    val documentMetadata: Map<String, String> = emptyMap(),
) {
    init {
        require(title.isNotBlank()) {
            "Architecture document title must not be blank."
        }
        require(temperature == null || temperature in 0.0..2.0) {
            "Architecture generation temperature must be between 0.0 and 2.0."
        }
        require(maxOutputTokens == null || maxOutputTokens > 0) {
            "Architecture generation maxOutputTokens must be greater than zero."
        }
        require(generationMetadata.keys.none(String::isBlank)) {
            "Architecture generation metadata keys must not be blank."
        }
        require(documentMetadata.keys.none(String::isBlank)) {
            "Architecture document metadata keys must not be blank."
        }
    }

    companion object {
        const val DEFAULT_MAX_OUTPUT_TOKENS: Int = 2_048
        const val DEFAULT_TITLE: String = "Architecture"
        const val DEFAULT_QUERY_NAME: String = "architecture"
    }
}
