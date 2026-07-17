package io.docpilot.provider.ollama

import java.net.URI
import java.time.Duration

data class OllamaConfiguration(
    val baseUri: URI = URI.create(DEFAULT_BASE_URL),
    val defaultModel: String = DEFAULT_MODEL,
    val requestTimeout: Duration = Duration.ofMinutes(5),
) {
    init {
        require(baseUri.scheme == "http" || baseUri.scheme == "https") {
            "Ollama base URI must use HTTP or HTTPS."
        }
        require(defaultModel.isNotBlank()) {
            "Ollama default model must not be blank."
        }
        require(!requestTimeout.isZero && !requestTimeout.isNegative) {
            "Ollama request timeout must be positive."
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "http://localhost:11434"
        const val DEFAULT_MODEL = "qwen3:8b"

        fun fromEnvironment(
            environment: Map<String, String> = System.getenv(),
        ): OllamaConfiguration =
            OllamaConfiguration(
                baseUri = URI.create(
                    environment["DOCPILOT_OLLAMA_BASE_URL"]
                        ?: DEFAULT_BASE_URL,
                ),
                defaultModel =
                    environment["DOCPILOT_OLLAMA_MODEL"]
                        ?: DEFAULT_MODEL,
                requestTimeout = environment[
                    "DOCPILOT_OLLAMA_TIMEOUT_SECONDS"
                ]?.toLongOrNull()
                    ?.let(Duration::ofSeconds)
                    ?: Duration.ofMinutes(5),
            )
    }
}
