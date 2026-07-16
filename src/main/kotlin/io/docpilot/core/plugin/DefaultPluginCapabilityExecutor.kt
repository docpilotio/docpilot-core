package io.docpilot.core.plugin

import io.docpilot.core.api.PluginCapabilityExecutor
import io.docpilot.core.api.PluginCapabilityResolver
import io.docpilot.core.api.PluginExecutor
import io.docpilot.core.api.PluginSelector
import io.docpilot.core.model.plugin.PluginCapabilityId
import io.docpilot.core.model.plugin.PluginContext
import io.docpilot.core.model.plugin.PluginId
import io.docpilot.core.model.plugin.PluginMessage
import io.docpilot.core.model.plugin.PluginMessageLevel
import io.docpilot.core.model.plugin.PluginResult
import io.docpilot.core.model.plugin.PluginStatus
import io.docpilot.core.model.selection.SelectionContext
import io.docpilot.core.model.selection.SelectionResult

/**
 * Default capability execution flow:
 *
 * capability -> resolver -> selector -> executor
 */
class DefaultPluginCapabilityExecutor(
    private val resolver: PluginCapabilityResolver,
    private val selector: PluginSelector,
    private val executor: PluginExecutor,
) : PluginCapabilityExecutor {

    override fun execute(
        capabilityId: PluginCapabilityId,
        pluginContext: PluginContext,
        selectionContext: SelectionContext<PluginId>,
    ): PluginResult {
        val resolution = resolver.resolve(
            capabilityId = capabilityId,
            context = pluginContext,
        )

        return when (
            val selection = selector.select(
                resolution = resolution,
                context = selectionContext,
            )
        ) {
            is SelectionResult.Selected ->
                executor.execute(
                    pluginId = selection.candidateId,
                    context = pluginContext,
                )

            is SelectionResult.Unavailable ->
                PluginResult(
                    status = PluginStatus.FAILED,
                    messages = selection.reasons.map { reason ->
                        PluginMessage(
                            level = PluginMessageLevel.ERROR,
                            text = reason,
                        )
                    },
                )
        }
    }

    companion object {

        fun create(
            registry: io.docpilot.core.api.PluginRegistry,
        ): DefaultPluginCapabilityExecutor =
            DefaultPluginCapabilityExecutor(
                resolver =
                    DefaultPluginCapabilityResolver(
                        registry = registry,
                    ),
                selector =
                    DefaultPluginSelector(),
                executor =
                    DefaultPluginExecutor(
                        registry = registry,
                    ),
            )
    }
}
