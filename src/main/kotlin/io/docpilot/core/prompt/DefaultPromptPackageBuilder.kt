package io.docpilot.core.prompt

import io.docpilot.core.api.PromptPackageBuilder
import io.docpilot.core.model.RenderedArtifact
import io.docpilot.core.model.evidence.Evidence
import io.docpilot.core.model.knowledge.KnowledgeBuildResult
import io.docpilot.core.model.prompt.PromptPackage
import io.docpilot.core.render.KnowledgeGraphJsonRenderer

/**
 * Creates a stable set of files for future AI-provider integrations.
 */
class DefaultPromptPackageBuilder :
    PromptPackageBuilder {

    override fun build(
        knowledge: KnowledgeBuildResult,
    ): PromptPackage {
        val graphArtifact =
            KnowledgeGraphJsonRenderer()
                .render(knowledge.graph)
                .copy(
                    relativePath =
                        "prompt-package/knowledge-graph.json",
                )

        return PromptPackage(
            artifacts = listOf(
                buildOverview(knowledge),
                graphArtifact,
                buildEvidenceJson(knowledge.evidence.items),
                buildInstructions(),
            ),
        )
    }

    private fun buildOverview(
        knowledge: KnowledgeBuildResult,
    ): RenderedArtifact =
        RenderedArtifact(
            relativePath = "prompt-package/overview.md",
            mediaType = "text/markdown",
            content = buildString {
                appendLine("# DocPilot Prompt Package")
                appendLine()
                appendLine("## Summary")
                appendLine()
                appendLine(
                    "- Knowledge nodes: " +
                        knowledge.graph.nodeCount,
                )
                appendLine(
                    "- Knowledge edges: " +
                        knowledge.graph.edgeCount,
                )
                appendLine(
                    "- Evidence items: " +
                        knowledge.evidence.size,
                )
                appendLine(
                    "- Unresolved items: " +
                        knowledge.graph.unresolved.size,
                )
                appendLine()
                appendLine("## Usage")
                appendLine()
                appendLine(
                    "Use only the supplied graph and evidence " +
                        "when generating architectural explanations.",
                )
                appendLine(
                    "Treat unresolved items as questions, not facts.",
                )
            },
        )

    private fun buildInstructions(): RenderedArtifact =
        RenderedArtifact(
            relativePath =
                "prompt-package/instructions.md",
            mediaType = "text/markdown",
            content = """
                # AI Instructions

                1. Distinguish deterministic facts from inference.
                2. Cite evidence IDs for factual claims.
                3. Do not invent missing relationships.
                4. Surface uncertainty explicitly.
                5. Treat unresolved items as open questions.
            """.trimIndent() + "\n",
        )

    private fun buildEvidenceJson(
        evidence: List<Evidence>,
    ): RenderedArtifact =
        RenderedArtifact(
            relativePath =
                "prompt-package/evidence.json",
            mediaType = "application/json",
            content = buildString {
                appendLine("{")
                appendLine(
                    "  \"schemaVersion\": \"0.1\",",
                )
                appendLine("  \"items\": [")

                evidence
                    .sortedBy { it.id.value }
                    .forEachIndexed { index, item ->
                        appendLine("    {")
                        appendLine(
                            "      \"id\": " +
                                json(item.id.value) + ",",
                        )
                        appendLine(
                            "      \"type\": " +
                                json(item.type.name) + ",",
                        )
                        appendLine(
                            "      \"summary\": " +
                                json(item.summary) + ",",
                        )
                        appendLine(
                            "      \"location\": {",
                        )
                        appendLine(
                            "        \"relativePath\": " +
                                json(
                                    item.location.relativePath,
                                ) + ",",
                        )
                        appendLine(
                            "        \"lineStart\": " +
                                nullableNumber(
                                    item.location.lineStart,
                                ) + ",",
                        )
                        appendLine(
                            "        \"columnStart\": " +
                                nullableNumber(
                                    item.location.columnStart,
                                ) + ",",
                        )
                        appendLine(
                            "        \"lineEnd\": " +
                                nullableNumber(
                                    item.location.lineEnd,
                                ) + ",",
                        )
                        appendLine(
                            "        \"columnEnd\": " +
                                nullableNumber(
                                    item.location.columnEnd,
                                ),
                        )
                        appendLine("      },")
                        appendLine(
                            "      \"attributes\": " +
                                jsonObject(item.attributes),
                        )
                        append("    }")

                        if (index != evidence.lastIndex) {
                            append(',')
                        }

                        appendLine()
                    }

                appendLine("  ]")
                appendLine("}")
            },
        )

    private fun nullableNumber(
        value: Int?,
    ): String =
        value?.toString() ?: "null"

    private fun jsonObject(
        values: Map<String, String>,
    ): String =
        values.toSortedMap()
            .entries
            .joinToString(
                prefix = "{",
                postfix = "}",
                separator = ", ",
            ) { (key, value) ->
                "${json(key)}: ${json(value)}"
            }

    private fun json(
        value: String,
    ): String =
        buildString {
            append('"')

            value.forEach { character ->
                when (character) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(character)
                }
            }

            append('"')
        }
}
