package io.docpilot.core.api

import io.docpilot.core.model.plugin.PluginResolutionResult
import io.docpilot.core.model.selection.SelectionContext
import io.docpilot.core.model.selection.SelectionResult

/**
 * Selects one plugin from a capability-resolution result.
 */
interface PluginSelector {

    fun select(
        resolution: PluginResolutionResult,
        context: SelectionContext = SelectionContext(),
    ): SelectionResult<DocPilotPlugin>
}