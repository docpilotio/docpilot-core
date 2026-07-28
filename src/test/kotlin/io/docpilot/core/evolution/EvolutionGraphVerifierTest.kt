package io.docpilot.core.evolution

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvolutionGraphVerifierTest {
    @Test
    fun `rejects cycles dangling endpoints and altered graph hash`() {
        val nodes = listOf(
            DocumentationEvolutionGraphNode("a", EvolutionGraphNodeKind.SPECIFICATION_CHANGE, "a"),
            DocumentationEvolutionGraphNode("b", EvolutionGraphNodeKind.ARTIFACT, "b"),
        )
        val validEdges = listOf(DocumentationEvolutionGraphEdge("a", "b", EvolutionGraphEdgeKind.CHANGES))
        val valid = DocumentationEvolutionGraph(nodes, validEdges, EvolutionCanonicalizer.graphSha256(nodes, validEdges))
        assertTrue(EvolutionGraphVerifier().verify(valid))

        val cycleEdges = listOf(
            DocumentationEvolutionGraphEdge("a", "b", EvolutionGraphEdgeKind.CHANGES),
            DocumentationEvolutionGraphEdge("b", "a", EvolutionGraphEdgeKind.CAUSES),
        ).sortedWith(compareBy({ it.sourceNodeId }, { it.targetNodeId }, { it.kind.name }))
        assertFalse(EvolutionGraphVerifier().verify(DocumentationEvolutionGraph(nodes, cycleEdges, EvolutionCanonicalizer.graphSha256(nodes, cycleEdges))))
        val danglingEdges = listOf(DocumentationEvolutionGraphEdge("a", "missing", EvolutionGraphEdgeKind.CHANGES))
        assertFalse(
            EvolutionGraphVerifier().verify(
                DocumentationEvolutionGraph(nodes, danglingEdges, EvolutionCanonicalizer.graphSha256(nodes, danglingEdges)),
            ),
        )
        assertFalse(EvolutionGraphVerifier().verify(valid.copy(graphSha256 = "0".repeat(64))))
    }
}
