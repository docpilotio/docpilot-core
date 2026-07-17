package io.docpilot.core.document.service

import io.docpilot.core.document.Document
import io.docpilot.core.document.DocumentMetadata
import io.docpilot.core.document.DocumentSection
import io.docpilot.core.generation.GenerationPipeline
import io.docpilot.core.model.ai.AiGenerationResult

/**
 * Orchestrates generation and converts a successful provider response into the
 * provider-independent document model introduced by RFC-0019.
 */
class DefaultDocumentService(
    private val generationPipeline: GenerationPipeline,
) : DocumentService {

    override fun generate(request: DocumentRequest): Document {
        val result = generationPipeline.generate(request.generation)

        return when (val ai = result.ai) {
            is AiGenerationResult.Success -> {
                val response = ai.response
                Document(
                    title = request.title,
                    sections = listOf(
                        DocumentSection(
                            id = request.sectionId,
                            title = request.sectionTitle,
                            content = response.content,
                        ),
                    ),
                    format = request.format,
                    metadata = DocumentMetadata(
                        type = request.metadataType,
                        attributes = request.metadata + mapOf(
                            DocumentRequest.PROVIDER_ID_METADATA to response.providerId.value,
                            DocumentRequest.MODEL_ID_METADATA to response.modelId.value,
                            DocumentRequest.FINISH_REASON_METADATA to response.finishReason.name,
                        ),
                    ),
                )
            }

            is AiGenerationResult.Failure ->
                throw DocumentGenerationException(ai.error)
        }
    }
}
