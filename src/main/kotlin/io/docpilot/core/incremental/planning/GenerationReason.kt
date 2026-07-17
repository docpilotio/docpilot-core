package io.docpilot.core.incremental.planning

/** Evidence explaining why a documentation section must be regenerated. */
enum class GenerationReason {
    SOURCE_CHANGED,
    KNOWLEDGE_UPDATED,
    DEPENDENCY_REQUIRED,
}
