package io.docpilot.core.incremental.specification.ai

public fun interface AiDocumentationMerger {
    public fun merge(existingDocumentation: String, patches: List<AiDocumentationPatch>): String
}

public class ManagedBlockAiDocumentationMerger : AiDocumentationMerger {
    override fun merge(existingDocumentation: String, patches: List<AiDocumentationPatch>): String {
        var merged = existingDocumentation.trimEnd()
        patches.sortedBy { it.targetId }.forEach { patch ->
            val block = renderBlock(patch)
            val regex = managedBlockRegex(patch.targetId)
            merged = if (regex.containsMatchIn(merged)) {
                merged.replace(regex, block)
            } else {
                val heading = if (merged.contains(AI_HEADING)) "" else "\n\n$AI_HEADING"
                "$merged$heading\n\n$block"
            }
        }
        return merged.trimEnd() + "\n"
    }

    private fun renderBlock(patch: AiDocumentationPatch): String = buildString {
        appendLine("<!-- DOCPILOT_AI_START id=${patch.targetId} -->")
        appendLine(patch.markdown.trim())
        append("<!-- DOCPILOT_AI_END id=${patch.targetId} -->")
    }

    private fun managedBlockRegex(targetId: String): Regex = Regex(
        "<!-- DOCPILOT_AI_START id=${Regex.escape(targetId)} -->(.*?)<!-- DOCPILOT_AI_END id=${Regex.escape(targetId)} -->",
        setOf(RegexOption.DOT_MATCHES_ALL),
    )

    private companion object {
        const val AI_HEADING = "## AI Incremental Documentation"
    }
}
