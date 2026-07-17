package io.docpilot.provider.openai

import java.net.URI
import java.time.Duration

data class OpenAiConfiguration(
    val apiKey: String,
    val baseUri: URI = URI.create(DEFAULT_BASE_URL),
    val defaultModel: String = DEFAULT_MODEL,
    val requestTimeout: Duration = Duration.ofSeconds(120),
    val organizationId: String? = null,
    val projectId: String? = null,
) {
    init {
        require(apiKey.isNotBlank()) {
            "OpenAI API key must not be blank."
        }
        require(baseUri.scheme == "http" || baseUri.scheme == "https") {
            "OpenAI base URI must use HTTP or HTTPS."
        }
        require(defaultModel.isNotBlank()) {
            "OpenAI default model must not be blank."
        }
        require(!requestTimeout.isZero && !requestTimeout.isNegative) {
            "OpenAI request timeout must be positive."
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com"
        const val DEFAULT_MODEL = "gpt-5.6-terra"

        fun fromEnvironment(
            environment: Map<String, String> = System.getenv(),
        ): OpenAiConfiguration =
            OpenAiConfiguration(
                apiKey = environment["OPENAI_API_KEY"]
                    ?: error("OPENAI_API_KEY environment variable is required."),
                baseUri = URI.create(
                    environment["DOCPILOT_OPENAI_BASE_URL"]
                        ?: DEFAULT_BASE_URL,
                ),
                defaultModel = environment["DOCPILOT_OPENAI_MODEL"]
                    ?: DEFAULT_MODEL,
                requestTimeout = environment["DOCPILOT_OPENAI_TIMEOUT_SECONDS"]
                    ?.toLongOrNull()
                    ?.let(Duration::ofSeconds)
                    ?: Duration.ofSeconds(120),
                organizationId = environment["OPENAI_ORGANIZATION_ID"],
                projectId = environment["OPENAI_PROJECT_ID"],
            )
    }
}
