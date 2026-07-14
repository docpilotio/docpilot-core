package io.docpilot.core.model

data class ProjectInventory(
    val project: ProjectRoot,
    val directories: List<String>,
    val files: List<ProjectFile>,
) {
    val totalDirectoryCount: Int get() = directories.size
    val totalFileCount: Int get() = files.size
    fun filesOfType(type: ProjectFileType): List<ProjectFile> =
        files.filter { it.type == type }
}

data class ProjectFile(
    val relativePath: String,
    val type: ProjectFileType,
)

enum class ProjectFileType {
    KOTLIN_SOURCE,
    JAVA_SOURCE,
    GRADLE_BUILD,
    GRADLE_SETTINGS,
    ANDROID_MANIFEST,
    XML_RESOURCE,
    MARKDOWN,
    OTHER,
}
