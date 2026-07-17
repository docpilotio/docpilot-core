package io.docpilot.core.incremental.prompt

import io.docpilot.core.incremental.ProjectFileChangeType

/** Minimal change metadata exposed to a generation prompt. */
data class PromptChangedFile(
    val relativePath: String,
    val type: ProjectFileChangeType,
) {
    init {
        require(relativePath.isNotBlank()) { "Prompt changed-file path must not be blank." }
    }
}
