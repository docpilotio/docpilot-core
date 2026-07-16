package io.docpilot.core.ai

import io.docpilot.core.api.AiProvider
import io.docpilot.core.model.ai.AiCapability
import io.docpilot.core.model.ai.AiErrorCode
import io.docpilot.core.model.ai.AiExecutionLocation
import io.docpilot.core.model.ai.AiFinishReason
import io.docpilot.core.model.ai.AiGenerationResult
import io.docpilot.core.model.ai.AiMessage
import io.docpilot.core.model.ai.AiMessageRole
import io.docpilot.core.model.ai.AiModelId
import io.docpilot.core.model.ai.AiProviderDescriptor
import io.docpilot.core.model.ai.AiProviderId
import io.docpilot.core.model.ai.AiRequest
import io.docpilot.core.model.ai.AiResponse
import io.docpilot.core.model.selection.SelectionContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DefaultAiRuntimeTest {
    private val request = AiRequest(
        modelId = AiModelId("shared-model"),
        messages = listOf(
            AiMessage(
                role = AiMessageRole.USER,
                content = "Generate a specification.",
            ),
        ),
    )

    @Test
    fun `explicit provider selection overrides priority`() {
        val ollama = provider("ollama", "local")
        val openAi = provider("openai", "cloud")
        val runtime = DefaultAiRuntime(
            registry = InMemoryAiProviderRegistry(
                listOf(ollama, openAi),
            ),
        )

        val result = runtime.generate(
            request = request,
            selectionContext = SelectionContext(
                explicitCandidateId = AiProviderId("openai"),
                priorities = mapOf(
                    AiProviderId("ollama") to 100,
                    AiProviderId("openai") to 90,
                ),
            ),
        )

        val success = assertIs<AiGenerationResult.Success>(result)
        assertEquals("cloud", success.response.content)
        assertEquals(
            AiProviderId("openai"),
            success.response.providerId,
        )
    }

    @Test
    fun `priority supports local first selection`() {
        val runtime = DefaultAiRuntime(
            registry = InMemoryAiProviderRegistry(
                listOf(
                    provider("openai", "cloud"),
                    provider("ollama", "local"),
                ),
            ),
        )

        val result = runtime.generate(
            request = request,
            selectionContext = SelectionContext(
                priorities = mapOf(
                    AiProviderId("ollama") to 100,
                    AiProviderId("openai") to 90,
                ),
            ),
        )

        val success = assertIs<AiGenerationResult.Success>(result)
        assertEquals("local", success.response.content)
    }

    @Test
    fun `missing explicit provider does not fallback`() {
        val runtime = DefaultAiRuntime(
            registry = InMemoryAiProviderRegistry(
                listOf(provider("ollama", "local")),
            ),
        )

        val result = runtime.generate(
            request = request,
            selectionContext = SelectionContext(
                explicitCandidateId = AiProviderId("openai"),
            ),
        )

        val failure = assertIs<AiGenerationResult.Failure>(result)
        assertEquals(
            AiErrorCode.PROVIDER_NOT_FOUND,
            failure.error.code,
        )
    }

    @Test
    fun `provider exception becomes structured failure`() {
        val throwing = object : AiProvider {
            override val descriptor = descriptor("ollama")

            override fun generate(
                request: AiRequest,
            ): AiGenerationResult =
                error("Connection refused")
        }
        val runtime = DefaultAiRuntime(
            InMemoryAiProviderRegistry(listOf(throwing)),
        )

        val result = runtime.generate(request)

        val failure = assertIs<AiGenerationResult.Failure>(result)
        assertEquals(
            AiErrorCode.PROVIDER_FAILURE,
            failure.error.code,
        )
        assertEquals(
            AiProviderId("ollama"),
            failure.error.providerId,
        )
    }

    private fun provider(
        id: String,
        content: String,
    ): AiProvider =
        object : AiProvider {
            override val descriptor = descriptor(id)

            override fun generate(
                request: AiRequest,
            ): AiGenerationResult =
                AiGenerationResult.Success(
                    AiResponse(
                        providerId = descriptor.id,
                        modelId = request.modelId,
                        content = content,
                        finishReason = AiFinishReason.STOP,
                    ),
                )
        }

    private fun descriptor(id: String) =
        AiProviderDescriptor(
            id = AiProviderId(id),
            displayName = id,
            version = "0.1.0",
            executionLocation =
                if (id == "ollama") {
                    AiExecutionLocation.LOCAL
                } else {
                    AiExecutionLocation.REMOTE
                },
            capabilities = setOf(AiCapability.TEXT_GENERATION),
            supportedModels = setOf(AiModelId("shared-model")),
        )
}
