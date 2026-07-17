package io.docpilot.core.model.prompt

/**
 * Values used to render a prompt template.
 */
data class PromptVariables(
    val values: Map<String, String>,
) {
    init {
        require(values.keys.none(String::isBlank)) {
            "Prompt variable names must not be blank."
        }
    }

    operator fun get(name: String): String? = values[name]

    companion object {
        val EMPTY = PromptVariables(emptyMap())
    }
}
