package io.docpilot.cli.bootstrap

import io.docpilot.core.api.AiProvider
import io.docpilot.core.facade.DocPilot
import io.docpilot.core.facade.DocPilotFactory
import io.docpilot.provider.ollama.OllamaAiProvider
import io.docpilot.provider.openai.OpenAiConfiguration
import io.docpilot.provider.openai.OpenAiProvider
import io.docpilot.cli.logging.ProjectLogSession
import io.docpilot.core.model.ai.*

class CliBootstrap(
    private val environment: Map<String, String> = System.getenv(),
) {

    fun create(providerId: String): DocPilot {
        val provider = createProvider(providerId)

        return DocPilotFactory.create(provider)
    }

    fun create(providerId: String, logSession: ProjectLogSession): DocPilot =
        DocPilotFactory.create(logSession.logging(createProvider(providerId)))

    fun createProvider(providerId: String): AiProvider =
        when (providerId.trim().lowercase()) {
            "ollama" -> createOllamaProvider()
            "openai" -> createOpenAiProvider()
            "fixture" -> FixtureAiProvider(false)
            "fixture-failure" -> FixtureAiProvider(true)

            else -> throw IllegalArgumentException(
                "AI provider '$providerId' was not found. " +
                        "Available providers: fixture, fixture-failure, ollama, openai",
            )
        }

    private fun createOllamaProvider(): AiProvider =
        OllamaAiProvider()

    private fun createOpenAiProvider(): AiProvider {
        val configuration = try {
            OpenAiConfiguration.fromEnvironment(environment)
        } catch (exception: IllegalStateException) {
            throw IllegalArgumentException(
                "OPENAI_API_KEY environment variable is required when --provider openai is selected.",
            )
        }
        return OpenAiProvider(configuration)
    }
}

private class FixtureAiProvider(private val fail: Boolean) : AiProvider {
    override val descriptor = AiProviderDescriptor(AiProviderId(if (fail) "fixture-failure" else "fixture"),
        "Deterministic fixture", "1.0.0", AiExecutionLocation.LOCAL, setOf(AiCapability.TEXT_GENERATION))
    override fun generate(request: AiRequest): AiGenerationResult = if (fail) AiGenerationResult.Failure(
        AiError(AiErrorCode.PROVIDER_FAILURE, "Controlled fixture failure; token=[REDACTED]", false, descriptor.id),
    ) else AiGenerationResult.Success(AiResponse(descriptor.id, request.modelId,
        "This bounded explanation adds context derived only from the canonical section while preserving its authoritative facts.",
        AiFinishReason.STOP))
}
