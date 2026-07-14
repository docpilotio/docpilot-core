package io.docpilot.core.render

import io.docpilot.core.model.ProjectBuildSystem
import io.docpilot.core.model.ProjectFileType
import io.docpilot.core.model.ProjectLanguage
import io.docpilot.core.model.ProjectSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectSummaryMarkdownRendererTest {

    private val renderer = ProjectSummaryMarkdownRenderer()

    @Test
    fun `renders a reviewable project summary`() {
        val artifact = renderer.render(
            ProjectSummary(
                projectName = "architecture-samples",
                gitRepository = true,
                languages = setOf(ProjectLanguage.KOTLIN),
                buildSystems = setOf(ProjectBuildSystem.GRADLE),
                candidateModulePaths = listOf("app"),
                fileCounts = ProjectFileType.entries.associateWith { type ->
                    when (type) {
                        ProjectFileType.KOTLIN_SOURCE -> 12
                        ProjectFileType.GRADLE_BUILD -> 2
                        ProjectFileType.GRADLE_SETTINGS -> 1
                        ProjectFileType.ANDROID_MANIFEST -> 1
                        else -> 0
                    }
                },
                totalFiles = 16,
                totalDirectories = 8,
            ),
        )

        assertEquals("docs/project-summary.md", artifact.relativePath)
        assertEquals("text/markdown", artifact.mediaType)
        assertTrue(artifact.content.contains("# Project Summary"))
        assertTrue(artifact.content.contains("`architecture-samples`"))
        assertTrue(artifact.content.contains("- `app`"))
        assertTrue(artifact.content.contains("Kotlin source files: 12"))
    }

    @Test
    fun `renders explicit empty sections`() {
        val artifact = renderer.render(
            ProjectSummary(
                projectName = "empty",
                gitRepository = false,
                languages = emptySet(),
                buildSystems = emptySet(),
                candidateModulePaths = emptyList(),
                fileCounts = emptyMap(),
                totalFiles = 0,
                totalDirectories = 0,
            ),
        )

        assertTrue(artifact.content.contains("- None detected"))
    }
}
