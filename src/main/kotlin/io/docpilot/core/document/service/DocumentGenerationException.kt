package io.docpilot.core.document.service

import io.docpilot.core.model.ai.AiError

/**
 * Raised when the generation pipeline cannot produce content for a document.
 */
class DocumentGenerationException(
    val error: AiError,
) : IllegalStateException(
    "Document generation failed [${error.code}]: ${error.message}",
)
