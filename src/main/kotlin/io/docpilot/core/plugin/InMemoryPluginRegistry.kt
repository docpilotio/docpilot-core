package io.docpilot.core.plugin

import io.docpilot.core.api.DocPilotPlugin
import io.docpilot.core.api.PluginRegistry
import io.docpilot.core.api.PluginValidationResult
import io.docpilot.core.api.PluginValidator
import io.docpilot.core.model.plugin.PluginCapabilityId
import io.docpilot.core.model.plugin.PluginCategory
import io.docpilot.core.model.plugin.PluginId

/**
 * Deterministic immutable in-memory plugin registry.
 */
class InMemoryPluginRegistry(
    plugins: List<DocPilotPlugin>,
    validator: PluginValidator =
        DefaultPluginValidator(),
) : PluginRegistry {

    private val pluginsById: Map<PluginId, DocPilotPlugin>

    init {
        val validationErrors = plugins.flatMap { plugin ->
            when (
                val result = validator.validate(plugin)
            ) {
                PluginValidationResult.Valid ->
                    emptyList()

                is PluginValidationResult.Invalid ->
                    result.errors.map { error ->
                        "${plugin.descriptor.id}: $error"
                    }
            }
        }

        require(validationErrors.isEmpty()) {
            validationErrors.joinToString(
                prefix = "Invalid plugins: ",
                separator = "; ",
            )
        }

        val duplicateIds = plugins
            .groupBy { it.descriptor.id }
            .filterValues { it.size > 1 }
            .keys

        require(duplicateIds.isEmpty()) {
            "Duplicate plugin IDs: ${
                duplicateIds
                    .map { it.value }
                    .sorted()
                    .joinToString()
            }"
        }

        pluginsById = plugins
            .sortedBy { it.descriptor.id.value }
            .associateBy { it.descriptor.id }
    }

    override fun all(): List<DocPilotPlugin> =
        pluginsById.values.toList()

    override fun find(
        id: PluginId,
    ): DocPilotPlugin? =
        pluginsById[id]

    override fun byCategory(
        category: PluginCategory,
    ): List<DocPilotPlugin> =
        pluginsById.values.filter {
            it.descriptor.category == category
        }

    override fun byCapability(
        capabilityId: PluginCapabilityId,
    ): List<DocPilotPlugin> =
        pluginsById.values.filter { plugin ->
            plugin.descriptor.capabilities.any {
                it.id == capabilityId
            }
        }
}
