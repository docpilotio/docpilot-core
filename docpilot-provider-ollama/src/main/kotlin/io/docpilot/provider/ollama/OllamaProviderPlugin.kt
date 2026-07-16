package io.docpilot.provider.ollama

import io.docpilot.core.api.AiProvider
import io.docpilot.core.api.AiProviderPlugin
import io.docpilot.core.model.ai.AiGenerationResult
import io.docpilot.core.model.ai.AiMessage
import io.docpilot.core.model.ai.AiMessageRole
import io.docpilot.core.model.ai.AiModelId
import io.docpilot.core.model.ai.AiRequest
import io.docpilot.core.model.plugin.PluginCapabilities
import io.docpilot.core.model.plugin.PluginCapability
import io.docpilot.core.model.plugin.PluginCategory
import io.docpilot.core.model.plugin.PluginContext
import io.docpilot.core.model.plugin.PluginDescriptor
import io.docpilot.core.model.plugin.PluginId
import io.docpilot.core.model.plugin.PluginMessage
import io.docpilot.core.model.plugin.PluginMessageLevel
import io.docpilot.core.model.plugin.PluginResult
import io.docpilot.core.model.plugin.PluginStatus

class OllamaProviderPlugin(
    override val provider: AiProvider = OllamaAiProvider(),
) : AiProviderPlugin {

    override val descriptor = PluginDescriptor(
        id = PluginId("docpilot.ai.ollama"),
        displayName = "Ollama AI Provider",
        category = PluginCategory.OUTPUT,
        version = "0.1.0",
        description =
            "Generates AI responses using a local Ollama server.",
        capabilities = setOf(
            PluginCapability(
                id = PluginCapabilities.AI_GENERATION,
            ),
        ),
    )

    override fun execute(
        context: PluginContext,
    ): PluginResult {
        val prompt = context.options["prompt"]
            ?: return failed(
                "Missing plugin option: prompt",
            )

        val model = context.options["model"]
            ?: OllamaConfiguration.fromEnvironment().defaultModel

        return when (
            val result = provider.generate(
                AiRequest(
                    modelId = AiModelId(model),
                    messages = listOf(
                        AiMessage(
                            role = AiMessageRole.USER,
                            content = prompt,
                        ),
                    ),
                ),
            )
        ) {
            is AiGenerationResult.Success ->
                PluginResult(
                    status = PluginStatus.SUCCESS,
                    messages = listOf(
                        PluginMessage(
                            level = PluginMessageLevel.INFO,
                            text = result.response.content,
                        ),
                    ),
                )

            is AiGenerationResult.Failure ->
                failed(result.error.message)
        }
    }

    private fun failed(
        message: String,
    ): PluginResult =
        PluginResult(
            status = PluginStatus.FAILED,
            messages = listOf(
                PluginMessage(
                    level = PluginMessageLevel.ERROR,
                    text = message,
                ),
            ),
        )
}
