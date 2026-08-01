package io.docpilot.provider.openai

import io.docpilot.core.api.AiProvider
import io.docpilot.core.api.AiProviderPlugin
import io.docpilot.core.model.ai.*
import io.docpilot.core.model.plugin.*

class OpenAiProviderPlugin(
    private val environment: Map<String, String> = System.getenv(),
    providerFactory: ((OpenAiConfiguration) -> AiProvider)? = null,
) : AiProviderPlugin {

    private val configuration: OpenAiConfiguration by lazy {
        OpenAiConfiguration.fromEnvironment(environment)
    }

    override val provider: AiProvider by lazy {
        providerFactory?.invoke(configuration)
            ?: OpenAiProvider(configuration)
    }

    override val descriptor = PluginDescriptor(
        id = PluginId("docpilot.ai.openai"),
        displayName = "OpenAI Provider",
        category = PluginCategory.OUTPUT,
        version = "0.1.0",
        description = "Generates AI responses using the OpenAI Responses API.",
        capabilities = setOf(
            PluginCapability(PluginCapabilities.AI_GENERATION),
        ),
    )

    override fun execute(context: PluginContext): PluginResult {
        val prompt = context.options["prompt"]
            ?: return failed("Missing plugin option: prompt")
        val model = context.options["model"]
            ?: environment["DOCPILOT_OPENAI_MODEL"]
            ?: OpenAiConfiguration.DEFAULT_MODEL

        return try {
            when (val result = provider.generate(
                AiRequest(
                    modelId = AiModelId(model),
                    messages = listOf(AiMessage(AiMessageRole.USER, prompt)),
                ),
            )) {
                is AiGenerationResult.Success -> PluginResult(
                    status = PluginStatus.SUCCESS,
                    messages = listOf(
                        PluginMessage(PluginMessageLevel.INFO, result.response.content),
                    ),
                )
                is AiGenerationResult.Failure -> failed(result.error.message)
            }
        } catch (exception: IllegalArgumentException) {
            failed(exception.message ?: "OpenAI configuration is invalid.")
        } catch (exception: IllegalStateException) {
            failed(exception.message ?: "OpenAI configuration is unavailable.")
        }
    }

    private fun failed(message: String) = PluginResult(
        status = PluginStatus.FAILED,
        messages = listOf(PluginMessage(PluginMessageLevel.ERROR, message)),
    )
}
