package io.docpilot.core.api

import io.docpilot.core.model.plugin.PluginCapabilityId
import io.docpilot.core.model.plugin.PluginContext
import io.docpilot.core.model.plugin.PluginId
import io.docpilot.core.model.plugin.PluginResult
import io.docpilot.core.model.selection.SelectionContext

/**
 * Resolves, selects, and executes one plugin for a capability.
 */
interface PluginCapabilityExecutor {

    fun execute(
        capabilityId: PluginCapabilityId,
        pluginContext: PluginContext,
        selectionContext: SelectionContext<PluginId> =
            SelectionContext(),
    ): PluginResult
}