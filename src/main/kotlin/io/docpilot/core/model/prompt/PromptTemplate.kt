package io.docpilot.core.model.prompt

/**
 * Reusable UTF-8 prompt template.
 */
data class PromptTemplate(
    val name: String,
    val content: String,
) {
    init {
        require(name.isNotBlank()) {
            "Prompt template name must not be blank."
        }
        require(content.isNotBlank()) {
            "Prompt template content must not be blank."
        }
    }
}
