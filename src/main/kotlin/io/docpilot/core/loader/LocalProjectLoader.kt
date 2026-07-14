package io.docpilot.core.loader

import io.docpilot.core.api.ProjectLoader
import io.docpilot.core.model.ProjectRoot
import java.nio.file.Files
import java.nio.file.Path

class LocalProjectLoader : ProjectLoader {
    override fun load(path: Path): ProjectRoot {
        val normalized = path.toAbsolutePath().normalize()
        require(Files.exists(normalized)) { "Path does not exist: $normalized" }
        require(Files.isDirectory(normalized)) { "Path is not a directory: $normalized" }

        val name = normalized.fileName?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: error("Project name is unavailable")

        return ProjectRoot(
            path = normalized,
            name = name,
            gitRepository = generateSequence(normalized as Path?) { it.parent }
                .any { Files.exists(it.resolve(".git")) },
        )
    }
}
