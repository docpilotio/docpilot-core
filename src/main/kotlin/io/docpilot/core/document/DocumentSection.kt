package io.docpilot.core.document

data class DocumentSection(
    val id: String,
    val title: String,
    val content: String,
    val level: Int = 2,
) {
    init {
        require(id.isNotBlank()) {
            "Document section id must not be blank."
        }
        require(title.isNotBlank()) {
            "Document section title must not be blank."
        }
        require(level in 1..6) {
            "Document section level must be between 1 and 6."
        }
    }
}
