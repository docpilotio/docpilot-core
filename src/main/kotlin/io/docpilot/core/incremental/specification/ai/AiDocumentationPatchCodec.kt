package io.docpilot.core.incremental.specification.ai

public fun interface AiDocumentationPatchCodec {
    public fun decode(response: String): List<AiDocumentationPatch>
}

public class MarkerAiDocumentationPatchCodec : AiDocumentationPatchCodec {
    override fun decode(response: String): List<AiDocumentationPatch> {
        val matches = PATCH_REGEX.findAll(response).toList()
        require(matches.isNotEmpty()) { "AI response did not contain any DocPilot patch blocks." }
        val patches = matches.map { match ->
            val id = match.groupValues[1].trim()
            val markdown = match.groupValues[2].trim()
            require(id.isNotBlank()) { "AI patch target id must not be blank." }
            require(markdown.isNotBlank()) { "AI patch markdown must not be blank for target: $id" }
            AiDocumentationPatch(id, markdown)
        }
        require(patches.map { it.targetId }.distinct().size == patches.size) {
            "AI response contains duplicate patch target ids."
        }
        return patches.sortedBy { it.targetId }
    }

    private companion object {
        val PATCH_REGEX = Regex(
            "<<<DOCPILOT_PATCH\\s+id=([^>]+)>>>(.*?)<<<END_DOCPILOT_PATCH>>>",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )
    }
}
