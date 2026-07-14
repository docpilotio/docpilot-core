package io.docpilot.core.model

import java.nio.file.Path

data class ProjectRoot(
    val path: Path,
    val name: String,
    val gitRepository: Boolean,
) {
    init {
        require(path.isAbsolute)
        require(name.isNotBlank())
    }
}
