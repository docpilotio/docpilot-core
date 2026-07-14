package io.docpilot.core.model

data class ProjectSummary(
    val projectName: String,
    val gitRepository: Boolean,
    val languages: Set<ProjectLanguage>,
    val buildSystems: Set<ProjectBuildSystem>,
    val candidateModulePaths: List<String>,
    val fileCounts: Map<ProjectFileType, Int>,
    val totalFiles: Int,
    val totalDirectories: Int,
) {
    fun count(type: ProjectFileType): Int = fileCounts[type] ?: 0
}

enum class ProjectLanguage { KOTLIN, JAVA }
enum class ProjectBuildSystem { GRADLE }
