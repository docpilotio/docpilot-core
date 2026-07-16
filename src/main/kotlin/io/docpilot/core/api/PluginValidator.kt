package io.docpilot.core.api

/**
 * Validates a plugin before it enters a registry.
 */
fun interface PluginValidator {
    fun validate(
        plugin: DocPilotPlugin,
    ): PluginValidationResult
}
