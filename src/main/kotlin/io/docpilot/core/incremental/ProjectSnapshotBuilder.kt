package io.docpilot.core.incremental

import java.nio.file.Path

/**
 * Creates a deterministic content snapshot for selected project files.
 */
fun interface ProjectSnapshotBuilder {

    fun build(
        projectRoot: Path,
        relativePaths: Collection<String>,
    ): ProjectSnapshot
}
