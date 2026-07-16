package io.docpilot.core.plugin

import io.docpilot.core.api.PluginCapabilityMatcher
import io.docpilot.core.api.PluginCapabilityResolver
import io.docpilot.core.api.PluginRegistry
import io.docpilot.core.model.plugin.PluginCapabilityId
import io.docpilot.core.model.plugin.PluginContext
import io.docpilot.core.model.plugin.PluginResolutionResult

/**
 * Resolves capability providers whose input requirements match the context.
 */
class DefaultPluginCapabilityResolver(
    private val registry: PluginRegistry,
    private val matcher: PluginCapabilityMatcher =
        DefaultPluginCapabilityMatcher(),
) : PluginCapabilityResolver {

    override fun resolve(
        capabilityId: PluginCapabilityId,
        context: PluginContext,
    ): PluginResolutionResult {
        val candidates = registry
            .byCapability(capabilityId)

        if (candidates.isEmpty()) {
            return PluginResolutionResult.Unavailable(
                capabilityId = capabilityId,
                reasons = listOf(
                    "No plugin provides capability: $capabilityId",
                ),
            )
        }

        val matching = candidates.filter { plugin ->
            val capability = plugin.descriptor.capabilities
                .first { it.id == capabilityId }

            matcher.matches(
                capability = capability,
                context = context,
            )
        }

        if (matching.isEmpty()) {
            return PluginResolutionResult.Unavailable(
                capabilityId = capabilityId,
                reasons = candidates.map { plugin ->
                    val capability = plugin.descriptor.capabilities
                        .first { it.id == capabilityId }

                    val requiredInputs = capability.requiredInputs
                        .map { it.name }
                        .sorted()
                        .joinToString()

                    buildString {
                        append(plugin.descriptor.id)
                        append(" requires inputs: ")
                        append(
                            requiredInputs.ifBlank { "none" },
                        )
                    }
                },
            )
        }

        return PluginResolutionResult.Resolved(
            plugins = matching.sortedBy {
                it.descriptor.id.value
            },
        )
    }
}
