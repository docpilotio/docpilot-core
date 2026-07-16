package io.docpilot.core.model.source

data class KotlinToken(
    val type: KotlinTokenType,
    val text: String,
    val line: Int,
    val column: Int,
) {
    init {
        require(line > 0) { "line must be greater than zero." }
        require(column > 0) { "column must be greater than zero." }
    }
}

enum class KotlinTokenType {
    KEYWORD,
    IDENTIFIER,
    STRING_LITERAL,
    CHARACTER_LITERAL,
    NUMBER_LITERAL,
    SYMBOL,
    END_OF_FILE,
}
