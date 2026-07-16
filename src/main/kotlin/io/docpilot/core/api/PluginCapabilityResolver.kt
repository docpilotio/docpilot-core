package io.docpilot.core.api

import io.docpilot.core.model.plugin.PluginCapabilityId
import io.docpilot.core.model.plugin.PluginContext
import io.docpilot.core.model.plugin.PluginResolutionResult

/**
 * Resolves plugins capable of serving one capability with the supplied context.
 */
fun interface PluginCapabilityResolver {
    fun resolve(
        capabilityId: PluginCapabilityId,
        context: PluginContext,
    ): PluginResolutionResult
}
