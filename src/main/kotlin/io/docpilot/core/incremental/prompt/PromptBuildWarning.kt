package io.docpilot.core.incremental.prompt

/** Non-fatal condition discovered while building a prompt. */
data class PromptBuildWarning(
    val code: PromptBuildWarningCode,
    val message: String,
) {
    init {
        require(message.isNotBlank()) { "Prompt warning message must not be blank." }
    }
}

enum class PromptBuildWarningCode {
    MISSING_KNOWLEDGE_NODE,
    MISSING_EVIDENCE,
    CONTEXT_TRUNCATED,
    PREVIOUS_SECTION_TRUNCATED,
}
