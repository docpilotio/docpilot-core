package io.docpilot.core.api

import io.docpilot.core.model.plugin.PluginId
import io.docpilot.core.model.plugin.PluginResolutionResult
import io.docpilot.core.model.selection.SelectionContext
import io.docpilot.core.model.selection.SelectionResult

interface PluginSelector {
    fun select(
        resolution: PluginResolutionResult,
        context: SelectionContext<PluginId> = SelectionContext(),
    ): SelectionResult<DocPilotPlugin, PluginId>
}
