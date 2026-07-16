package io.docpilot.provider.ollama

import io.docpilot.core.api.AiProvider
import io.docpilot.core.model.ai.AiFinishReason
import io.docpilot.core.model.ai.AiGenerationResult
import io.docpilot.core.model.ai.AiProviderDescriptor
import io.docpilot.core.model.ai.AiRequest
import io.docpilot.core.model.ai.AiResponse
import io.docpilot.core.model.plugin.PluginContext
import io.docpilot.core.model.plugin.PluginStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class OllamaProviderPluginTest {

    @Test
    fun `bridges plugin execution to AI provider`() {
        val plugin = OllamaProviderPlugin(
            provider = object : AiProvider {
                override val descriptor =
                    OllamaAiProvider().descriptor

                override fun generate(
                    request: AiRequest,
                ): AiGenerationResult =
                    AiGenerationResult.Success(
                        AiResponse(
                            providerId =
                                OllamaAiProvider.PROVIDER_ID,
                            modelId = request.modelId,
                            content = "local response",
                            finishReason =
                                AiFinishReason.STOP,
                        ),
                    )
            },
        )

        val result = plugin.execute(
            PluginContext(
                options = mapOf(
                    "prompt" to "Hello",
                    "model" to "qwen3:8b",
                ),
            ),
        )

        assertEquals(PluginStatus.SUCCESS, result.status)
        assertEquals(
            "local response",
            result.messages.single().text,
        )
    }
}
