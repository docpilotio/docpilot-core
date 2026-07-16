package io.docpilot.core.ai

import io.docpilot.core.api.AiProvider
import io.docpilot.core.model.ai.AiCapability
import io.docpilot.core.model.ai.AiExecutionLocation
import io.docpilot.core.model.ai.AiGenerationResult
import io.docpilot.core.model.ai.AiModelId
import io.docpilot.core.model.ai.AiProviderDescriptor
import io.docpilot.core.model.ai.AiProviderId
import io.docpilot.core.model.ai.AiRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InMemoryAiProviderRegistryTest {
    @Test
    fun `orders and filters providers deterministically`() {
        val openAi = provider(
            id = "openai",
            location = AiExecutionLocation.REMOTE,
        )
        val ollama = provider(
            id = "ollama",
            location = AiExecutionLocation.LOCAL,
        )

        val registry = InMemoryAiProviderRegistry(
            listOf(openAi, ollama),
        )

        assertEquals(
            listOf("ollama", "openai"),
            registry.all().map { it.descriptor.id.value },
        )
        assertEquals(
            listOf(ollama),
            registry.byExecutionLocation(
                AiExecutionLocation.LOCAL,
            ),
        )
    }

    @Test
    fun `rejects duplicate provider IDs`() {
        assertFailsWith<IllegalArgumentException> {
            InMemoryAiProviderRegistry(
                listOf(
                    provider("ollama", AiExecutionLocation.LOCAL),
                    provider("ollama", AiExecutionLocation.LOCAL),
                ),
            )
        }
    }

    private fun provider(
        id: String,
        location: AiExecutionLocation,
    ): AiProvider =
        object : AiProvider {
            override val descriptor = AiProviderDescriptor(
                id = AiProviderId(id),
                displayName = id,
                version = "0.1.0",
                executionLocation = location,
                capabilities = setOf(AiCapability.TEXT_GENERATION),
            )

            override fun generate(
                request: AiRequest,
            ): AiGenerationResult =
                error("Not needed by this test.")
        }
}
