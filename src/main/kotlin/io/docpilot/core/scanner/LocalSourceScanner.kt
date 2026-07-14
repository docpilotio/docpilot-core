package io.docpilot.core.scanner

import io.docpilot.core.api.SourceScanner
import io.docpilot.core.model.ProjectFile
import io.docpilot.core.model.ProjectFileType
import io.docpilot.core.model.ProjectInventory
import io.docpilot.core.model.ProjectRoot
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name

/**
 * Recursively scans a local project directory.
 */
public class LocalSourceScanner(
    excludedDirectoryNames: Set<String> = DEFAULT_EXCLUDED_DIRECTORIES,
) : SourceScanner {

    private val excludedDirectoryNames: Set<String> =
        excludedDirectoryNames.toSet()

    override fun scan(project: ProjectRoot): ProjectInventory {
        val directories = mutableListOf<String>()
        val files = mutableListOf<ProjectFile>()

        Files.walk(project.path).use { stream ->
            stream
                .filter { candidate -> candidate != project.path }
                .filter { candidate -> !isInsideExcludedDirectory(project.path, candidate) }
                .forEach { candidate ->
                    val relativePath = normalizeRelativePath(
                        project.path.relativize(candidate),
                    )

                    when {
                        Files.isDirectory(candidate) -> {
                            directories += relativePath
                        }

                        Files.isRegularFile(candidate) -> {
                            files += ProjectFile(
                                relativePath = relativePath,
                                type = classify(candidate),
                            )
                        }
                    }
                }
        }

        return ProjectInventory(
            project = project,
            directories = directories.sorted(),
            files = files.sortedBy(ProjectFile::relativePath),
        )
    }

    private fun isInsideExcludedDirectory(
        root: Path,
        candidate: Path,
    ): Boolean {
        val relative = root.relativize(candidate)

        return relative.any { segment ->
            segment.toString() in excludedDirectoryNames
        }
    }

    private fun classify(path: Path): ProjectFileType {
        val fileName = path.name

        return when {
            fileName == "AndroidManifest.xml" ->
                ProjectFileType.ANDROID_MANIFEST

            fileName == "settings.gradle" ||
                fileName == "settings.gradle.kts" ->
                ProjectFileType.GRADLE_SETTINGS

            fileName == "build.gradle" ||
                fileName == "build.gradle.kts" ->
                ProjectFileType.GRADLE_BUILD

            path.extension.equals("kt", ignoreCase = true) ->
                ProjectFileType.KOTLIN_SOURCE

            path.extension.equals("java", ignoreCase = true) ->
                ProjectFileType.JAVA_SOURCE

            path.extension.equals("xml", ignoreCase = true) ->
                ProjectFileType.XML_RESOURCE

            path.extension.equals("md", ignoreCase = true) ||
                path.extension.equals("markdown", ignoreCase = true) ->
                ProjectFileType.MARKDOWN

            else ->
                ProjectFileType.OTHER
        }
    }

    private fun normalizeRelativePath(path: Path): String =
        path.joinToString(separator = "/") { it.toString() }

    public companion object {
        public val DEFAULT_EXCLUDED_DIRECTORIES: Set<String> =
            setOf(
                ".git",
                ".gradle",
                ".idea",
                "build",
                "out",
                "node_modules",
            )
    }
}
