package io.docpilot.core.incremental.knowledge

import io.docpilot.core.incremental.ProjectChangeSet
import io.docpilot.core.incremental.ProjectFileChangeType
import io.docpilot.core.model.knowledge.KnowledgeBuildResult
import io.docpilot.core.model.knowledge.KnowledgeEdge

/**
 * Evidence-driven, provider-independent incremental knowledge analysis.
 *
 * Direct impact is derived from source evidence and the conventional
 * `relativePath` node attribute. The result is then expanded through incident
 * graph edges by [neighborDepth] hops so callers receive the changed symbols
 * together with their immediate architectural context.
 */
class DefaultIncrementalKnowledgeAnalyzer(
    private val neighborDepth: Int = DEFAULT_NEIGHBOR_DEPTH,
) : IncrementalKnowledgeAnalyzer {

    init {
        require(neighborDepth >= 0) { "neighborDepth must not be negative." }
    }

    override fun analyze(
        knowledge: KnowledgeBuildResult,
        changes: ProjectChangeSet,
    ): IncrementalKnowledgeImpact {
        val changedPaths = changes.changes
            .asSequence()
            .filter { it.type != ProjectFileChangeType.UNCHANGED }
            .map { normalizePath(it.relativePath) }
            .distinct()
            .sorted()
            .toList()

        if (changedPaths.isEmpty()) {
            return IncrementalKnowledgeImpact.EMPTY
        }

        val changedPathSet = changedPaths.toSet()
        val affectedEvidenceIds = knowledge.evidence.items
            .asSequence()
            .filter { normalizePath(it.location.relativePath) in changedPathSet }
            .map { it.id.value }
            .toSortedSet()

        val directNodeIds = knowledge.graph.nodes
            .asSequence()
            .filter { node ->
                node.attributes[RELATIVE_PATH_ATTRIBUTE]
                    ?.let(::normalizePath)
                    ?.let(changedPathSet::contains) == true ||
                    node.evidenceRefs.any(affectedEvidenceIds::contains)
            }
            .map { it.id }
            .toSortedSet()

        val affectedNodeIds = expandNodes(
            seedNodeIds = directNodeIds,
            edges = knowledge.graph.edges,
        )

        val affectedEdgeIds = knowledge.graph.edges
            .asSequence()
            .filter { edge ->
                edge.sourceNodeId in affectedNodeIds ||
                    edge.targetNodeId in affectedNodeIds ||
                    edge.evidenceRefs.any(affectedEvidenceIds::contains)
            }
            .map { it.id }
            .toSortedSet()

        val referencedEvidenceIds = buildSet {
            addAll(affectedEvidenceIds)
            knowledge.graph.nodes
                .filter { it.id in affectedNodeIds }
                .forEach { addAll(it.evidenceRefs) }
            knowledge.graph.edges
                .filter { it.id in affectedEdgeIds }
                .forEach { addAll(it.evidenceRefs) }
        }.sorted()

        return IncrementalKnowledgeImpact(
            changedRelativePaths = changedPaths,
            directlyAffectedNodeIds = directNodeIds.toList(),
            affectedNodeIds = affectedNodeIds.toList(),
            affectedEdgeIds = affectedEdgeIds.toList(),
            affectedEvidenceIds = referencedEvidenceIds,
        )
    }

    private fun expandNodes(
        seedNodeIds: Set<String>,
        edges: List<KnowledgeEdge>,
    ): java.util.SortedSet<String> {
        val affected = seedNodeIds.toSortedSet()
        var frontier: Set<String> = seedNodeIds

        repeat(neighborDepth) {
            if (frontier.isEmpty()) return@repeat

            val next = edges.asSequence()
                .filter {
                    it.sourceNodeId in frontier || it.targetNodeId in frontier
                }
                .flatMap { sequenceOf(it.sourceNodeId, it.targetNodeId) }
                .filterNot(affected::contains)
                .toSortedSet()

            affected.addAll(next)
            frontier = next
        }

        return affected
    }

    private fun normalizePath(path: String): String =
        path.replace('\\', '/').removePrefix("./")

    companion object {
        const val DEFAULT_NEIGHBOR_DEPTH: Int = 1
        private const val RELATIVE_PATH_ATTRIBUTE = "relativePath"
    }
}
