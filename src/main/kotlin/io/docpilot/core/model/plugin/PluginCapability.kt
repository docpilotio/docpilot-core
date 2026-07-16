package io.docpilot.core.model.plugin

/**
 * Extensible capability identifier owned by a plugin or the DocPilot platform.
 */
@JvmInline
value class PluginCapabilityId(
    val value: String,
) {
    init {
        require(value.matches(ID_PATTERN)) {
            "Plugin capability ID must use lowercase letters, numbers, dots, or hyphens."
        }
    }

    override fun toString(): String = value

    private companion object {
        val ID_PATTERN =
            Regex("[a-z0-9]+(?:[.-][a-z0-9]+)*")
    }
}

/**
 * Pipeline inputs that may be required to execute a capability.
 */
enum class PluginInput {
    SOURCE_INDEX,
    KNOWLEDGE,
    PROMPT_PACKAGE,
}

/**
 * One capability exposed by a plugin.
 */
data class PluginCapability(
    val id: PluginCapabilityId,
    val requiredInputs: Set<PluginInput> = emptySet(),
)

/**
 * Well-known capability IDs supplied by DocPilot.
 *
 * External plugins may declare additional IDs without changing core.
 */
object PluginCapabilities {
    val ARCHITECTURE_SPEC =
        PluginCapabilityId(
            "docpilot.capability.architecture-spec",
        )

    val CLASS_DIAGRAM =
        PluginCapabilityId(
            "docpilot.capability.class-diagram",
        )

    val SEQUENCE_DIAGRAM =
        PluginCapabilityId(
            "docpilot.capability.sequence-diagram",
        )

    val KNOWLEDGE_EXPORT =
        PluginCapabilityId(
            "docpilot.capability.knowledge-export",
        )

    val MARKDOWN =
        PluginCapabilityId(
            "docpilot.capability.markdown",
        )

    val AI_GENERATION =
        PluginCapabilityId(
            "docpilot.capability.ai-generation",
        )
}
