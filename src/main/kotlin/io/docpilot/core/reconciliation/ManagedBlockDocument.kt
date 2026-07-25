package io.docpilot.core.reconciliation

internal data class ManagedBlockRegion(
    val id: String,
    val fullStart: Int,
    val fullEnd: Int,
    val body: String,
)

internal object ManagedBlockDocument {
    private val block = Regex(
        "<!--\\s*DOCPILOT_AI_START\\s+id=([^\\s>]+)\\s*-->(.*?)" +
            "<!--\\s*DOCPILOT_AI_END\\s+id=\\1\\s*-->",
        setOf(RegexOption.DOT_MATCHES_ALL),
    )
    private val starts = Regex("<!--\\s*DOCPILOT_AI_START\\s+id=([^\\s>]+)\\s*-->")
    private val ends = Regex("<!--\\s*DOCPILOT_AI_END\\s+id=([^\\s>]+)\\s*-->")

    fun parse(content: String): List<ManagedBlockRegion> {
        val matches = block.findAll(content).toList()
        val startIds = starts.findAll(content).map { it.groupValues[1] }.toList()
        val endIds = ends.findAll(content).map { it.groupValues[1] }.toList()
        require(matches.size == startIds.size && startIds.sorted() == endIds.sorted()) {
            "Malformed managed block markers."
        }
        require(startIds.distinct().size == startIds.size) { "Duplicate managed block ids." }
        return matches.map {
            ManagedBlockRegion(
                id = it.groupValues[1],
                fullStart = it.range.first,
                fullEnd = it.range.last + 1,
                body = it.groupValues[2],
            )
        }
    }

    fun merge(
        base: String,
        current: String,
        candidate: String,
        authorizedRemovalBlockIds: Set<String> = emptySet(),
    ): String {
        val baseBlocks = parse(base).associateBy { it.id }
        val currentBlocks = parse(current).associateBy { it.id }
        val candidateBlocks = parse(candidate).associateBy { it.id }
        require(baseBlocks.keys == currentBlocks.keys) {
            "Managed block sets changed."
        }
        val removed = baseBlocks.keys - candidateBlocks.keys
        require(candidateBlocks.keys.all(baseBlocks::containsKey) && removed.all(authorizedRemovalBlockIds::contains)) {
            "Managed block removal requires complete explicit Review approval."
        }
        var merged = current
        currentBlocks.values.sortedByDescending { it.fullStart }.forEach { currentBlock ->
            val baseBody = baseBlocks.getValue(currentBlock.id).body
            val currentBody = currentBlock.body
            val candidateBlock = candidateBlocks[currentBlock.id]
            val replacement = if (candidateBlock == null) {
                check(currentBody == baseBody) {
                    "Managed block ${currentBlock.id} changed before approved removal."
                }
                ""
            } else {
                when {
                    currentBody == baseBody -> candidate.substring(candidateBlock.fullStart, candidateBlock.fullEnd)
                    candidateBlock.body == baseBody || candidateBlock.body == currentBody ->
                        current.substring(currentBlock.fullStart, currentBlock.fullEnd)
                    else -> error("Managed block ${currentBlock.id} changed in both current and candidate.")
                }
            }
            merged = merged.replaceRange(currentBlock.fullStart, currentBlock.fullEnd, replacement)
        }
        return merged
    }
}
