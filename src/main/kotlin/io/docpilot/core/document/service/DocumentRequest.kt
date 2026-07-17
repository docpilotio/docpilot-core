package io.docpilot.core.document.service

import io.docpilot.core.document.DocumentFormat
import io.docpilot.core.generation.GenerationRequest

/**
 * Describes one document to be produced from an AI generation request.
 */
data class DocumentRequest(
    val title: String,
    val generation: GenerationRequest,
    val sectionId: String = DEFAULT_SECTION_ID,
    val sectionTitle: String = DEFAULT_SECTION_TITLE,
    val format: DocumentFormat = DocumentFormat.MARKDOWN,
    val metadataType: String = DEFAULT_METADATA_TYPE,
    val metadata: Map<String, String> = emptyMap(),
) {
    init {
        require(title.isNotBlank()) {
            "Document request title must not be blank."
        }
        require(sectionId.isNotBlank()) {
            "Document request sectionId must not be blank."
        }
        require(sectionTitle.isNotBlank()) {
            "Document request sectionTitle must not be blank."
        }
        require(metadataType.isNotBlank()) {
            "Document request metadataType must not be blank."
        }
        require(metadata.keys.none(String::isBlank)) {
            "Document request metadata keys must not be blank."
        }
        require(metadata.keys.none(RESERVED_METADATA_KEYS::contains)) {
            "Document request metadata must not redefine reserved generation metadata keys."
        }
    }

    companion object {
        const val DEFAULT_SECTION_ID: String = "generated-content"
        const val DEFAULT_SECTION_TITLE: String = "Generated Content"
        const val DEFAULT_METADATA_TYPE: String = "ai-generated-document"

        const val PROVIDER_ID_METADATA: String = "ai.providerId"
        const val MODEL_ID_METADATA: String = "ai.modelId"
        const val FINISH_REASON_METADATA: String = "ai.finishReason"

        val RESERVED_METADATA_KEYS: Set<String> = setOf(
            PROVIDER_ID_METADATA,
            MODEL_ID_METADATA,
            FINISH_REASON_METADATA,
        )
    }
}
