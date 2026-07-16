package io.docpilot.core.api

import io.docpilot.core.model.ai.AiGenerationResult
import io.docpilot.core.model.ai.AiProviderDescriptor
import io.docpilot.core.model.ai.AiRequest

interface AiProvider {
    val descriptor: AiProviderDescriptor

    fun generate(
        request: AiRequest,
    ): AiGenerationResult
}
