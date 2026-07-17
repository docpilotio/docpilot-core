package io.docpilot.core.generation.context

import io.docpilot.core.model.knowledge.KnowledgeResult

/**
 * Renders knowledge without AI, embeddings, ranking, or provider-specific
 * tokenization.
 */
class DefaultKnowledgeContextRenderer : KnowledgeContextRenderer {

    override fun render(
        knowledge: KnowledgeResult,
        policy: KnowledgeContextPolicy,
    ): RenderedKnowledgeContext {
        val nodes = knowledge.nodes.take(policy.maxNodes)
        val edges = knowledge.edges.take(policy.maxEdges)
        val evidence = knowledge.evidence.take(policy.maxEvidence)

        val omittedNodes = knowledge.nodes.size - nodes.size
        val omittedEdges = knowledge.edges.size - edges.size
        val omittedEvidence = knowledge.evidence.size - evidence.size

        val lines = buildList {
            add("## Nodes")
            if (nodes.isEmpty()) {
                add("- None")
            } else {
                nodes.forEach { node ->
                    add("- ${node.id} | ${node.kind} | ${oneLine(node.name)}")
                }
            }

            add("")
            add("## Relationships")
            if (edges.isEmpty()) {
                add("- None")
            } else {
                edges.forEach { edge ->
                    add(
                        "- ${edge.id} | ${edge.sourceNodeId} " +
                            "--${edge.relationship}--> ${edge.targetNodeId}",
                    )
                }
            }

            add("")
            add("## Evidence")
            if (evidence.isEmpty()) {
                add("- None")
            } else {
                evidence.forEach { item ->
                    add(
                        "- ${item.id.value} | " +
                            "${item.location.relativePath} | " +
                            truncate(
                                value = oneLine(item.summary),
                                maxCharacters =
                                    policy.maxEvidenceSummaryCharacters,
                            ),
                    )
                }
            }

            add("")
            add("## Context Limits")
            add("- Omitted nodes: $omittedNodes")
            add("- Omitted relationships: $omittedEdges")
            add("- Omitted evidence: $omittedEvidence")
        }

        val bounded = fitCompleteLines(
            lines = lines,
            maxCharacters = policy.maxCharacters,
        )

        return RenderedKnowledgeContext(
            content = bounded.content,
            includedNodes = nodes.size,
            includedEdges = edges.size,
            includedEvidence = evidence.size,
            omittedNodes = omittedNodes,
            omittedEdges = omittedEdges,
            omittedEvidence = omittedEvidence,
            characterBudgetReached = bounded.characterBudgetReached,
        )
    }

    private fun fitCompleteLines(
        lines: List<String>,
        maxCharacters: Int,
    ): BoundedText {
        val output = StringBuilder()
        var characterBudgetReached = false

        for (line in lines) {
            val separatorLength = if (output.isEmpty()) 0 else 1
            val required = separatorLength + line.length

            if (output.length + required > maxCharacters) {
                characterBudgetReached = true
                break
            }

            if (separatorLength == 1) {
                output.append('\n')
            }
            output.append(line)
        }

        if (output.isEmpty()) {
            val fallback = "#".take(maxCharacters)
            return BoundedText(
                content = fallback,
                characterBudgetReached = true,
            )
        }

        return BoundedText(
            content = output.toString(),
            characterBudgetReached = characterBudgetReached,
        )
    }

    private fun oneLine(value: String): String =
        value.replace(WHITESPACE, " ").trim()

    private fun truncate(
        value: String,
        maxCharacters: Int,
    ): String {
        if (value.length <= maxCharacters) {
            return value
        }

        if (maxCharacters == 1) {
            return ELLIPSIS
        }

        return value.take(maxCharacters - 1).trimEnd() + ELLIPSIS
    }

    private data class BoundedText(
        val content: String,
        val characterBudgetReached: Boolean,
    )

    companion object {
        private val WHITESPACE = Regex("\\s+")
        private const val ELLIPSIS = "…"
    }
}
