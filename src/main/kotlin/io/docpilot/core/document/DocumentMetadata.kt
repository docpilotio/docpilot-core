package io.docpilot.core.document

data class DocumentMetadata(
    val type: String,
    val attributes: Map<String, String> = emptyMap(),
) {
    init {
        require(type.isNotBlank()) {
            "Document metadata type must not be blank."
        }
        require(attributes.keys.none(String::isBlank)) {
            "Document metadata attribute keys must not be blank."
        }
    }
}
