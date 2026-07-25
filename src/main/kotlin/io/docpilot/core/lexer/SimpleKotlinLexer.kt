package io.docpilot.core.lexer

import io.docpilot.core.api.KotlinLexer
import io.docpilot.core.model.source.KotlinToken
import io.docpilot.core.model.source.KotlinTokenType

class SimpleKotlinLexer : KotlinLexer {

    override fun tokenize(source: String): List<KotlinToken> =
        Scanner(source).scan()

    private class Scanner(
        private val source: String,
    ) {
        private val tokens = mutableListOf<KotlinToken>()
        private var index = 0
        private var line = 1
        private var column = 1

        fun scan(): List<KotlinToken> {
            while (!isAtEnd()) {
                when {
                    current().isWhitespace() -> skipWhitespace()
                    startsWith("//") -> skipLineComment()
                    startsWith("/*") -> skipBlockComment()
                    current() == '"' -> scanQuoted(
                        KotlinTokenType.STRING_LITERAL,
                        '"',
                    )
                    current() == '\'' -> scanQuoted(
                        KotlinTokenType.CHARACTER_LITERAL,
                        '\'',
                    )
                    current().isDigit() -> scanNumber()
                    isIdentifierStart(current()) -> scanIdentifier()
                    else -> scanSymbol()
                }
            }

            tokens += KotlinToken(
                KotlinTokenType.END_OF_FILE,
                "",
                line,
                column,
            )
            return tokens.toList()
        }

        private fun skipWhitespace() {
            while (!isAtEnd() && current().isWhitespace()) {
                advance()
            }
        }

        private fun skipLineComment() {
            while (!isAtEnd() && current() != '\n') {
                advance()
            }
        }

        private fun skipBlockComment() {
            advance()
            advance()
            while (!isAtEnd() && !startsWith("*/")) {
                advance()
            }
            if (startsWith("*/")) {
                advance()
                advance()
            }
        }

        private fun scanQuoted(
            type: KotlinTokenType,
            closing: Char,
        ) {
            val start = index
            val tokenLine = line
            val tokenColumn = column
            advance()

            while (!isAtEnd()) {
                when {
                    current() == '\\' -> {
                        advance()
                        if (!isAtEnd()) advance()
                    }
                    current() == closing -> {
                        advance()
                        break
                    }
                    else -> advance()
                }
            }

            addToken(
                type,
                source.substring(start, index),
                tokenLine,
                tokenColumn,
            )
        }


        private fun scanBacktickIdentifier() {
            val tokenLine = line
            val tokenColumn = column
            advance()
            val start = index
            while (!isAtEnd() && current() != '`') {
                advance()
            }
            val text = source.substring(start, index)
            if (!isAtEnd()) advance()
            addToken(
                KotlinTokenType.IDENTIFIER,
                text,
                tokenLine,
                tokenColumn,
            )
        }

        private fun scanNumber() {
            val start = index
            val tokenLine = line
            val tokenColumn = column

            while (!isAtEnd() &&
                (current().isLetterOrDigit() ||
                    current() == '_' ||
                    current() == '.')
            ) {
                advance()
            }

            addToken(
                KotlinTokenType.NUMBER_LITERAL,
                source.substring(start, index),
                tokenLine,
                tokenColumn,
            )
        }

        private fun scanIdentifier() {
            val start = index
            val tokenLine = line
            val tokenColumn = column

            while (!isAtEnd() && isIdentifierPart(current())) {
                advance()
            }

            val text = source.substring(start, index)
            addToken(
                if (text in KEYWORDS) {
                    KotlinTokenType.KEYWORD
                } else {
                    KotlinTokenType.IDENTIFIER
                },
                text,
                tokenLine,
                tokenColumn,
            )
        }

        private fun scanSymbol() {
            val tokenLine = line
            val tokenColumn = column
            val text = MULTI_CHARACTER_SYMBOLS.firstOrNull(::startsWith)
                ?: current().toString()
            repeat(text.length) { advance() }
            addToken(
                KotlinTokenType.SYMBOL,
                text,
                tokenLine,
                tokenColumn,
            )
        }

        private fun addToken(
            type: KotlinTokenType,
            text: String,
            tokenLine: Int,
            tokenColumn: Int,
        ) {
            tokens += KotlinToken(
                type,
                text,
                tokenLine,
                tokenColumn,
            )
        }

        private fun advance(): Char {
            val value = source[index]
            index += 1
            if (value == '\n') {
                line += 1
                column = 1
            } else {
                column += 1
            }
            return value
        }

        private fun current(): Char = source[index]
        private fun startsWith(value: String): Boolean =
            source.startsWith(value, index)
        private fun isAtEnd(): Boolean = index >= source.length
        private fun isIdentifierStart(value: Char): Boolean =
            value == '_' || value.isLetter()
        private fun isIdentifierPart(value: Char): Boolean =
            value == '_' || value.isLetterOrDigit()

        companion object {
            private val MULTI_CHARACTER_SYMBOLS = listOf(
                "!==", "===", "..<", "!!", "?.", "?:", "::", "->", "=>",
                "==", "!=", "<=", ">=", "&&", "||", "++", "--", "+=", "-=",
                "*=", "/=", "%=", "..",
            )
            private val KEYWORDS = setOf(
                "package",
                "import",
                "class",
                "interface",
                "object",
                "fun",
                "val",
                "var",
                "typealias",
                "enum",
                "annotation",
                "public",
                "internal",
                "protected",
                "private",
                "constructor", "companion", "abstract", "final", "open",
                "sealed", "data", "value", "inner", "suspend", "operator",
                "infix", "inline", "tailrec", "override", "const", "lateinit",
                "external", "expect", "actual", "where", "as", "out", "in",
            )
        }
    }
}
