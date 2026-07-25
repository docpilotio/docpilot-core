package io.docpilot.core.incremental.specification.ai

public fun interface AiDocumentationMerger {
    public fun merge(existingDocumentation: String, patches: List<AiDocumentationPatch>): String
}

public class ManagedBlockAiDocumentationMerger : AiDocumentationMerger {
    override fun merge(existingDocumentation: String, patches: List<AiDocumentationPatch>): String {
        require(patches.map { it.targetId }.distinct().size == patches.size) {
            "AI documentation operations contain duplicate target ids."
        }
        validateManagedBlocks(existingDocumentation)
        val existingIds = managedBlockMatches(existingDocumentation).map { it.groupValues[1].trim() }.toSet()
        patches.filter { it.operation == AiDocumentationPatchOperation.REMOVE }.forEach {
            require(it.targetId in existingIds) {
                "Cannot remove missing managed documentation block: ${it.targetId}"
            }
        }

        var merged = existingDocumentation.trimEnd()
        patches.sortedWith(compareBy({ it.targetId }, { it.operation.ordinal })).forEach { patch ->
            when (patch.operation) {
                AiDocumentationPatchOperation.UPSERT -> {
                    val block = renderBlock(patch)
                    val regex = managedBlockRegex(patch.targetId)
                    merged = if (regex.containsMatchIn(merged)) {
                        merged.replace(regex, block)
                    } else {
                        val heading = if (merged.contains(AI_HEADING)) "" else "\n\n$AI_HEADING"
                        "$merged$heading\n\n$block"
                    }
                }
                AiDocumentationPatchOperation.REMOVE -> {
                    merged = managedBlockRegex(patch.targetId).replace(merged, "")
                    merged = normalizeRemovalBoundary(merged)
                }
            }
        }
        return removeEmptyOwnedHeading(merged).trimEnd() + "\n"
    }

    private fun renderBlock(patch: AiDocumentationPatch): String = buildString {
        appendLine("<!-- DOCPILOT_AI_START id=${patch.targetId} -->")
        appendLine(patch.markdown.trim())
        append("<!-- DOCPILOT_AI_END id=${patch.targetId} -->")
    }

    private fun managedBlockRegex(targetId: String): Regex = Regex(
        "<!--\\s*DOCPILOT_AI_START\\s+id=${Regex.escape(targetId)}\\s*-->" +
            "(.*?)" +
            "<!--\\s*DOCPILOT_AI_END\\s+id=${Regex.escape(targetId)}\\s*-->",
        setOf(RegexOption.DOT_MATCHES_ALL),
    )

    private fun managedBlockMatches(documentation: String): List<MatchResult> =
        BLOCK_REGEX.findAll(documentation).toList()

    private fun validateManagedBlocks(documentation: String) {
        val startIds = START_REGEX.findAll(documentation).map { it.groupValues[1].trim() }.toList()
        val endIds = END_REGEX.findAll(documentation).map { it.groupValues[1].trim() }.toList()
        val matches = managedBlockMatches(documentation)
        require(startIds.size == endIds.size && matches.size == startIds.size) {
            "Existing documentation contains malformed DocPilot AI managed blocks."
        }
        require(startIds.sorted() == endIds.sorted()) {
            "Existing documentation contains mismatched DocPilot AI managed block markers."
        }
        require(startIds.distinct().size == startIds.size) {
            "Existing documentation contains duplicate DocPilot AI managed block target ids."
        }
    }

    private fun normalizeRemovalBoundary(documentation: String): String =
        documentation.replace(Regex("(\\r?\\n){3,}"), "\n\n")

    private fun removeEmptyOwnedHeading(documentation: String): String {
        if (BLOCK_REGEX.containsMatchIn(documentation)) return documentation
        val headingStart = documentation.indexOf(AI_HEADING)
        if (headingStart < 0) return documentation
        val lineStart = documentation.lastIndexOf('\n', headingStart).let { if (it < 0) 0 else it + 1 }
        val headingLineEnd = documentation.indexOf('\n', headingStart).let {
            if (it < 0) documentation.length else it + 1
        }
        if (documentation.substring(lineStart, headingLineEnd).trim() != AI_HEADING) return documentation
        val nextSection = documentation.indexOf("\n## ", headingLineEnd)
            .let { if (it < 0) documentation.length else it + 1 }
        if (documentation.substring(headingLineEnd, nextSection).isNotBlank()) return documentation
        return documentation.removeRange(lineStart, nextSection)
    }

    private companion object {
        const val AI_HEADING = "## AI Incremental Documentation"
        val START_REGEX = Regex("<!--\\s*DOCPILOT_AI_START\\s+id=([^\\s>]+)\\s*-->")
        val END_REGEX = Regex("<!--\\s*DOCPILOT_AI_END\\s+id=([^\\s>]+)\\s*-->")
        val BLOCK_REGEX = Regex(
            "<!--\\s*DOCPILOT_AI_START\\s+id=([^\\s>]+)\\s*-->(.*?)<!--\\s*DOCPILOT_AI_END\\s+id=\\1\\s*-->",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )
    }
}
