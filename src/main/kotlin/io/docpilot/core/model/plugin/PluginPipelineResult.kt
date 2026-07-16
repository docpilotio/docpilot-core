package io.docpilot.core.model.plugin

import io.docpilot.core.model.RenderedArtifact

/**
 * Aggregated result of executing a plugin category.
 */
data class PluginPipelineResult(
    val category: PluginCategory,
    val executions: List<PluginExecutionRecord>,
) {
    val status: PluginStatus
        get() = when {
            executions.any {
                it.result.status == PluginStatus.FAILED
            } -> PluginStatus.FAILED

            executions.any {
                it.result.status == PluginStatus.PARTIAL
            } -> PluginStatus.PARTIAL

            else -> PluginStatus.SUCCESS
        }

    val artifacts: List<RenderedArtifact>
        get() = executions.flatMap { it.result.artifacts }

    val messages: List<PluginMessage>
        get() = executions.flatMap { it.result.messages }

    init {
        require(
            executions.map { it.pluginId }
                .distinct()
                .size == executions.size,
        ) {
            "Plugin pipeline execution IDs must be unique."
        }

        require(
            artifacts.map { it.relativePath }
                .distinct()
                .size == artifacts.size,
        ) {
            "Plugin pipeline artifact paths must be unique."
        }
    }
}

data class PluginExecutionRecord(
    val pluginId: PluginId,
    val result: PluginResult,
)
