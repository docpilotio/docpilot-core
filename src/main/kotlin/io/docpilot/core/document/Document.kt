package io.docpilot.core.document

data class Document(
    val title: String,
    val sections: List<DocumentSection>,
    val format: DocumentFormat = DocumentFormat.MARKDOWN,
    val metadata: DocumentMetadata? = null,
) {
    init {
        require(title.isNotBlank()) {
            "Document title must not be blank."
        }
        require(sections.map(DocumentSection::id).distinct().size == sections.size) {
            "Document section ids must be unique."
        }
    }
}
