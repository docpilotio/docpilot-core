package io.docpilot.core.plugin

import io.docpilot.core.api.DocPilotPlugin
import io.docpilot.core.api.PluginLoader
import java.util.ServiceLoader

/**
 * Discovers classpath plugins through Java's ServiceLoader mechanism.
 *
 * Plugin JARs must provide:
 *
 * META-INF/services/io.docpilot.core.api.DocPilotPlugin
 */
class ServiceLoaderPluginLoader(
    private val classLoader: ClassLoader =
        Thread.currentThread().contextClassLoader
            ?: ServiceLoaderPluginLoader::class.java.classLoader,
) : PluginLoader {

    override fun load(): List<DocPilotPlugin> =
        ServiceLoader
            .load(
                DocPilotPlugin::class.java,
                classLoader,
            )
            .toList()
            .sortedBy {
                it.descriptor.id.value
            }
}
