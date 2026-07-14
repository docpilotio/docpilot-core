package io.docpilot.core.loader

import io.docpilot.core.api.ProjectLoader
import io.docpilot.core.model.ProjectRoot
import java.nio.file.Files
import java.nio.file.Path

/**
 * Loads a project from an existing local directory.
 */
public class LocalProjectLoader : ProjectLoader {

    override fun load(path: Path): ProjectRoot {
        val normalizedPath = path.toAbsolutePath().normalize()

        if (!Files.exists(normalizedPath)) {
            throw ProjectLoadException(
                path = normalizedPath,
                reason = ProjectLoadFailure.PATH_NOT_FOUND,
            )
        }

        if (!Files.isDirectory(normalizedPath)) {
            throw ProjectLoadException(
                path = normalizedPath,
                reason = ProjectLoadFailure.NOT_A_DIRECTORY,
            )
        }

        val projectName = normalizedPath.fileName
            ?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: throw ProjectLoadException(
                path = normalizedPath,
                reason = ProjectLoadFailure.NAME_UNAVAILABLE,
            )

        return ProjectRoot(
            path = normalizedPath,
            name = projectName,
            gitRepository = findGitDirectory(normalizedPath) != null,
        )
    }

    private fun findGitDirectory(start: Path): Path? {
        var current: Path? = start

        while (current != null) {
            if (Files.exists(current.resolve(".git"))) {
                return current
            }
            current = current.parent
        }

        return null
    }
}

public class ProjectLoadException(
    public val path: Path,
    public val reason: ProjectLoadFailure,
) : IllegalArgumentException(
    "Unable to load project at '$path': ${reason.description}",
)

public enum class ProjectLoadFailure(
    public val description: String,
) {
    PATH_NOT_FOUND("path does not exist"),
    NOT_A_DIRECTORY("path is not a directory"),
    NAME_UNAVAILABLE("project name could not be determined"),
}
