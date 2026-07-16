package io.docpilot.core.api

import io.docpilot.core.model.ai.AiExecutionLocation
import io.docpilot.core.model.ai.AiProviderId

interface AiProviderRegistry {
    fun all(): List<AiProvider>

    fun find(
        providerId: AiProviderId,
    ): AiProvider?

    fun byExecutionLocation(
        location: AiExecutionLocation,
    ): List<AiProvider>
}
