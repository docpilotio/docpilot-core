package io.docpilot.core.render

import io.docpilot.core.model.*

class ProjectSummaryMarkdownRenderer {
    fun render(summary: ProjectSummary): RenderedArtifact =
        RenderedArtifact(
            "docs/project-summary.md",
            "text/markdown",
            buildString {
                appendLine("# Project Summary")
                appendLine()
                appendLine("## Project")
                appendLine()
                appendLine("- Name: `${summary.projectName}`")
                appendLine("- Git repository: ${if (summary.gitRepository) "Yes" else "No"}")
                appendLine()
                appendLine("## Detected Languages")
                appendLine()
                if (summary.languages.isEmpty()) appendLine("- None detected")
                else summary.languages.sortedBy { it.name }
                    .forEach { appendLine("- ${it.name.lowercase().replaceFirstChar(Char::uppercase)}") }
                appendLine()
                appendLine("## Detected Build Systems")
                appendLine()
                if (summary.buildSystems.isEmpty()) appendLine("- None detected")
                else summary.buildSystems.sortedBy { it.name }
                    .forEach { appendLine("- ${it.name.lowercase().replaceFirstChar(Char::uppercase)}") }
                appendLine()
                appendLine("## Candidate Modules")
                appendLine()
                if (summary.candidateModulePaths.isEmpty()) appendLine("- None detected")
                else summary.candidateModulePaths.forEach { appendLine("- `$it`") }
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
            },
        )
}
