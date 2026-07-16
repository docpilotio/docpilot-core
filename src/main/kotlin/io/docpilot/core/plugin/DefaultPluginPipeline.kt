package io.docpilot.core.plugin

import io.docpilot.core.api.PluginExecutor
import io.docpilot.core.api.PluginPipeline
import io.docpilot.core.api.PluginRegistry
import io.docpilot.core.model.plugin.PluginCategory
import io.docpilot.core.model.plugin.PluginContext
import io.docpilot.core.model.plugin.PluginExecutionRecord
import io.docpilot.core.model.plugin.PluginPipelineResult

/**
 * Runs all plugins in a category using registry order.
 */
class DefaultPluginPipeline(
    private val registry: PluginRegistry,
    private val executor: PluginExecutor =
        DefaultPluginExecutor(registry),
) : PluginPipeline {

    override fun execute(
        category: PluginCategory,
        context: PluginContext,
    ): PluginPipelineResult {
        val executions = registry
            .byCategory(category)
            .map { plugin ->
                PluginExecutionRecord(
                    pluginId = plugin.descriptor.id,
                    result = executor.execute(
                        pluginId = plugin.descriptor.id,
                        context = context,
                    ),
                )
            }

        return PluginPipelineResult(
            category = category,
            executions = executions,
        )
    }
}
