package io.docpilot.cli.bootstrap

import io.docpilot.core.api.AiProvider
import io.docpilot.core.facade.DocPilot
import io.docpilot.core.facade.DocPilotFactory
import io.docpilot.provider.ollama.OllamaAiProvider
import io.docpilot.provider.openai.OpenAiConfiguration
import io.docpilot.provider.openai.OpenAiProvider
import io.docpilot.cli.logging.ProjectLogSession

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

            else -> throw IllegalArgumentException(
                "AI provider '$providerId' was not found. " +
                        "Available providers: ollama, openai",
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
