package io.docpilot.core.evolution

import io.docpilot.core.incremental.execution.DocumentationArtifactReason
import io.docpilot.core.reconciliation.DocumentationOwnership
import io.docpilot.core.reconciliation.ReconciliationOperationKind

internal class EvolutionCausalGraphBuilder {
    fun build(
        request: DocumentationEvolutionRequest,
        changes: List<DocumentationEvolutionChange>,
        impacts: List<EvolutionArtifactImpact>,
    ): DocumentationEvolutionGraph {
        val nodes = linkedMapOf<String, DocumentationEvolutionGraphNode>()
        val edges = linkedSetOf<DocumentationEvolutionGraphEdge>()
        fun node(value: DocumentationEvolutionGraphNode) {
            val previous = nodes.putIfAbsent(value.nodeId, value)
            require(previous == null || previous == value) { "Conflicting Evolution graph node: ${value.nodeId}" }
        }
        fun edge(source: String, target: String, kind: EvolutionGraphEdgeKind) {
            edges += DocumentationEvolutionGraphEdge(source, target, kind)
        }

        changes.forEach { change ->
            val changeNodeId = "change:${change.changeId}"
            node(
                DocumentationEvolutionGraphNode(
                    changeNodeId,
                    graphKind(change),
                    change.subjectId,
                    change.evidenceRefs,
                ),
            )
            change.evidenceRefs.forEach { evidenceRef ->
                val evidenceNodeId = "evidence:${EvolutionCanonicalizer.sha256(evidenceRef)}"
                node(DocumentationEvolutionGraphNode(evidenceNodeId, EvolutionGraphNodeKind.SOURCE_EVIDENCE, evidenceRef, listOf(evidenceRef)))
                edge(evidenceNodeId, changeNodeId, EvolutionGraphEdgeKind.CAUSES)
            }
        }

        impacts.forEach { impact ->
            val artifactNodeId = "artifact:${impact.artifactId}"
            val planNodeId = "artifact-plan:${impact.artifactId}:${impact.operation.name}"
            node(DocumentationEvolutionGraphNode(artifactNodeId, EvolutionGraphNodeKind.ARTIFACT, impact.artifactId))
            node(DocumentationEvolutionGraphNode(planNodeId, EvolutionGraphNodeKind.ARTIFACT_PLAN_ACTION, impact.artifactId))
            changes.filter { it.subjectKind == EvolutionSubjectKind.ARTIFACT && it.subjectId == impact.artifactId }
                .forEach { artifactChange ->
                    edge(
                        "change:${artifactChange.changeId}",
                        artifactNodeId,
                        if (artifactChange.kind == EvolutionChangeKind.ARTIFACT_RETAINED) {
                            EvolutionGraphEdgeKind.RETAINS
                        } else {
                            EvolutionGraphEdgeKind.CHANGES
                        },
                    )
                }
            impact.causalChangeIds.forEach { changeId ->
                val edgeKind = if (DocumentationArtifactReason.DEPENDENCY_REFRESH.name in impact.selectionReasons) {
                    EvolutionGraphEdgeKind.REFRESHES
                } else {
                    EvolutionGraphEdgeKind.SELECTS
                }
                edge("change:$changeId", planNodeId, edgeKind)
            }
            edge(
                planNodeId,
                artifactNodeId,
                if (impact.operation == io.docpilot.core.incremental.execution.DocumentationArtifactOperation.KEEP) {
                    EvolutionGraphEdgeKind.RETAINS
                } else {
                    EvolutionGraphEdgeKind.PRODUCES
                },
            )
            if (impact.beforeArtifactSha256 != null) {
                val state = "document-state:before:${impact.artifactId}:${impact.beforeArtifactSha256}"
                node(DocumentationEvolutionGraphNode(state, EvolutionGraphNodeKind.DOCUMENT_STATE, impact.beforeArtifactSha256))
            }
            if (impact.afterArtifactSha256 != null) {
                val state = "document-state:after:${impact.artifactId}:${impact.afterArtifactSha256}"
                node(DocumentationEvolutionGraphNode(state, EvolutionGraphNodeKind.DOCUMENT_STATE, impact.afterArtifactSha256))
                edge(artifactNodeId, state, EvolutionGraphEdgeKind.CHANGES)
            }
        }

        val ownershipNodes = mutableMapOf<String, String>()
        request.afterOwnershipManifests.forEach { manifest ->
            val nodeId = "ownership:${manifest.artifactId.value}:${manifest.manifestSha256}"
            ownershipNodes[manifest.artifactId.value] = nodeId
            node(
                DocumentationEvolutionGraphNode(
                    nodeId,
                    EvolutionGraphNodeKind.OWNERSHIP_DECISION,
                    manifest.artifactId.value,
                    manifest.evidenceRefs,
                ),
            )
        }
        request.reconciliationPlan?.operations.orEmpty().forEach { operation ->
            val operationNodeId = "reconciliation:${operation.operationId}"
            node(
                DocumentationEvolutionGraphNode(
                    operationNodeId,
                    EvolutionGraphNodeKind.RECONCILIATION_OPERATION,
                    operation.operationId,
                    operation.evidenceRefs,
                ),
            )
            ownershipNodes[operation.artifactId.value]?.let { ownershipNode ->
                val edgeKind = when {
                    operation.kind == ReconciliationOperationKind.CONFLICT ||
                        operation.ownership in setOf(DocumentationOwnership.UNKNOWN, DocumentationOwnership.CONFLICTED) ->
                        EvolutionGraphEdgeKind.PROHIBITS
                    else -> EvolutionGraphEdgeKind.PERMITS
                }
                edge(ownershipNode, operationNodeId, edgeKind)
            }
            val artifactNodeId = "artifact:${operation.artifactId.value}"
            node(DocumentationEvolutionGraphNode(artifactNodeId, EvolutionGraphNodeKind.ARTIFACT, operation.artifactId.value))
            edge(
                operationNodeId,
                artifactNodeId,
                if (operation.kind == ReconciliationOperationKind.KEEP ||
                    operation.kind == ReconciliationOperationKind.KEEP_USER_CONTENT
                ) EvolutionGraphEdgeKind.RETAINS else EvolutionGraphEdgeKind.CHANGES,
            )
        }
        request.reconciliationResult?.let { result ->
            val resultNodeId = "applied-result:${result.resultSha256}"
            node(DocumentationEvolutionGraphNode(resultNodeId, EvolutionGraphNodeKind.APPLIED_RESULT, result.resultSha256))
            request.reconciliationPlan?.explanationReport?.explanations.orEmpty().forEach { explanation ->
                val decisionNodeId = "decision:${explanation.decisionId}"
                node(
                    DocumentationEvolutionGraphNode(
                        decisionNodeId,
                        EvolutionGraphNodeKind.USER_DECISION,
                        explanation.subjectId,
                        explanation.evidenceRefs,
                    ),
                )
                explanation.causedOperationIds.forEach { operationId ->
                    edge(decisionNodeId, "reconciliation:$operationId", EvolutionGraphEdgeKind.AUTHORIZES)
                }
            }
            result.appliedOperationIds.forEach { edge("reconciliation:$it", resultNodeId, EvolutionGraphEdgeKind.CHANGES) }
            result.retainedOperationIds.forEach { edge("reconciliation:$it", resultNodeId, EvolutionGraphEdgeKind.RETAINS) }
        }

        val sortedNodes = nodes.values.sortedBy { it.nodeId }
        val sortedEdges = edges.sortedWith(compareBy({ it.sourceNodeId }, { it.targetNodeId }, { it.kind.name }))
        EvolutionGraphVerifier().requireValid(sortedNodes, sortedEdges)
        return DocumentationEvolutionGraph(
            sortedNodes,
            sortedEdges,
            EvolutionCanonicalizer.graphSha256(sortedNodes, sortedEdges),
        )
    }

    private fun graphKind(change: DocumentationEvolutionChange): EvolutionGraphNodeKind = when (change.subjectKind) {
        EvolutionSubjectKind.RELATIONSHIP -> EvolutionGraphNodeKind.RELATIONSHIP_CHANGE
        EvolutionSubjectKind.OWNERSHIP -> EvolutionGraphNodeKind.OWNERSHIP_DECISION
        EvolutionSubjectKind.USER_DECISION -> EvolutionGraphNodeKind.USER_DECISION
        EvolutionSubjectKind.RECONCILIATION_OPERATION -> EvolutionGraphNodeKind.RECONCILIATION_OPERATION
        EvolutionSubjectKind.ARTIFACT -> EvolutionGraphNodeKind.ARTIFACT
        else -> EvolutionGraphNodeKind.SPECIFICATION_CHANGE
    }
}

public class EvolutionGraphVerifier {
    public fun verify(graph: DocumentationEvolutionGraph): Boolean = runCatching {
        requireValid(graph.nodes, graph.edges)
        require(graph.graphSha256 == EvolutionCanonicalizer.graphSha256(graph.nodes, graph.edges))
    }.isSuccess

    internal fun requireValid(
        nodes: List<DocumentationEvolutionGraphNode>,
        edges: List<DocumentationEvolutionGraphEdge>,
    ) {
        require(nodes == nodes.distinct().sortedBy { it.nodeId }) { "Evolution graph nodes must be sorted and unique." }
        require(edges == edges.distinct().sortedWith(compareBy({ it.sourceNodeId }, { it.targetNodeId }, { it.kind.name }))) {
            "Evolution graph edges must be sorted and unique."
        }
        val ids = nodes.mapTo(hashSetOf()) { it.nodeId }
        require(edges.all { it.sourceNodeId in ids && it.targetNodeId in ids }) { "Evolution graph has dangling edge endpoints." }
        require(edges.none { it.sourceNodeId == it.targetNodeId }) { "Evolution graph self edge is not allowed." }
        val outgoing = edges.groupBy { it.sourceNodeId }
        val visiting = hashSetOf<String>()
        val visited = hashSetOf<String>()
        fun visit(id: String) {
            require(visiting.add(id)) { "Evolution graph cycle detected." }
            if (visited.add(id)) outgoing[id].orEmpty().forEach { visit(it.targetNodeId) }
            visiting.remove(id)
        }
        ids.sorted().forEach(::visit)
    }
}
