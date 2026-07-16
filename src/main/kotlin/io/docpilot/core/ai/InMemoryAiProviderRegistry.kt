package io.docpilot.core.ai

import io.docpilot.core.api.AiProvider
import io.docpilot.core.api.AiProviderRegistry
import io.docpilot.core.model.ai.AiExecutionLocation
import io.docpilot.core.model.ai.AiProviderId

class InMemoryAiProviderRegistry(
    providers: List<AiProvider>,
) : AiProviderRegistry {
    private val providersById: Map<AiProviderId, AiProvider>

    init {
        val duplicateIds = providers
            .groupBy { it.descriptor.id }
            .filterValues { it.size > 1 }
            .keys

        require(duplicateIds.isEmpty()) {
            "Duplicate AI provider IDs: ${
                duplicateIds.sorted().joinToString()
            }"
        }

        providersById = providers
            .sortedBy { it.descriptor.id }
            .associateBy { it.descriptor.id }
    }

    override fun all(): List<AiProvider> =
        providersById.values.toList()

    override fun find(
        providerId: AiProviderId,
    ): AiProvider? =
        providersById[providerId]

    override fun byExecutionLocation(
        location: AiExecutionLocation,
    ): List<AiProvider> =
        providersById.values.filter {
            it.descriptor.executionLocation == location
        }
}
