package io.docpilot.core.document

/**
 * Deterministically renders the format declared by a [Document].
 */
class DocumentRenderer {

    fun render(document: Document): String =
        when (document.format) {
            DocumentFormat.MARKDOWN -> renderMarkdown(document)
            DocumentFormat.PLAIN_TEXT -> renderPlainText(document)
        }

    private fun renderMarkdown(document: Document): String = buildString {
        append("# ")
        appendLine(document.title)

        document.metadata?.let { metadata ->
            appendLine()
            appendLine("<!--")
            append("type: ")
            appendLine(metadata.type)
            metadata.attributes.toSortedMap().forEach { (key, value) ->
                append(key)
                append(": ")
                appendLine(value)
            }
            appendLine("-->")
        }

        document.sections.forEach { section ->
            appendLine()
            append("#".repeat(section.level))
            append(" ")
            appendLine(section.title)
            if (section.content.isNotEmpty()) {
                appendLine()
                appendLine(section.content.trimEnd())
            }
        }
    }.trimEnd()

    private fun renderPlainText(document: Document): String = buildString {
        appendLine(document.title)

        document.sections.forEach { section ->
            appendLine()
            appendLine(section.title)
            if (section.content.isNotEmpty()) {
                appendLine()
                appendLine(section.content.trimEnd())
            }
        }
    }.trimEnd()
}
