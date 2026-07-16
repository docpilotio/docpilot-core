package io.docpilot.core.render

import io.docpilot.core.model.RenderedArtifact
import io.docpilot.core.model.knowledge.KnowledgeEdge
import io.docpilot.core.model.knowledge.KnowledgeGraph
import io.docpilot.core.model.knowledge.KnowledgeNode
import io.docpilot.core.model.knowledge.KnowledgeUnresolvedItem

/**
 * Renders a deterministic Knowledge Graph JSON artifact.
 *
 * This implementation intentionally has no external JSON dependency.
 */
class KnowledgeGraphJsonRenderer {

    fun render(graph: KnowledgeGraph): RenderedArtifact =
        RenderedArtifact(
            relativePath = "docs/knowledge-graph.json",
            mediaType = "application/json",
            content = buildJson(graph),
        )

    private fun buildJson(graph: KnowledgeGraph): String =
        buildString {
            appendLine("{")
            appendLine("  \"schemaVersion\": \"0.1\",")
            appendLine("  \"nodes\": [")
            appendNodes(graph.nodes)
            appendLine("  ],")
            appendLine("  \"edges\": [")
            appendEdges(graph.edges)
            appendLine("  ],")
            appendLine("  \"unresolved\": [")
            appendUnresolved(graph.unresolved)
            appendLine("  ]")
            appendLine("}")
        }

    private fun StringBuilder.appendNodes(
        nodes: List<KnowledgeNode>,
    ) {
        nodes.forEachIndexed { index, node ->
            appendLine("    {")
            appendLine("      \"id\": ${jsonString(node.id)},")
            appendLine("      \"name\": ${jsonString(node.name)},")
            appendLine("      \"kind\": ${jsonString(node.kind.name)},")
            appendLine("      \"confidence\": ${formatConfidence(node.confidence)},")
            appendLine("      \"attributes\": ${jsonObject(node.attributes)},")
            appendLine("      \"evidenceRefs\": ${jsonStringArray(node.evidenceRefs.sorted())}")
            append("    }")

            if (index != nodes.lastIndex) {
                append(',')
            }

            appendLine()
        }
    }

    private fun StringBuilder.appendEdges(
        edges: List<KnowledgeEdge>,
    ) {
        edges.forEachIndexed { index, edge ->
            appendLine("    {")
            appendLine("      \"id\": ${jsonString(edge.id)},")
            appendLine("      \"sourceNodeId\": ${jsonString(edge.sourceNodeId)},")
            appendLine("      \"targetNodeId\": ${jsonString(edge.targetNodeId)},")
            appendLine(
                "      \"relationship\": ${jsonString(edge.relationship.name)},",
            )
            appendLine("      \"confidence\": ${formatConfidence(edge.confidence)},")
            appendLine("      \"attributes\": ${jsonObject(edge.attributes)},")
            appendLine("      \"evidenceRefs\": ${jsonStringArray(edge.evidenceRefs.sorted())}")
            append("    }")

            if (index != edges.lastIndex) {
                append(',')
            }

            appendLine()
        }
    }

    private fun StringBuilder.appendUnresolved(
        unresolved: List<KnowledgeUnresolvedItem>,
    ) {
        unresolved.forEachIndexed { index, item ->
            appendLine("    {")
            appendLine("      \"id\": ${jsonString(item.id)},")
            appendLine(
                "      \"subjectNodeId\": ${
                    item.subjectNodeId?.let(::jsonString) ?: "null"
                },",
            )
            appendLine("      \"question\": ${jsonString(item.question)},")
            appendLine("      \"reason\": ${jsonString(item.reason)}")
            append("    }")

            if (index != unresolved.lastIndex) {
                append(',')
            }

            appendLine()
        }
    }

    private fun jsonObject(
        values: Map<String, String>,
    ): String {
        if (values.isEmpty()) {
            return "{}"
        }

        return values.toSortedMap()
            .entries
            .joinToString(
                prefix = "{",
                postfix = "}",
                separator = ", ",
            ) { (key, value) ->
                "${jsonString(key)}: ${jsonString(value)}"
            }
    }

    private fun jsonStringArray(
        values: List<String>,
    ): String =
        values.joinToString(
            prefix = "[",
            postfix = "]",
            separator = ", ",
            transform = ::jsonString,
        )

    private fun jsonString(value: String): String =
        buildString {
            append('"')

            value.forEach { character ->
                when (character) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else ->
                        if (character.code < 0x20) {
                            append(
                                "\\u%04x".format(character.code),
                            )
                        } else {
                            append(character)
                        }
                }
            }

            append('"')
        }

    private fun formatConfidence(
        confidence: Double,
    ): String =
        when {
            confidence == confidence.toLong().toDouble() ->
                "${confidence.toLong()}.0"

            else ->
                confidence.toString()
        }
}
