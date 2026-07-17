package io.docpilot.core.model.prompt

/**
 * Fully rendered provider-neutral prompt.
 */
data class RenderedPrompt(
    val templateName: String,
    val content: String,
) {
    init {
        require(templateName.isNotBlank()) {
            "Rendered prompt template name must not be blank."
        }
        require(content.isNotBlank()) {
            "Rendered prompt content must not be blank."
        }
    }
}
