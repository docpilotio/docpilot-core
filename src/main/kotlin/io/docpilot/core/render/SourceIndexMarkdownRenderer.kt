package io.docpilot.core.render

import io.docpilot.core.model.RenderedArtifact
import io.docpilot.core.model.source.SourceFile
import io.docpilot.core.model.source.SourceImport
import io.docpilot.core.model.source.SourceIndex
import io.docpilot.core.model.source.SourceSymbol

/**
 * Renders a project Source Index as deterministic Markdown.
 */
class SourceIndexMarkdownRenderer {

    fun render(index: SourceIndex): RenderedArtifact =
        RenderedArtifact(
            relativePath = "docs/source-index.md",
            mediaType = "text/markdown",
            content = buildMarkdown(index),
        )

    private fun buildMarkdown(index: SourceIndex): String =
        buildString {
            appendLine("# Source Index")
            appendLine()
            appendLine("## Overview")
            appendLine()
            appendLine("- Indexed files: ${index.totalFileCount}")
            appendLine("- Extracted symbols: ${index.totalSymbolCount}")
            appendLine("- Indexing failures: ${index.failures.size}")
            appendLine()
            appendLine("## Files")
            appendLine()

            if (index.files.isEmpty()) {
                appendLine("- None indexed")
            } else {
                index.files.forEach { file ->
                    appendSourceFile(file)
                }
            }

            appendLine()
            appendLine("## Failures")
            appendLine()

            if (index.failures.isEmpty()) {
                appendLine("- None")
            } else {
                index.failures.forEach { failure ->
                    appendLine(
                        "- `${failure.relativePath}`: ${failure.message}",
                    )
                }
            }
        }

    private fun StringBuilder.appendSourceFile(
        file: SourceFile,
    ) {
        appendLine("### `${file.relativePath}`")
        appendLine()
        appendLine(
            "- Language: ${file.language.name}",
        )
        appendLine(
            "- Package: ${
                file.packageName?.let { "`$it`" }
                    ?: "Default package"
            }",
        )
        appendLine()

        appendLine("#### Imports")
        appendLine()
        appendImports(file.imports)
        appendLine()

        appendLine("#### Symbols")
        appendLine()
        appendSymbols(file.symbols)
        appendLine()
    }

    private fun StringBuilder.appendImports(
        imports: List<SourceImport>,
    ) {
        if (imports.isEmpty()) {
            appendLine("- None")
            return
        }

        imports.forEach { sourceImport ->
            val wildcardSuffix =
                if (sourceImport.wildcard) ".*" else ""

            val aliasSuffix =
                sourceImport.alias
                    ?.let { " as $it" }
                    .orEmpty()

            appendLine(
                "- `${sourceImport.qualifiedName}$wildcardSuffix$aliasSuffix`",
            )
        }
    }

    private fun StringBuilder.appendSymbols(
        symbols: List<SourceSymbol>,
    ) {
        if (symbols.isEmpty()) {
            appendLine("- None")
            return
        }

        symbols.forEach { symbol ->
            appendLine(
                "- `${symbol.name}` — " +
                    "${symbol.kind.name}, " +
                    "${symbol.visibility.name}" +
                    locationSuffix(symbol),
            )
        }
    }

    private fun locationSuffix(
        symbol: SourceSymbol,
    ): String {
        val line = symbol.location?.lineStart
            ?: return ""

        return ", line $line"
    }
}
