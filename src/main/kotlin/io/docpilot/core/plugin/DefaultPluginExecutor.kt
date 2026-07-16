package io.docpilot.core.plugin

import io.docpilot.core.api.PluginExecutor
import io.docpilot.core.api.PluginRegistry
import io.docpilot.core.model.plugin.PluginContext
import io.docpilot.core.model.plugin.PluginId
import io.docpilot.core.model.plugin.PluginMessage
import io.docpilot.core.model.plugin.PluginMessageLevel
import io.docpilot.core.model.plugin.PluginResult
import io.docpilot.core.model.plugin.PluginStatus

/**
 * Executes plugins from a registry and converts exceptions into
 * deterministic failed results.
 */
class DefaultPluginExecutor(
    private val registry: PluginRegistry,
) : PluginExecutor {

    override fun execute(
        pluginId: PluginId,
        context: PluginContext,
    ): PluginResult {
        val plugin = registry.find(pluginId)
            ?: return PluginResult(
                status = PluginStatus.FAILED,
                messages = listOf(
                    PluginMessage(
                        level = PluginMessageLevel.ERROR,
                        text = "Plugin not found: $pluginId",
                    ),
                ),
            )

        return try {
            plugin.execute(context)
        } catch (exception: Exception) {
            PluginResult(
                status = PluginStatus.FAILED,
                messages = listOf(
                    PluginMessage(
                        level = PluginMessageLevel.ERROR,
                        text = buildString {
                            append("Plugin execution failed: ")
                            append(pluginId)
                            append(". ")
                            append(
                                exception.message
                                    ?: exception::class.simpleName
                                    ?: "Unknown error",
                            )
                        },
                    ),
                ),
            )
        }
    }
}
