package io.docpilot.core.model

/**
 * Deterministic inventory of files discovered in a project.
 *
 * Every path is relative to [project.path].
 */
public data class ProjectInventory(
    public val project: ProjectRoot,
    public val directories: List<String>,
    public val files: List<ProjectFile>,
) {
    public val totalDirectoryCount: Int
        get() = directories.size

    public val totalFileCount: Int
        get() = files.size

    public fun filesOfType(type: ProjectFileType): List<ProjectFile> =
        files.filter { it.type == type }
}

public data class ProjectFile(
    public val relativePath: String,
    public val type: ProjectFileType,
) {
    init {
        require(relativePath.isNotBlank()) {
            "relativePath must not be blank."
        }
    }
}

public enum class ProjectFileType {
    KOTLIN_SOURCE,
    JAVA_SOURCE,
    GRADLE_BUILD,
    GRADLE_SETTINGS,
    ANDROID_MANIFEST,
    XML_RESOURCE,
    MARKDOWN,
    OTHER,
}
