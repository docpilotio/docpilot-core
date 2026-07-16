package io.docpilot.core.model.knowledge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class KnowledgeGraphModelTest {

    @Test
    fun `creates a valid graph`() {
        val project = KnowledgeNode(
            id = "project:sample",
            name = "sample",
            kind = KnowledgeNodeKind.PROJECT,
        )
        val file = KnowledgeNode(
            id = "file:Sample.kt",
            name = "Sample.kt",
            kind = KnowledgeNodeKind.FILE,
        )
        val edge = KnowledgeEdge(
            id = "edge:contains",
            sourceNodeId = project.id,
            targetNodeId = file.id,
            relationship = RelationshipType.CONTAINS,
        )

        val graph = KnowledgeGraph(
            nodes = listOf(project, file),
            edges = listOf(edge),
            unresolved = listOf(
                KnowledgeUnresolvedItem(
                    id = "unresolved:role",
                    subjectNodeId = file.id,
                    question = "What role does this file have?",
                    reason = "No deterministic evidence.",
                ),
            ),
        )

        assertEquals(2, graph.nodeCount)
        assertEquals(1, graph.edgeCount)
        assertNotNull(graph.node(file.id))
    }

    @Test
    fun `rejects duplicate node IDs`() {
        val first = KnowledgeNode(
            id = "node:same",
            name = "First",
            kind = KnowledgeNodeKind.CLASS,
        )
        val second = first.copy(name = "Second")

        assertFailsWith<IllegalArgumentException> {
            KnowledgeGraph(listOf(first, second), emptyList())
        }
    }

    @Test
    fun `rejects missing edge endpoints`() {
        val source = KnowledgeNode(
            id = "node:source",
            name = "Source",
            kind = KnowledgeNodeKind.CLASS,
        )

        assertFailsWith<IllegalArgumentException> {
            KnowledgeGraph(
                nodes = listOf(source),
                edges = listOf(
                    KnowledgeEdge(
                        id = "edge:missing",
                        sourceNodeId = source.id,
                        targetNodeId = "node:missing",
                        relationship = RelationshipType.USES,
                    ),
                ),
            )
        }
    }

    @Test
    fun `rejects invalid confidence`() {
        assertFailsWith<IllegalArgumentException> {
            KnowledgeNode(
                id = "node:invalid",
                name = "Invalid",
                kind = KnowledgeNodeKind.UNKNOWN,
                confidence = 1.1,
            )
        }
    }
}
