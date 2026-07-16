package io.docpilot.core.api

import io.docpilot.core.model.plugin.PluginCapabilityId
import io.docpilot.core.model.plugin.PluginCategory
import io.docpilot.core.model.plugin.PluginId

/**
 * Read-only registry of plugins available to DocPilot.
 */
interface PluginRegistry {

    fun all(): List<DocPilotPlugin>

    fun find(id: PluginId): DocPilotPlugin?

    fun byCategory(
        category: PluginCategory,
    ): List<DocPilotPlugin>

    fun byCapability(
        capabilityId: PluginCapabilityId,
    ): List<DocPilotPlugin>
}
