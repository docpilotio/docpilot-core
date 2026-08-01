package io.docpilot.provider.openai

import java.net.URI
import java.time.Duration

class OpenAiConfiguration(
    apiKey: String,
    val baseUri: URI = URI.create(DEFAULT_BASE_URL),
    defaultModel: String = DEFAULT_MODEL,
    val requestTimeout: Duration = DEFAULT_TIMEOUT,
    organizationId: String? = null,
    projectId: String? = null,
    val maxResponseBytes: Int = DEFAULT_MAX_RESPONSE_BYTES,
) {
    val apiKey: String = apiKey.trim()
    val defaultModel: String = defaultModel.trim()
    val organizationId: String? = organizationId?.trim()?.takeIf(String::isNotEmpty)
    val projectId: String? = projectId?.trim()?.takeIf(String::isNotEmpty)

    init {
        require(this.apiKey.isNotEmpty()) { "OpenAI API key must not be blank." }
        require(baseUri.scheme?.lowercase() in setOf("http", "https") && baseUri.host != null) {
            "OpenAI base URI must be an absolute HTTP or HTTPS URI with a host."
        }
        require(baseUri.userInfo == null && baseUri.query == null && baseUri.fragment == null) {
            "OpenAI base URI must not contain user info, a query, or a fragment."
        }
        require(this.defaultModel.isNotEmpty()) { "OpenAI default model must not be blank." }
        require(requestTimeout in MIN_TIMEOUT..MAX_TIMEOUT) {
            "OpenAI request timeout must be between ${MIN_TIMEOUT.seconds} and ${MAX_TIMEOUT.seconds} seconds."
        }
        require(maxResponseBytes in 1..MAX_RESPONSE_BYTES_LIMIT) {
            "OpenAI response size limit must be between 1 and $MAX_RESPONSE_BYTES_LIMIT bytes."
        }
    }

    override fun toString(): String =
        "OpenAiConfiguration(apiKey=<redacted>, baseUri=$baseUri, defaultModel=$defaultModel, " +
            "requestTimeout=$requestTimeout, organizationId=$organizationId, projectId=$projectId, " +
            "maxResponseBytes=$maxResponseBytes)"

    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com"
        const val DEFAULT_MODEL = "gpt-5.6-terra"
        val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(120)
        val MIN_TIMEOUT: Duration = Duration.ofSeconds(1)
        val MAX_TIMEOUT: Duration = Duration.ofMinutes(5)
        const val DEFAULT_MAX_RESPONSE_BYTES = 1_048_576
        const val MAX_RESPONSE_BYTES_LIMIT = 16_777_216

        fun fromEnvironment(environment: Map<String, String> = System.getenv()): OpenAiConfiguration {
            val timeout = environment["DOCPILOT_OPENAI_TIMEOUT_SECONDS"]?.let { raw ->
                raw.trim().toLongOrNull()
                    ?: throw IllegalArgumentException("DOCPILOT_OPENAI_TIMEOUT_SECONDS must be an integer.")
            }?.let(Duration::ofSeconds) ?: DEFAULT_TIMEOUT
            val baseUri = try {
                URI.create(environment["DOCPILOT_OPENAI_BASE_URL"]?.trim() ?: DEFAULT_BASE_URL)
            } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("DOCPILOT_OPENAI_BASE_URL must be a valid URI.")
            }
            return OpenAiConfiguration(
                apiKey = environment["OPENAI_API_KEY"]
                    ?: error("OPENAI_API_KEY environment variable is required."),
                baseUri = baseUri,
                defaultModel = environment["DOCPILOT_OPENAI_MODEL"] ?: DEFAULT_MODEL,
                requestTimeout = timeout,
                organizationId = environment["OPENAI_ORGANIZATION_ID"],
                projectId = environment["OPENAI_PROJECT_ID"],
            )
        }
    }
}
