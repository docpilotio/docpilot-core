package io.docpilot.core.model

/**
 * Concise facts derived from a [ProjectInventory].
 */
public data class ProjectSummary(
    public val projectName: String,
    public val gitRepository: Boolean,
    public val languages: Set<ProjectLanguage>,
    public val buildSystems: Set<ProjectBuildSystem>,
    public val candidateModulePaths: List<String>,
    public val fileCounts: Map<ProjectFileType, Int>,
    public val totalFiles: Int,
    public val totalDirectories: Int,
) {
    init {
        require(projectName.isNotBlank()) {
            "projectName must not be blank."
        }
        require(totalFiles >= 0) {
            "totalFiles must not be negative."
        }
        require(totalDirectories >= 0) {
            "totalDirectories must not be negative."
        }
        require(fileCounts.values.all { it >= 0 }) {
            "fileCounts must not contain negative values."
        }
    }

    public fun count(type: ProjectFileType): Int =
        fileCounts[type] ?: 0
}

public enum class ProjectLanguage {
    KOTLIN,
    JAVA,
}

public enum class ProjectBuildSystem {
    GRADLE,
}
