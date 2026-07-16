package io.docpilot.core.model.selection

/**
 * Selection preferences ordered from strongest to weakest.
 *
 * 1. explicitCandidateId
 * 2. preferredCandidateIds
 * 3. priorities
 * 4. deterministic candidate ID order
 */
data class SelectionContext(
    val explicitCandidateId: String? = null,
    val preferredCandidateIds: List<String> = emptyList(),
    val priorities: Map<String, Int> = emptyMap(),
) {
    init {
        require(
            explicitCandidateId == null ||
                explicitCandidateId.isNotBlank(),
        ) {
            "Explicit candidate ID must be null or non-blank."
        }
        require(preferredCandidateIds.none(String::isBlank)) {
            "Preferred candidate IDs must not be blank."
        }
        require(
            preferredCandidateIds.distinct().size ==
                preferredCandidateIds.size,
        ) {
            "Preferred candidate IDs must be unique."
        }
        require(priorities.keys.none(String::isBlank)) {
            "Priority candidate IDs must not be blank."
        }
    }
}

sealed interface SelectionResult<out T> {

    data class Selected<T>(
        val candidate: T,
        val candidateId: String,
        val reason: SelectionReason,
    ) : SelectionResult<T> {
        init {
            require(candidateId.isNotBlank()) {
                "Selected candidate ID must not be blank."
            }
        }
    }

    data class Unavailable(
        val reasons: List<String>,
    ) : SelectionResult<Nothing> {
        init {
            require(reasons.isNotEmpty()) {
                "Unavailable selection must contain a reason."
            }
            require(reasons.none(String::isBlank)) {
                "Selection reasons must not be blank."
            }
        }
    }
}

enum class SelectionReason {
    EXPLICIT,
    PREFERRED,
    PRIORITY,
    DETERMINISTIC_FALLBACK,
}
