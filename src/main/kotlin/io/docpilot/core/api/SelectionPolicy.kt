package io.docpilot.core.api

import io.docpilot.core.model.selection.SelectionContext
import io.docpilot.core.model.selection.SelectionResult

/**
 * Generic deterministic policy for selecting one candidate.
 */
interface SelectionPolicy<T> {

    fun select(
        candidates: List<T>,
        context: SelectionContext = SelectionContext(),
    ): SelectionResult<T>
}