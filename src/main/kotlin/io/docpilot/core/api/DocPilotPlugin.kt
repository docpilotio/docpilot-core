package io.docpilot.core.api

/**
 * Base contract for pluggable DocPilot capabilities.
 */
public interface DocPilotPlugin {
    public val id: String
    public val displayName: String
    public val version: String

    /**
     * Performs lightweight validation before the plugin is used.
     */
    public fun validate(): PluginValidationResult = PluginValidationResult.Valid
}

public sealed interface PluginValidationResult {
    public data object Valid : PluginValidationResult

    public data class Invalid(
        public val reasons: List<String>,
    ) : PluginValidationResult {
        init {
            require(reasons.isNotEmpty()) {
                "Invalid plugin validation must include at least one reason."
            }
        }
    }
}
