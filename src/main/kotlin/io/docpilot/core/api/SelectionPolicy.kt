package io.docpilot.core.api

import io.docpilot.core.model.selection.SelectionContext
import io.docpilot.core.model.selection.SelectionResult

interface SelectionPolicy<T, ID> {
    fun select(
        candidates: List<T>,
        context: SelectionContext<ID> = SelectionContext(),
    ): SelectionResult<T, ID>
}
