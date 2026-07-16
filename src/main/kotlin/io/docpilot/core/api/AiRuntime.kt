package io.docpilot.core.api

import io.docpilot.core.model.ai.AiGenerationResult
import io.docpilot.core.model.ai.AiProviderId
import io.docpilot.core.model.ai.AiRequest
import io.docpilot.core.model.selection.SelectionContext

interface AiRuntime {
    fun generate(
        request: AiRequest,
        selectionContext: SelectionContext<AiProviderId> =
            SelectionContext(),
    ): AiGenerationResult
}
