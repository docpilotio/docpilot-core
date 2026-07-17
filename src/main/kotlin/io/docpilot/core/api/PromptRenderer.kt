package io.docpilot.core.api

import io.docpilot.core.model.prompt.PromptTemplate
import io.docpilot.core.model.prompt.PromptVariables
import io.docpilot.core.model.prompt.RenderedPrompt

/**
 * Renders a provider-neutral prompt from a template and variables.
 */
fun interface PromptRenderer {
    fun render(
        template: PromptTemplate,
        variables: PromptVariables,
    ): RenderedPrompt
}
