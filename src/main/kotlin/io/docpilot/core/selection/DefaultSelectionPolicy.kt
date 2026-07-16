package io.docpilot.core.selection

import io.docpilot.core.api.SelectionPolicy
import io.docpilot.core.model.selection.SelectionContext
import io.docpilot.core.model.selection.SelectionReason
import io.docpilot.core.model.selection.SelectionResult

class DefaultSelectionPolicy<T, ID : Comparable<ID>>(
    private val candidateId: (T) -> ID,
) : SelectionPolicy<T, ID> {

    override fun select(
        candidates: List<T>,
        context: SelectionContext<ID>,
    ): SelectionResult<T, ID> {
        if (candidates.isEmpty()) {
            return SelectionResult.Unavailable(
                listOf("No candidates are available."),
            )
        }

        val candidatesById = candidates.associateBy(candidateId)
        require(candidatesById.size == candidates.size) {
            "Candidate IDs must be unique."
        }

        context.explicitCandidateId?.let { id ->
            val candidate = candidatesById[id]
            return if (candidate != null) {
                selected(candidate, id, SelectionReason.EXPLICIT)
            } else {
                SelectionResult.Unavailable(
                    listOf("Explicit candidate is unavailable: $id"),
                )
            }
        }

        context.preferredCandidateIds.forEach { id ->
            candidatesById[id]?.let {
                return selected(
                    it,
                    id,
                    SelectionReason.PREFERRED,
                )
            }
        }

        val prioritized = candidatesById.entries
            .filter { it.key in context.priorities }
            .sortedWith(
                compareByDescending<Map.Entry<ID, T>> {
                    context.priorities.getValue(it.key)
                }.thenBy { it.key },
            )
            .firstOrNull()

        if (prioritized != null) {
            return selected(
                prioritized.value,
                prioritized.key,
                SelectionReason.PRIORITY,
            )
        }

        val fallback = candidatesById.entries.minBy { it.key }
        return selected(
            fallback.value,
            fallback.key,
            SelectionReason.DETERMINISTIC_FALLBACK,
        )
    }

    private fun selected(
        candidate: T,
        id: ID,
        reason: SelectionReason,
    ) = SelectionResult.Selected(
        candidate = candidate,
        candidateId = id,
        reason = reason,
    )
}
