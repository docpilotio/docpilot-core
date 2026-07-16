package io.docpilot.core.model.plugin

import io.docpilot.core.model.RenderedArtifact
import io.docpilot.core.model.knowledge.KnowledgeBuildResult
import io.docpilot.core.model.prompt.PromptPackage
import io.docpilot.core.model.source.SourceIndex

/**
 * Immutable input exposed to a plugin.
 *
 * Fields are optional because analysis and output plugins require
 * different stages of the DocPilot pipeline.
 */
data class PluginContext(
    val sourceIndex: SourceIndex? = null,
    val knowledge: KnowledgeBuildResult? = null,
    val promptPackage: PromptPackage? = null,
    val options: Map<String, String> = emptyMap(),
) {
    init {
        require(options.keys.none(String::isBlank)) {
            "Plugin option keys must not be blank."
        }
    }
}

data class PluginResult(
    val status: PluginStatus,
    val artifacts: List<RenderedArtifact> = emptyList(),
    val messages: List<PluginMessage> = emptyList(),
) {
    init {
        require(
            artifacts.map { it.relativePath }
                .distinct()
                .size == artifacts.size,
        ) {
            "Plugin artifact paths must be unique."
        }

        require(
            status != PluginStatus.FAILED ||
                messages.any { it.level == PluginMessageLevel.ERROR },
        ) {
            "A failed plugin result must contain an error message."
        }
    }

    val isSuccess: Boolean
        get() = status == PluginStatus.SUCCESS
}

enum class PluginStatus {
    SUCCESS,
    PARTIAL,
    FAILED,
}

data class PluginMessage(
    val level: PluginMessageLevel,
    val text: String,
) {
    init {
        require(text.isNotBlank()) {
            "Plugin message text must not be blank."
        }
    }
}

enum class PluginMessageLevel {
    INFO,
    WARNING,
    ERROR,
}
