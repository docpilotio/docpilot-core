package io.docpilot.core.incremental.specification.ai

public fun interface AiDocumentationPatchCodec {
    public fun decode(response: String): List<AiDocumentationPatch>
}

public class MarkerAiDocumentationPatchCodec : AiDocumentationPatchCodec {
    override fun decode(response: String): List<AiDocumentationPatch> {
        val upserts = PATCH_REGEX.findAll(response).map { match ->
            val id = match.groupValues[1].trim()
            val markdown = match.groupValues[2].trim()
            require(id.isNotBlank()) { "AI patch target id must not be blank." }
            require(markdown.isNotBlank()) { "AI patch markdown must not be blank for target: $id" }
            require(!DOCPILOT_MARKER_REGEX.containsMatchIn(markdown)) {
                "AI patch markdown must not contain nested DocPilot markers for target: $id"
            }
            AiDocumentationPatch(id, markdown)
        }.toList()
        val removals = REMOVE_REGEX.findAll(response).map { match ->
            val id = match.groupValues[1].trim()
            require(id.isNotBlank()) { "AI remove target id must not be blank." }
            AiDocumentationPatch(id, "", AiDocumentationPatchOperation.REMOVE)
        }.toList()
        val patches = upserts + removals
        require(patches.isNotEmpty()) { "AI response did not contain any DocPilot patch operations." }
        require(patches.map { it.targetId }.distinct().size == patches.size) {
            "AI response contains duplicate patch target ids."
        }
        val recognizedRanges = (PATCH_REGEX.findAll(response) + REMOVE_REGEX.findAll(response))
            .map { it.range }
            .sortedBy { it.first }
            .toList()
        val unrecognizedDocPilotMarkers = DOCPILOT_MARKER_REGEX.findAll(response)
            .filter { marker -> recognizedRanges.none { marker.range.first in it } }
            .toList()
        require(unrecognizedDocPilotMarkers.isEmpty()) {
            "AI response contains malformed or unknown DocPilot patch markers."
        }
        return patches.sortedWith(compareBy({ it.targetId }, { it.operation.ordinal }))
    }

    private companion object {
        val PATCH_REGEX = Regex(
            "<<<DOCPILOT_PATCH\\s+id=([^>]+)>>>(.*?)<<<END_DOCPILOT_PATCH>>>",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )
        val REMOVE_REGEX = Regex("<<<DOCPILOT_REMOVE\\s+id=([^>]+)>>>")
        val DOCPILOT_MARKER_REGEX = Regex("<<<(?:END_)?DOCPILOT_[^>]*>>>")
    }
}
