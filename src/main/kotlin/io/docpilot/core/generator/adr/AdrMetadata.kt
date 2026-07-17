package io.docpilot.core.generator.adr

/** Lifecycle status written to ADR metadata and supplied to the ADR prompt. */
enum class AdrStatus(val value: String) {
    PROPOSED("proposed"),
    ACCEPTED("accepted"),
    DEPRECATED("deprecated"),
    SUPERSEDED("superseded"),
}

/** Stable metadata keys emitted by the ADR generator. */
object AdrMetadata {
    const val DOCUMENT_TYPE: String = "adr"
    const val SECTION_ID: String = "adr"
    const val STATUS_KEY: String = "adr.status"
    const val TITLE_KEY: String = "adr.title"
    const val GENERATOR_KEY: String = "document.generator"
    const val GENERATOR_VALUE: String = "adr"
}
