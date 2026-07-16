package io.docpilot.core.render

import io.docpilot.core.model.knowledge.KnowledgeEdge
import io.docpilot.core.model.knowledge.KnowledgeGraph
import io.docpilot.core.model.knowledge.KnowledgeNode
import io.docpilot.core.model.knowledge.KnowledgeNodeKind
import io.docpilot.core.model.knowledge.KnowledgeUnresolvedItem
import io.docpilot.core.model.knowledge.RelationshipType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KnowledgeGraphJsonRendererTest {

    private val renderer =
        KnowledgeGraphJsonRenderer()

    @Test
    fun `renders deterministic JSON artifact`() {
        val file = KnowledgeNode(
            id = "file:Sample.kt",
            name = "Sample.kt",
            kind = KnowledgeNodeKind.FILE,
            attributes = linkedMapOf(
                "language" to "KOTLIN",
                "relativePath" to "Sample.kt",
            ),
            evidenceRefs = linkedSetOf(
                "source:Sample.kt",
            ),
        )
        val symbol = KnowledgeNode(
            id = "symbol:Sample",
            name = "Sample",
            kind = KnowledgeNodeKind.CLASS,
            confidence = 0.9,
        )
        val edge = KnowledgeEdge(
            id = "edge:declares",
            sourceNodeId = file.id,
            targetNodeId = symbol.id,
            relationship = RelationshipType.DECLARES,
        )

        val artifact = renderer.render(
            KnowledgeGraph(
                nodes = listOf(file, symbol),
                edges = listOf(edge),
                unresolved = listOf(
                    KnowledgeUnresolvedItem(
                        id = "unresolved:role",
                        subjectNodeId = symbol.id,
                        question =
                            "What is the architectural role?",
                        reason =
                            "No deterministic role evidence.",
                    ),
                ),
            ),
        )

        assertEquals(
            "docs/knowledge-graph.json",
            artifact.relativePath,
        )
        assertEquals(
            "application/json",
            artifact.mediaType,
        )
        assertTrue(
            artifact.content.contains(
                "\"schemaVersion\": \"0.1\"",
            ),
        )
        assertTrue(
            artifact.content.contains(
                "\"relationship\": \"DECLARES\"",
            ),
        )
        assertTrue(
            artifact.content.contains(
                "\"confidence\": 0.9",
            ),
        )

        val languageIndex =
            artifact.content.indexOf("\"language\"")
        val relativePathIndex =
            artifact.content.indexOf("\"relativePath\"")

        assertTrue(languageIndex < relativePathIndex)
    }

    @Test
    fun `escapes JSON string values`() {
        val node = KnowledgeNode(
            id = "node:special",
            name = "A \"quoted\"\\name\nnext",
            kind = KnowledgeNodeKind.UNKNOWN,
        )

        val content = renderer.render(
            KnowledgeGraph(
                nodes = listOf(node),
                edges = emptyList(),
            ),
        ).content

        assertTrue(
            content.contains(
                "\"name\": \"A \\\"quoted\\\"\\\\name\\nnext\"",
            ),
        )
    }

    @Test
    fun `renders empty graph arrays`() {
        val content = renderer.render(
            KnowledgeGraph(
                nodes = emptyList(),
                edges = emptyList(),
            ),
        ).content

        assertTrue(content.contains("\"nodes\": [\n  ]"))
        assertTrue(content.contains("\"edges\": [\n  ]"))
        assertTrue(
            content.contains("\"unresolved\": [\n  ]"),
        )
    }
}
