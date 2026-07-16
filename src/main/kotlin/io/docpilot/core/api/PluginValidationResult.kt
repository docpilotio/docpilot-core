package io.docpilot.core.api

/**
 * Result of validating whether a plugin can participate in DocPilot.
 */
sealed interface PluginValidationResult {

    /**
     * The plugin satisfies all validation rules.
     */
    data object Valid : PluginValidationResult

    /**
     * The plugin failed one or more validation rules.
     */
    data class Invalid(
        val errors: List<String>,
    ) : PluginValidationResult {
        init {
            require(errors.isNotEmpty()) {
                "Plugin validation errors must not be empty."
            }
            require(errors.none(String::isBlank)) {
                "Plugin validation errors must not contain blank messages."
            }
        }
    }
}
