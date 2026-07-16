package io.docpilot.core.selection

import io.docpilot.core.api.SelectionPolicy
import io.docpilot.core.model.selection.SelectionContext
import io.docpilot.core.model.selection.SelectionReason
import io.docpilot.core.model.selection.SelectionResult

/**
 * Reusable deterministic candidate-selection policy.
 */
class DefaultSelectionPolicy<T>(
    private val candidateId: (T) -> String,
) : SelectionPolicy<T> {

    override fun select(
        candidates: List<T>,
        context: SelectionContext,
    ): SelectionResult<T> {
        if (candidates.isEmpty()) {
            return SelectionResult.Unavailable(
                reasons = listOf("No candidates are available."),
            )
        }

        val candidatesById = candidates
            .associateBy(candidateId)

        require(candidatesById.size == candidates.size) {
            "Candidate IDs must be unique."
        }
        require(candidatesById.keys.none(String::isBlank)) {
            "Candidate IDs must not be blank."
        }

        context.explicitCandidateId?.let { explicitId ->
            val explicit = candidatesById[explicitId]

            return if (explicit != null) {
                selected(
                    candidate = explicit,
                    id = explicitId,
                    reason = SelectionReason.EXPLICIT,
                )
            } else {
                SelectionResult.Unavailable(
                    reasons = listOf(
                        "Explicit candidate is unavailable: " +
                            explicitId,
                    ),
                )
            }
        }

        context.preferredCandidateIds.forEach { preferredId ->
            candidatesById[preferredId]?.let { candidate ->
                return selected(
                    candidate = candidate,
                    id = preferredId,
                    reason = SelectionReason.PREFERRED,
                )
            }
        }

        val prioritized = candidatesById.entries
            .filter { (id, _) -> id in context.priorities }
            .sortedWith(
                compareByDescending<Map.Entry<String, T>> {
                    context.priorities.getValue(it.key)
                }.thenBy { it.key },
            )
            .firstOrNull()

        if (prioritized != null) {
            return selected(
                candidate = prioritized.value,
                id = prioritized.key,
                reason = SelectionReason.PRIORITY,
            )
        }

        val fallback = candidatesById.entries
            .minBy { it.key }

        return selected(
            candidate = fallback.value,
            id = fallback.key,
            reason = SelectionReason.DETERMINISTIC_FALLBACK,
        )
    }

    private fun selected(
        candidate: T,
        id: String,
        reason: SelectionReason,
    ): SelectionResult.Selected<T> =
        SelectionResult.Selected(
            candidate = candidate,
            candidateId = id,
            reason = reason,
        )
}
