package io.docpilot.core.summary

import io.docpilot.core.api.ProjectSummaryBuilder
import io.docpilot.core.model.*

class DefaultProjectSummaryBuilder : ProjectSummaryBuilder {
    override fun build(inventory: ProjectInventory): ProjectSummary {
        val counts = ProjectFileType.entries.associateWith { type ->
            inventory.files.count { it.type == type }
        }

        val languages = buildSet {
            if (counts.getValue(ProjectFileType.KOTLIN_SOURCE) > 0) add(ProjectLanguage.KOTLIN)
            if (counts.getValue(ProjectFileType.JAVA_SOURCE) > 0) add(ProjectLanguage.JAVA)
        }

        val buildSystems = buildSet {
            if (
                counts.getValue(ProjectFileType.GRADLE_BUILD) +
                counts.getValue(ProjectFileType.GRADLE_SETTINGS) > 0
            ) add(ProjectBuildSystem.GRADLE)
        }

        val modules = inventory.files.asSequence()
            .filter { it.type == ProjectFileType.GRADLE_BUILD }
            .map { it.relativePath.substringBeforeLast('/', "") }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .toList()

        return ProjectSummary(
            inventory.project.name,
            inventory.project.gitRepository,
            languages,
            buildSystems,
            modules,
            counts,
            inventory.totalFileCount,
            inventory.totalDirectoryCount,
        )
    }
}
