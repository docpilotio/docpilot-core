package io.docpilot.core.plugin

import io.docpilot.core.api.DocPilotPlugin
import io.docpilot.core.api.PluginSelector
import io.docpilot.core.api.SelectionPolicy
import io.docpilot.core.model.plugin.PluginResolutionResult
import io.docpilot.core.model.selection.SelectionContext
import io.docpilot.core.model.selection.SelectionResult
import io.docpilot.core.selection.DefaultSelectionPolicy

/**
 * Applies the shared selection policy to resolved plugin candidates.
 */
class DefaultPluginSelector(
    private val policy: SelectionPolicy<DocPilotPlugin> =
        DefaultSelectionPolicy {
            it.descriptor.id.value
        },
) : PluginSelector {

    override fun select(
        resolution: PluginResolutionResult,
        context: SelectionContext,
    ): SelectionResult<DocPilotPlugin> =
        when (resolution) {
            is PluginResolutionResult.Resolved ->
                policy.select(
                    candidates = resolution.plugins,
                    context = context,
                )

            is PluginResolutionResult.Unavailable ->
                SelectionResult.Unavailable(
                    reasons = resolution.reasons,
                )
        }
}
