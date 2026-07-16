package io.docpilot.core.plugin

import io.docpilot.core.api.PluginCapabilityExecutor
import io.docpilot.core.api.PluginLoader
import io.docpilot.core.api.PluginPipeline
import io.docpilot.core.api.PluginRegistry

/**
 * Ready-to-use runtime composition for discovered plugins.
 */
data class DefaultPluginRuntime(
    val registry: PluginRegistry,
    val pipeline: PluginPipeline,
    val capabilityExecutor: PluginCapabilityExecutor =
        DefaultPluginCapabilityExecutor.create(registry),
) {
    companion object {

        fun discover(
            loader: PluginLoader =
                ServiceLoaderPluginLoader(),
        ): DefaultPluginRuntime {
            val registry = InMemoryPluginRegistry(
                plugins = loader.load(),
            )

            return DefaultPluginRuntime(
                registry = registry,
                pipeline = DefaultPluginPipeline(
                    registry = registry,
                ),
                capabilityExecutor =
                    DefaultPluginCapabilityExecutor.create(
                        registry = registry,
                    ),
            )
        }
    }
}
