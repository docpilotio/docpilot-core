package io.docpilot.core.extractor

import io.docpilot.core.api.KotlinSymbolExtractor
import io.docpilot.core.model.source.KotlinToken
import io.docpilot.core.model.source.KotlinTokenType
import io.docpilot.core.model.source.SourceFile
import io.docpilot.core.model.source.SourceImport
import io.docpilot.core.model.source.SourceLanguage
import io.docpilot.core.model.source.SourceLocation
import io.docpilot.core.model.source.SourceSymbol
import io.docpilot.core.model.source.SourceSymbolKind
import io.docpilot.core.model.source.SourceVisibility

class SimpleKotlinSymbolExtractor : KotlinSymbolExtractor {

    override fun extract(
        relativePath: String,
        tokens: List<KotlinToken>,
    ): SourceFile {
        require(relativePath.isNotBlank()) {
            "relativePath must not be blank."
        }

        return SourceFile(
            relativePath = relativePath,
            language = SourceLanguage.KOTLIN,
            packageName = extractPackageName(tokens),
            imports = extractImports(tokens),
            symbols = extractSymbols(relativePath, tokens),
        )
    }

    private fun extractPackageName(
        tokens: List<KotlinToken>,
    ): String? {
        val keywordIndex = tokens.indexOfFirst {
            it.type == KotlinTokenType.KEYWORD &&
                it.text == "package"
        }

        if (keywordIndex < 0) return null

        return readQualifiedName(
            tokens = tokens,
            startIndex = keywordIndex + 1,
        ).qualifiedName.takeIf { it.isNotBlank() }
    }

    private fun extractImports(
        tokens: List<KotlinToken>,
    ): List<SourceImport> {
        val imports = mutableListOf<SourceImport>()
        var index = 0

        while (index < tokens.size) {
            val token = tokens[index]

            if (token.type == KotlinTokenType.KEYWORD &&
                token.text == "import"
            ) {
                val qualified = readQualifiedName(
                    tokens = tokens,
                    startIndex = index + 1,
                )

                var cursor = qualified.nextIndex
                var alias: String? = null

                if (tokens.getOrNull(cursor)?.text == "as") {
                    alias = tokens.getOrNull(cursor + 1)
                        ?.takeIf {
                            it.type == KotlinTokenType.IDENTIFIER
                        }
                        ?.text
                    cursor += 2
                }

                if (qualified.qualifiedName.isNotBlank()) {
                    imports += SourceImport(
                        qualifiedName = qualified.qualifiedName,
                        alias = alias,
                        wildcard = qualified.wildcard,
                    )
                }

                index = maxOf(cursor, index + 1)
            } else {
                index += 1
            }
        }

        return imports
    }

    private fun readQualifiedName(
        tokens: List<KotlinToken>,
        startIndex: Int,
    ): QualifiedNameResult {
        val parts = mutableListOf<String>()
        var index = startIndex
        var wildcard = false
        var expectIdentifier = true

        while (index < tokens.size) {
            val token = tokens[index]

            when {
                expectIdentifier &&
                    token.type == KotlinTokenType.IDENTIFIER -> {
                    parts += token.text
                    expectIdentifier = false
                    index += 1
                }

                !expectIdentifier &&
                    token.type == KotlinTokenType.SYMBOL &&
                    token.text == "." -> {
                    if (tokens.getOrNull(index + 1)?.text == "*") {
                        wildcard = true
                        index += 2
                        break
                    }

                    expectIdentifier = true
                    index += 1
                }

                else -> break
            }
        }

        return QualifiedNameResult(
            qualifiedName = parts.joinToString("."),
            nextIndex = index,
            wildcard = wildcard,
        )
    }

    private fun extractSymbols(
        relativePath: String,
        tokens: List<KotlinToken>,
    ): List<SourceSymbol> {
        val symbols = mutableListOf<SourceSymbol>()
        var index = 0
        var pendingVisibility = SourceVisibility.DEFAULT

        while (index < tokens.size) {
            val token = tokens[index]

            val visibility = visibilityOf(token)
            if (visibility != null) {
                pendingVisibility = visibility
                index += 1
                continue
            }

            val declaration = declarationAt(tokens, index)

            if (declaration != null) {
                val nameToken = tokens.getOrNull(
                    declaration.nameIndex,
                )

                if (nameToken?.type == KotlinTokenType.IDENTIFIER) {
                    symbols += SourceSymbol(
                        name = nameToken.text,
                        kind = declaration.kind,
                        visibility = pendingVisibility,
                        location = SourceLocation(
                            relativePath = relativePath,
                            lineStart = token.line,
                            columnStart = token.column,
                        ),
                    )
                }

                pendingVisibility = SourceVisibility.DEFAULT
                index = maxOf(
                    index + 1,
                    declaration.nameIndex + 1,
                )
            } else {
                if (token.type != KotlinTokenType.KEYWORD) {
                    pendingVisibility =
                        SourceVisibility.DEFAULT
                }

                index += 1
            }
        }

        return symbols
    }

    private fun declarationAt(
        tokens: List<KotlinToken>,
        index: Int,
    ): DeclarationResult? {
        val token = tokens.getOrNull(index)
            ?: return null

        if (token.type != KotlinTokenType.KEYWORD) {
            return null
        }

        return when (token.text) {
            "class" -> DeclarationResult(
                SourceSymbolKind.CLASS,
                index + 1,
            )

            "interface" -> DeclarationResult(
                SourceSymbolKind.INTERFACE,
                index + 1,
            )

            "object" -> DeclarationResult(
                SourceSymbolKind.OBJECT,
                index + 1,
            )

            "fun" -> DeclarationResult(
                SourceSymbolKind.FUNCTION,
                index + 1,
            )

            "val", "var" -> DeclarationResult(
                SourceSymbolKind.PROPERTY,
                index + 1,
            )

            "typealias" -> DeclarationResult(
                SourceSymbolKind.TYPE_ALIAS,
                index + 1,
            )

            "enum" ->
                if (tokens.getOrNull(index + 1)?.text == "class") {
                    DeclarationResult(
                        SourceSymbolKind.ENUM_CLASS,
                        index + 2,
                    )
                } else {
                    null
                }

            "annotation" ->
                if (tokens.getOrNull(index + 1)?.text == "class") {
                    DeclarationResult(
                        SourceSymbolKind.ANNOTATION_CLASS,
                        index + 2,
                    )
                } else {
                    null
                }

            else -> null
        }
    }

    private fun visibilityOf(
        token: KotlinToken,
    ): SourceVisibility? {
        if (token.type != KotlinTokenType.KEYWORD) {
            return null
        }

        return when (token.text) {
            "public" -> SourceVisibility.PUBLIC
            "internal" -> SourceVisibility.INTERNAL
            "protected" -> SourceVisibility.PROTECTED
            "private" -> SourceVisibility.PRIVATE
            else -> null
        }
    }

    private data class QualifiedNameResult(
        val qualifiedName: String,
        val nextIndex: Int,
        val wildcard: Boolean,
    )

    private data class DeclarationResult(
        val kind: SourceSymbolKind,
        val nameIndex: Int,
    )
}
