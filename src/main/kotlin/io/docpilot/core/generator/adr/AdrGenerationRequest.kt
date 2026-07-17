package io.docpilot.core.generator.adr

import io.docpilot.core.document.DocumentFormat
import io.docpilot.core.model.ai.AiModelId
import io.docpilot.core.model.ai.AiResponseFormat
import io.docpilot.core.model.knowledge.KnowledgeBuildResult
import io.docpilot.core.model.knowledge.KnowledgeQuery
import io.docpilot.core.model.prompt.PromptVariables

/** Input required to generate one Architecture Decision Record. */
data class AdrGenerationRequest(
    val knowledge: KnowledgeBuildResult,
    val modelId: AiModelId,
    val title: String,
    val context: String,
    val decision: String,
    val consequences: String,
    val alternatives: String = "No alternatives were supplied.",
    val status: AdrStatus = AdrStatus.ACCEPTED,
    val query: KnowledgeQuery = KnowledgeQuery(name = DEFAULT_QUERY_NAME),
    val variables: PromptVariables = PromptVariables.EMPTY,
    val format: DocumentFormat = DocumentFormat.MARKDOWN,
    val temperature: Double? = null,
    val maxOutputTokens: Int? = null,
    val responseFormat: AiResponseFormat = AiResponseFormat.TEXT,
    val generationMetadata: Map<String, String> = emptyMap(),
    val documentMetadata: Map<String, String> = emptyMap(),
) {
    init {
        require(title.isNotBlank()) { "ADR title must not be blank." }
        require(context.isNotBlank()) { "ADR context must not be blank." }
        require(decision.isNotBlank()) { "ADR decision must not be blank." }
        require(consequences.isNotBlank()) { "ADR consequences must not be blank." }
        require(alternatives.isNotBlank()) { "ADR alternatives must not be blank." }
        require(temperature == null || temperature in 0.0..2.0) {
            "ADR generation temperature must be between 0.0 and 2.0."
        }
        require(maxOutputTokens == null || maxOutputTokens > 0) {
            "ADR generation maxOutputTokens must be greater than zero."
        }
        require(generationMetadata.keys.none(String::isBlank)) {
            "ADR generation metadata keys must not be blank."
        }
        require(documentMetadata.keys.none(String::isBlank)) {
            "ADR document metadata keys must not be blank."
        }
        require(RESERVED_VARIABLES.none { it in variables.values }) {
            "ADR prompt variables must not redefine reserved ADR variables."
        }
    }

    companion object {
        const val DEFAULT_QUERY_NAME: String = "adr"
        const val TITLE_VARIABLE: String = "adr.title"
        const val STATUS_VARIABLE: String = "adr.status"
        const val CONTEXT_VARIABLE: String = "adr.context"
        const val DECISION_VARIABLE: String = "adr.decision"
        const val ALTERNATIVES_VARIABLE: String = "adr.alternatives"
        const val CONSEQUENCES_VARIABLE: String = "adr.consequences"

        val RESERVED_VARIABLES: Set<String> = setOf(
            TITLE_VARIABLE,
            STATUS_VARIABLE,
            CONTEXT_VARIABLE,
            DECISION_VARIABLE,
            ALTERNATIVES_VARIABLE,
            CONSEQUENCES_VARIABLE,
        )
    }
}
