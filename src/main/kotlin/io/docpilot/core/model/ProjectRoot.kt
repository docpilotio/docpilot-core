package io.docpilot.core.model

import java.nio.file.Path

/**
 * Validated root directory of a software project.
 */
public data class ProjectRoot(
    public val path: Path,
    public val name: String,
    public val gitRepository: Boolean,
) {
    init {
        require(path.isAbsolute) {
            "ProjectRoot path must be absolute."
        }
        require(name.isNotBlank()) {
            "ProjectRoot name must not be blank."
        }
    }
}