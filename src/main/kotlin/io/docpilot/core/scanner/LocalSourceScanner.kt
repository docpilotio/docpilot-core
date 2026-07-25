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

class LocalSourceScanner(
    private val excluded: Set<String> = setOf(
        ".git", ".gradle", ".idea", "build", "out", "node_modules"
    ),
) : SourceScanner {

    override fun scan(project: ProjectRoot): ProjectInventory {
        val dirs = mutableListOf<String>()
        val files = mutableListOf<ProjectFile>()

        Files.walk(project.path).use { stream ->
            stream
                .filter { it != project.path }
                .filter { candidate ->
                    project.path.relativize(candidate)
                        .none { it.toString() in excluded }
                }
                .forEach { candidate ->
                    val relative = project.path.relativize(candidate)
                        .joinToString("/") { it.toString() }
                    if (isManagedOutput(relative)) return@forEach

                    when {
                        Files.isDirectory(candidate) && !isManagedContainer(relative) -> dirs += relative
                        Files.isRegularFile(candidate) ->
                            files += ProjectFile(relative, classify(candidate))
                    }
                }
        }

        return ProjectInventory(
            project,
            dirs.sorted(),
            files.sortedBy { it.relativePath },
        )
    }

    private fun isManagedOutput(relativePath: String): Boolean =
        relativePath == ".docpilot" || relativePath.startsWith(".docpilot/") ||
            relativePath == "prompt-package" || relativePath.startsWith("prompt-package/") ||
            relativePath == "docs/project-summary.md" ||
            relativePath == "docs/source-index.md" ||
            relativePath == "docs/knowledge-graph.json" ||
            relativePath == "docs/project-specification.md" ||
            relativePath == "docs/architecture/overview.md" ||
            relativePath == "docs/specification" ||
            relativePath.startsWith("docs/specification/")

    private fun isManagedContainer(relativePath: String): Boolean =
        relativePath == "docs" || relativePath == "docs/specification" ||
            relativePath == "docs/architecture"

    private fun classify(path: Path): ProjectFileType =
        when {
            path.name == "AndroidManifest.xml" -> ProjectFileType.ANDROID_MANIFEST
            path.name == "settings.gradle" ||
                path.name == "settings.gradle.kts" -> ProjectFileType.GRADLE_SETTINGS
            path.name == "build.gradle" ||
                path.name == "build.gradle.kts" -> ProjectFileType.GRADLE_BUILD
            path.extension.equals("kt", true) -> ProjectFileType.KOTLIN_SOURCE
            path.extension.equals("java", true) -> ProjectFileType.JAVA_SOURCE
            path.extension.equals("xml", true) -> ProjectFileType.XML_RESOURCE
            path.extension.equals("md", true) ||
                path.extension.equals("markdown", true) -> ProjectFileType.MARKDOWN
            else -> ProjectFileType.OTHER
        }
}
