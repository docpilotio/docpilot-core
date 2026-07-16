package io.docpilot.core.api

import io.docpilot.core.model.plugin.PluginContext
import io.docpilot.core.model.plugin.PluginDescriptor
import io.docpilot.core.model.plugin.PluginResult

/**
 * Stable extension contract implemented by DocPilot plugins.
 */
interface DocPilotPlugin {

    val descriptor: PluginDescriptor

    fun execute(
        context: PluginContext,
    ): PluginResult
}
