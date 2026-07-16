package io.docpilot.core.api

import io.docpilot.core.model.plugin.PluginCategory
import io.docpilot.core.model.plugin.PluginContext
import io.docpilot.core.model.plugin.PluginPipelineResult

/**
 * Executes all registered plugins of one category in deterministic order.
 */
fun interface PluginPipeline {
    fun execute(
        category: PluginCategory,
        context: PluginContext,
    ): PluginPipelineResult
}
