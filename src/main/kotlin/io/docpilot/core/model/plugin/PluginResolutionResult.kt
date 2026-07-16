package io.docpilot.core.model.plugin

import io.docpilot.core.api.DocPilotPlugin

/**
 * Result of capability-based plugin resolution.
 */
sealed interface PluginResolutionResult {

    data class Resolved(
        val plugins: List<DocPilotPlugin>,
    ) : PluginResolutionResult {
        init {
            require(plugins.isNotEmpty()) {
                "Resolved plugin list must not be empty."
            }
            require(
                plugins.map { it.descriptor.id }
                    .distinct()
                    .size == plugins.size,
            ) {
                "Resolved plugin IDs must be unique."
            }
        }
    }

    data class Unavailable(
        val capabilityId: PluginCapabilityId,
        val reasons: List<String>,
    ) : PluginResolutionResult {
        init {
            require(reasons.isNotEmpty()) {
                "Unavailable resolution must contain at least one reason."
            }
            require(reasons.none(String::isBlank)) {
                "Resolution reasons must not contain blank messages."
            }
        }
    }
}
