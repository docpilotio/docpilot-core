package io.docpilot.core.plugin

import io.docpilot.core.api.DocPilotPlugin
import io.docpilot.core.api.PluginValidationResult
import io.docpilot.core.api.PluginValidator

/**
 * Applies the core plugin contract rules.
 */
class DefaultPluginValidator : PluginValidator {

    override fun validate(
        plugin: DocPilotPlugin,
    ): PluginValidationResult {
        val errors = buildList {
            val descriptor = plugin.descriptor

            if (descriptor.displayName.isBlank()) {
                add("Plugin display name must not be blank.")
            }

            if (descriptor.description.isBlank()) {
                add("Plugin description must not be blank.")
            }
        }

        return if (errors.isEmpty()) {
            PluginValidationResult.Valid
        } else {
            PluginValidationResult.Invalid(errors)
        }
    }
}
