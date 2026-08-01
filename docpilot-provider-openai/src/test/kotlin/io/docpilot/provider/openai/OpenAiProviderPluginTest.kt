package io.docpilot.provider.openai

import io.docpilot.core.api.AiProvider
import io.docpilot.core.api.DocPilotPlugin
import io.docpilot.core.model.ai.*
import io.docpilot.core.model.plugin.PluginContext
import io.docpilot.core.model.plugin.PluginStatus
import kotlin.test.*
import java.util.ServiceLoader

class OpenAiProviderPluginTest {
    @Test
    fun `ServiceLoader discovers OpenAI plugin`() {
        val plugins = ServiceLoader.load(DocPilotPlugin::class.java).toList()
        assertTrue(plugins.any { it.descriptor.id.value == "docpilot.ai.openai" })
    }

    @Test
    fun `discovery does not require API key`() {
        val plugin = OpenAiProviderPlugin(environment = emptyMap())
        assertEquals("docpilot.ai.openai", plugin.descriptor.id.value)
    }

    @Test
    fun `missing API key becomes plugin failure`() {
        val plugin = OpenAiProviderPlugin(environment = emptyMap())
        val result = plugin.execute(PluginContext(options = mapOf("prompt" to "Hello")))
        assertEquals(PluginStatus.FAILED, result.status)
        assertTrue(result.messages.single().text.contains("OPENAI_API_KEY"))
    }

    @Test
    fun `bridges plugin execution`() {
        val plugin = OpenAiProviderPlugin(
            environment = mapOf("OPENAI_API_KEY" to "test-key"),
            providerFactory = { configuration ->
                object : AiProvider {
                    override val descriptor = OpenAiProvider(configuration).descriptor
                    override fun generate(request: AiRequest) = AiGenerationResult.Success(
                        AiResponse(
                            providerId = OpenAiProvider.PROVIDER_ID,
                            modelId = request.modelId,
                            content = "remote response",
                            finishReason = AiFinishReason.STOP,
                        ),
                    )
                }
            },
        )
        val result = plugin.execute(PluginContext(options = mapOf("prompt" to "Hello")))
        assertEquals(PluginStatus.SUCCESS, result.status)
        assertEquals("remote response", result.messages.single().text)
    }
}
