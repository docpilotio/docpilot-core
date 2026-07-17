package io.docpilot.core.knowledge

import io.docpilot.core.api.KnowledgeRetriever
import io.docpilot.core.model.evidence.Evidence
import io.docpilot.core.model.knowledge.KnowledgeBuildResult
import io.docpilot.core.model.knowledge.KnowledgeNode
import io.docpilot.core.model.knowledge.KnowledgeNodeKind
import io.docpilot.core.model.knowledge.KnowledgeQuery
import io.docpilot.core.model.knowledge.KnowledgeResult

/**
 * Performs simple deterministic filtering without AI, embeddings, or ranking.
 */
class DefaultKnowledgeRetriever : KnowledgeRetriever {

    override fun retrieve(
        knowledge: KnowledgeBuildResult,
        query: KnowledgeQuery,
    ): KnowledgeResult {
        val evidenceById = knowledge.evidence.items.associateBy {
            it.id.value
        }

        val matchedNodes = knowledge.graph.nodes
            .asSequence()
            .filter { node ->
                matchesName(
                    node = node,
                    query = query,
                )
            }
            .filter { node ->
                matchesKind(
                    node = node,
                    query = query,
                )
            }
            .filter { node ->
                matchesRelativePath(
                    node = node,
                    query = query,
                    evidenceById = evidenceById,
                )
            }
            .sortedBy { node ->
                node.id
            }
            .take(query.limit)
            .toList()

        val matchedNodeIds = matchedNodes
            .mapTo(mutableSetOf()) { node ->
                node.id
            }

        val matchedNodeEvidenceIds = matchedNodes
            .flatMapTo(mutableSetOf()) { node ->
                node.evidenceRefs
            }

        val relatedEdges = knowledge.graph.edges
            .asSequence()
            .filter { edge ->
                edge.sourceNodeId in matchedNodeIds ||
                        edge.targetNodeId in matchedNodeIds
            }
            .filter { edge ->
                edge.evidenceRefs.isNotEmpty() &&
                        edge.evidenceRefs.all { evidenceId ->
                            evidenceId in matchedNodeEvidenceIds
                        }
            }
            .sortedBy { edge ->
                edge.id
            }
            .toList()

        val evidenceIds = buildSet {
            matchedNodes.forEach { node ->
                addAll(node.evidenceRefs)
            }
            relatedEdges.forEach { edge ->
                addAll(edge.evidenceRefs)
            }
        }

        val matchedEvidence = evidenceIds
            .mapNotNull(evidenceById::get)
            .sortedBy { evidence ->
                evidence.id.value
            }

        return KnowledgeResult(
            nodes = matchedNodes,
            edges = relatedEdges,
            evidence = matchedEvidence,
        )
    }

    private fun matchesName(
        node: KnowledgeNode,
        query: KnowledgeQuery,
    ): Boolean {
        val expected = query.name ?: return true

        /*
         * A name-only query is treated as a symbol lookup.
         * FILE nodes participate only when FILE is explicitly requested.
         */
        if (
            node.kind == KnowledgeNodeKind.FILE &&
            query.kind != KnowledgeNodeKind.FILE
        ) {
            return false
        }

        return node.name.contains(
            other = expected,
            ignoreCase = true,
        )
    }

    private fun matchesKind(
        node: KnowledgeNode,
        query: KnowledgeQuery,
    ): Boolean =
        query.kind?.let { expected ->
            node.kind == expected
        } ?: true

    private fun matchesRelativePath(
        node: KnowledgeNode,
        query: KnowledgeQuery,
        evidenceById: Map<String, Evidence>,
    ): Boolean {
        val expected = query.relativePath
            ?.replace('\\', '/')
            ?: return true

        val attributePath = node.attributes["relativePath"]
            ?.replace('\\', '/')

        if (
            attributePath?.contains(
                other = expected,
                ignoreCase = true,
            ) == true
        ) {
            return true
        }

        return node.evidenceRefs.any { reference ->
            evidenceById[reference]
                ?.location
                ?.relativePath
                ?.replace('\\', '/')
                ?.contains(
                    other = expected,
                    ignoreCase = true,
                ) == true
        }
    }
}
