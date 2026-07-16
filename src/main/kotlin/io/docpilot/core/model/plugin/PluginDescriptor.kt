package io.docpilot.core.model.plugin

@JvmInline
value class PluginId(
    val value: String,
) {
    init {
        require(value.matches(ID_PATTERN)) {
            "Plugin ID must use lowercase letters, numbers, dots, or hyphens."
        }
    }

    override fun toString(): String = value

    private companion object {
        val ID_PATTERN =
            Regex("[a-z0-9]+(?:[.-][a-z0-9]+)*")
    }
}

enum class PluginCategory {
    ANALYSIS,
    OUTPUT,
}

data class PluginDescriptor(
    val id: PluginId,
    val displayName: String,
    val category: PluginCategory,
    val version: String,
    val description: String,
    val capabilities: Set<PluginCapability> = emptySet(),
) {
    init {
        require(displayName.isNotBlank()) {
            "Plugin displayName must not be blank."
        }
        require(version.matches(VERSION_PATTERN)) {
            "Plugin version must use semantic version format."
        }
        require(description.isNotBlank()) {
            "Plugin description must not be blank."
        }
        require(
            capabilities.map { it.id }
                .distinct()
                .size == capabilities.size,
        ) {
            "Plugin capability IDs must be unique."
        }
    }

    private companion object {
        val VERSION_PATTERN =
            Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][0-9A-Za-z.-]+)?")
    }
}
