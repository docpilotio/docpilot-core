package io.docpilot.core.model.ai

data class AiProviderDescriptor(
    val id: AiProviderId,
    val displayName: String,
    val version: String,
    val executionLocation: AiExecutionLocation,
    val capabilities: Set<AiCapability> = emptySet(),
    val supportedModels: Set<AiModelId> = emptySet(),
) {
    init {
        require(displayName.isNotBlank()) {
            "AI provider display name must not be blank."
        }
        require(version.matches(VERSION_PATTERN)) {
            "AI provider version must use semantic version format."
        }
    }

    fun supports(modelId: AiModelId): Boolean =
        supportedModels.isEmpty() || modelId in supportedModels

    private companion object {
        val VERSION_PATTERN =
            Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][0-9A-Za-z.-]+)?")
    }
}

enum class AiExecutionLocation {
    LOCAL,
    REMOTE,
}

enum class AiCapability {
    TEXT_GENERATION,
    STRUCTURED_OUTPUT,
    STREAMING,
    TOOL_CALLING,
}
