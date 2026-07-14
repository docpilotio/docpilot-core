package io.docpilot.core.summary

import io.docpilot.core.api.ProjectSummaryBuilder
import io.docpilot.core.model.ProjectBuildSystem
import io.docpilot.core.model.ProjectFile
import io.docpilot.core.model.ProjectFileType
import io.docpilot.core.model.ProjectInventory
import io.docpilot.core.model.ProjectLanguage
import io.docpilot.core.model.ProjectSummary

/**
 * Builds a project summary using only facts available in the inventory.
 */
public class DefaultProjectSummaryBuilder : ProjectSummaryBuilder {

    override fun build(inventory: ProjectInventory): ProjectSummary {
        val counts = ProjectFileType.entries.associateWith { type ->
            inventory.files.count { it.type == type }
        }

        val languages = buildSet {
            if (counts.getValue(ProjectFileType.KOTLIN_SOURCE) > 0) {
                add(ProjectLanguage.KOTLIN)
            }
            if (counts.getValue(ProjectFileType.JAVA_SOURCE) > 0) {
                add(ProjectLanguage.JAVA)
            }
        }

        val buildSystems = buildSet {
            val gradleFileCount =
                counts.getValue(ProjectFileType.GRADLE_BUILD) +
                    counts.getValue(ProjectFileType.GRADLE_SETTINGS)

            if (gradleFileCount > 0) {
                add(ProjectBuildSystem.GRADLE)
            }
        }

        return ProjectSummary(
            projectName = inventory.project.name,
            gitRepository = inventory.project.gitRepository,
            languages = languages,
            buildSystems = buildSystems,
            candidateModulePaths = detectCandidateModulePaths(inventory.files),
            fileCounts = counts,
            totalFiles = inventory.totalFileCount,
            totalDirectories = inventory.totalDirectoryCount,
        )
    }

    private fun detectCandidateModulePaths(
        files: List<ProjectFile>,
    ): List<String> =
        files.asSequence()
            .filter { it.type == ProjectFileType.GRADLE_BUILD }
            .map(ProjectFile::relativePath)
            .mapNotNull(::parentPath)
            .distinct()
            .sorted()
            .toList()

    private fun parentPath(relativePath: String): String? {
        val separatorIndex = relativePath.lastIndexOf('/')

        if (separatorIndex <= 0) {
            return null
        }

        return relativePath.substring(0, separatorIndex)
    }
}
