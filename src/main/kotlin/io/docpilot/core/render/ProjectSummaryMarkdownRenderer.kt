package io.docpilot.core.render

import io.docpilot.core.model.ProjectBuildSystem
import io.docpilot.core.model.ProjectFileType
import io.docpilot.core.model.ProjectLanguage
import io.docpilot.core.model.ProjectSummary
import io.docpilot.core.model.RenderedArtifact

/**
 * Renders a [ProjectSummary] as a Markdown artifact.
 */
public class ProjectSummaryMarkdownRenderer {

    public fun render(summary: ProjectSummary): RenderedArtifact =
        RenderedArtifact(
            relativePath = "docs/project-summary.md",
            mediaType = "text/markdown",
            content = buildMarkdown(summary),
        )

    private fun buildMarkdown(summary: ProjectSummary): String =
        buildString {
            appendLine("# Project Summary")
            appendLine()
            appendLine("## Project")
            appendLine()
            appendLine("- Name: `${summary.projectName}`")
            appendLine("- Git repository: ${yesNo(summary.gitRepository)}")
            appendLine()

            appendLine("## Detected Languages")
            appendLine()
            appendList(
                values = summary.languages
                    .sortedBy(ProjectLanguage::name)
                    .map { it.name.lowercase().replaceFirstChar(Char::uppercase) },
            )
            appendLine()

            appendLine("## Detected Build Systems")
            appendLine()
            appendList(
                values = summary.buildSystems
                    .sortedBy(ProjectBuildSystem::name)
                    .map { it.name.lowercase().replaceFirstChar(Char::uppercase) },
            )
            appendLine()

            appendLine("## Candidate Modules")
            appendLine()
            appendList(summary.candidateModulePaths.map { "`$it`" })
            appendLine()

            appendLine("## Inventory")
            appendLine()
            appendLine("- Directories: ${summary.totalDirectories}")
            appendLine("- Files: ${summary.totalFiles}")
            appendLine("- Kotlin source files: ${summary.count(ProjectFileType.KOTLIN_SOURCE)}")
            appendLine("- Java source files: ${summary.count(ProjectFileType.JAVA_SOURCE)}")
            appendLine("- Gradle build files: ${summary.count(ProjectFileType.GRADLE_BUILD)}")
            appendLine("- Gradle settings files: ${summary.count(ProjectFileType.GRADLE_SETTINGS)}")
            appendLine("- Android manifests: ${summary.count(ProjectFileType.ANDROID_MANIFEST)}")
            appendLine("- XML files: ${summary.count(ProjectFileType.XML_RESOURCE)}")
            appendLine("- Markdown files: ${summary.count(ProjectFileType.MARKDOWN)}")
        }

    private fun StringBuilder.appendList(values: List<String>) {
        if (values.isEmpty()) {
            appendLine("- None detected")
            return
        }

        values.forEach { value ->
            appendLine("- $value")
        }
    }

    private fun yesNo(value: Boolean): String =
        if (value) "Yes" else "No"
}
