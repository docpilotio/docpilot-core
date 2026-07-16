package io.docpilot.core.model.knowledge

data class KnowledgeGraph(
    val nodes: List<KnowledgeNode>,
    val edges: List<KnowledgeEdge>,
    val unresolved: List<KnowledgeUnresolvedItem> = emptyList(),
) {
    init {
        require(nodes.map { it.id }.distinct().size == nodes.size) {
            "Knowledge node IDs must be unique."
        }
        require(edges.map { it.id }.distinct().size == edges.size) {
            "Knowledge edge IDs must be unique."
        }

        val nodeIds = nodes.mapTo(mutableSetOf()) { it.id }
        require(edges.all {
            it.sourceNodeId in nodeIds && it.targetNodeId in nodeIds
        }) {
            "Every knowledge edge endpoint must reference an existing node."
        }
    }

    val nodeCount: Int get() = nodes.size
    val edgeCount: Int get() = edges.size

    fun node(id: String): KnowledgeNode? =
        nodes.firstOrNull { it.id == id }
}

data class KnowledgeUnresolvedItem(
    val id: String,
    val subjectNodeId: String?,
    val question: String,
    val reason: String,
) {
    init {
        require(id.isNotBlank()) { "Unresolved item ID must not be blank." }
        require(subjectNodeId == null || subjectNodeId.isNotBlank()) {
            "subjectNodeId must be null or non-blank."
        }
        require(question.isNotBlank()) { "Question must not be blank." }
        require(reason.isNotBlank()) { "Reason must not be blank." }
    }
}
