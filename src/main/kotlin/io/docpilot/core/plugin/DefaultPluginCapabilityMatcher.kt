package io.docpilot.core.plugin

import io.docpilot.core.api.PluginCapabilityMatcher
import io.docpilot.core.model.plugin.PluginCapability
import io.docpilot.core.model.plugin.PluginContext
import io.docpilot.core.model.plugin.PluginInput

/**
 * Matches capability input requirements against available context data.
 */
class DefaultPluginCapabilityMatcher :
    PluginCapabilityMatcher {

    override fun matches(
        capability: PluginCapability,
        context: PluginContext,
    ): Boolean =
        capability.requiredInputs.all { input ->
            when (input) {
                PluginInput.SOURCE_INDEX ->
                    context.sourceIndex != null

                PluginInput.KNOWLEDGE ->
                    context.knowledge != null

                PluginInput.PROMPT_PACKAGE ->
                    context.promptPackage != null
            }
        }
}
