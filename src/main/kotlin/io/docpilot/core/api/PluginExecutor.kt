package io.docpilot.core.api

import io.docpilot.core.model.plugin.PluginContext
import io.docpilot.core.model.plugin.PluginId
import io.docpilot.core.model.plugin.PluginResult

/**
 * Executes one registered plugin by ID.
 */
fun interface PluginExecutor {
    fun execute(
        pluginId: PluginId,
        context: PluginContext,
    ): PluginResult
}
