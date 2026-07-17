package io.docpilot.core.incremental.planning

/** Relative execution importance for an incremental generation job. */
enum class GenerationPriority(val weight: Int) {
    HIGH(3),
    MEDIUM(2),
    LOW(1),
}
