package io.docpilot.core.prompt

import io.docpilot.core.api.PromptRenderer
import io.docpilot.core.model.prompt.PromptTemplate
import io.docpilot.core.model.prompt.PromptVariables
import io.docpilot.core.model.prompt.RenderedPrompt

/**
 * Performs strict {{variable}} replacement without control-flow syntax.
 */
class DefaultPromptRenderer : PromptRenderer {

    override fun render(
        template: PromptTemplate,
        variables: PromptVariables,
    ): RenderedPrompt {
        val rendered = VARIABLE_PATTERN.replace(
            template.content,
        ) { match ->
            val name = match.groupValues[1]

            variables[name]
                ?: throw IllegalArgumentException(
                    "Missing prompt variable: $name",
                )
        }

        require(!UNRESOLVED_PATTERN.containsMatchIn(rendered)) {
            "Prompt contains an invalid or unresolved variable expression."
        }

        return RenderedPrompt(
            templateName = template.name,
            content = rendered,
        )
    }

    private companion object {
        val VARIABLE_PATTERN = Regex(
            "\\{\\{\\s*([A-Za-z][A-Za-z0-9_.-]*)\\s*}}",
        )

        val UNRESOLVED_PATTERN = Regex("\\{\\{")
    }
}
