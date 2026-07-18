package io.docpilot.core.incremental.specification.review

public fun interface ManagedDocumentationBlockReader {
    public fun read(documentation: String): Map<String, String>
}

public class HtmlCommentManagedDocumentationBlockReader : ManagedDocumentationBlockReader {
    override fun read(documentation: String): Map<String, String> {
        val startIds = START_REGEX.findAll(documentation).map { it.groupValues[1].trim() }.toList()
        val endIds = END_REGEX.findAll(documentation).map { it.groupValues[1].trim() }.toList()
        val matches = BLOCK_REGEX.findAll(documentation).toList()

        require(startIds.size == endIds.size && matches.size == startIds.size) {
            "Existing documentation contains malformed DocPilot AI managed blocks."
        }
        require(startIds.sorted() == endIds.sorted()) {
            "Existing documentation contains mismatched DocPilot AI managed block markers."
        }
        require(startIds.distinct().size == startIds.size) {
            "Existing documentation contains duplicate DocPilot AI managed block target ids."
        }

        return matches
            .map { match ->
                val targetId = match.groupValues[1].trim()
                val markdown = match.groupValues[2].trim()
                require(targetId.isNotBlank()) { "Managed documentation target id must not be blank." }
                targetId to markdown
            }
            .sortedBy { it.first }
            .toMap(linkedMapOf())
    }

    private companion object {
        val START_REGEX = Regex("<!--\\s*DOCPILOT_AI_START\\s+id=([^\\s>]+)\\s*-->")
        val END_REGEX = Regex("<!--\\s*DOCPILOT_AI_END\\s+id=([^\\s>]+)\\s*-->")
        val BLOCK_REGEX = Regex(
            "<!--\\s*DOCPILOT_AI_START\\s+id=([^\\s>]+)\\s*-->(.*?)<!--\\s*DOCPILOT_AI_END\\s+id=\\1\\s*-->",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )
    }
}
