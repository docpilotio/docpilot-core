package io.docpilot.core.cli

import io.docpilot.core.plugin.DefaultPluginRuntime
import java.io.PrintStream

/**
 * Lists plugins discovered in the current runtime.
 */
internal fun runPluginsCommand(
    out: PrintStream,
    runtime: DefaultPluginRuntime =
        DefaultPluginRuntime.discover(),
): Int {
    val plugins = runtime.registry.all()

    if (plugins.isEmpty()) {
        out.println("No plugins discovered.")
        return 0
    }

    plugins.forEach { plugin ->
        val descriptor = plugin.descriptor

        out.println(
            buildString {
                append(descriptor.id.value)
                append(" | ")
                append(descriptor.category.name)
                append(" | ")
                append(descriptor.version)
                append(" | ")
                append(descriptor.displayName)
            },
        )
    }

    return 0
}
