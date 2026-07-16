package io.docpilot.core.model.selection

data class SelectionContext<ID>(
    val explicitCandidateId: ID? = null,
    val preferredCandidateIds: List<ID> = emptyList(),
    val priorities: Map<ID, Int> = emptyMap(),
) {
    init {
        require(
            preferredCandidateIds.distinct().size ==
                preferredCandidateIds.size,
        ) {
            "Preferred candidate IDs must be unique."
        }
    }
}

sealed interface SelectionResult<out T, out ID> {
    data class Selected<T, ID>(
        val candidate: T,
        val candidateId: ID,
        val reason: SelectionReason,
    ) : SelectionResult<T, ID>

    data class Unavailable(
        val reasons: List<String>,
    ) : SelectionResult<Nothing, Nothing> {
        init {
            require(reasons.isNotEmpty())
            require(reasons.none(String::isBlank))
        }
    }
}

enum class SelectionReason {
    EXPLICIT,
    PREFERRED,
    PRIORITY,
    DETERMINISTIC_FALLBACK,
}
