package io.docpilot.core.template

import io.docpilot.core.model.prompt.PromptTemplate

/**
 * Provider-neutral definition used to generate a particular kind of document.
 *
 * The prompt remains compatible with the RFC-0015 prompt renderer and may use
 * `{{knowledge}}` plus caller-supplied variables.
 */
data class DocumentTemplate(
    val id: TemplateId,
    val name: String,
    val description: String,
    val prompt: PromptTemplate,
    val defaultSectionTitle: String = "Generated Content",
    val metadata: Map<String, String> = emptyMap(),
) {
    init {
        require(name.isNotBlank()) { "Document template name must not be blank." }
        require(description.isNotBlank()) { "Document template description must not be blank." }
        require(defaultSectionTitle.isNotBlank()) {
            "Document template defaultSectionTitle must not be blank."
        }
        require(metadata.keys.none(String::isBlank)) {
            "Document template metadata keys must not be blank."
        }
    }
}
