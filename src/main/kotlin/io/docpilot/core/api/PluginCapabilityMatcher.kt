package io.docpilot.core.api

import io.docpilot.core.model.plugin.PluginCapability
import io.docpilot.core.model.plugin.PluginContext

/**
 * Determines whether a capability can execute with the supplied context.
 */
fun interface PluginCapabilityMatcher {
    fun matches(
        capability: PluginCapability,
        context: PluginContext,
    ): Boolean
}
