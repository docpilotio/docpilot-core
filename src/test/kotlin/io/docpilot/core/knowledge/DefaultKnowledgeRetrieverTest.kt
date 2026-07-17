package io.docpilot.core.knowledge

import io.docpilot.core.model.evidence.Evidence
import io.docpilot.core.model.evidence.EvidenceCollection
import io.docpilot.core.model.evidence.EvidenceId
import io.docpilot.core.model.evidence.EvidenceLocation
import io.docpilot.core.model.evidence.EvidenceType
import io.docpilot.core.model.knowledge.KnowledgeBuildResult
import io.docpilot.core.model.knowledge.KnowledgeEdge
import io.docpilot.core.model.knowledge.KnowledgeGraph
import io.docpilot.core.model.knowledge.KnowledgeNode
import io.docpilot.core.model.knowledge.KnowledgeNodeKind
import io.docpilot.core.model.knowledge.KnowledgeQuery
import io.docpilot.core.model.knowledge.RelationshipType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DefaultKnowledgeRetrieverTest {

    private val retriever = DefaultKnowledgeRetriever()

    @Test
    fun `finds symbols by case insensitive name and includes evidence`() {
        val result = retriever.retrieve(
            knowledge = knowledge(),
            query = KnowledgeQuery(name = "repository"),
        )

        assertEquals(
            listOf("symbol:TaskRepository"),
            result.nodes.map { it.id },
        )
        assertEquals(
            listOf("edge:file-repository"),
            result.edges.map { it.id },
        )
        assertEquals(
            listOf("evidence:repository"),
            result.evidence.map { it.id.value },
        )
    }

    @Test
    fun `filters by kind and relative path`() {
        val result = retriever.retrieve(
            knowledge = knowledge(),
            query = KnowledgeQuery(
                kind = KnowledgeNodeKind.CLASS,
                relativePath = "feature/tasks",
            ),
        )

        assertEquals(
            listOf("symbol:TasksViewModel"),
            result.nodes.map { it.id },
        )
    }

    @Test
    fun `applies limit after stable ID ordering`() {
        val result = retriever.retrieve(
            knowledge = knowledge(),
            query = KnowledgeQuery(
                kind = KnowledgeNodeKind.CLASS,
                limit = 1,
            ),
        )

        assertEquals(
            listOf("symbol:TaskRepository"),
            result.nodes.map { it.id },
        )
    }

    @Test
    fun `returns stable ordering regardless of graph input ordering`() {
        val first = retriever.retrieve(
            knowledge = knowledge(reverse = false),
            query = KnowledgeQuery(kind = KnowledgeNodeKind.CLASS),
        )
        val second = retriever.retrieve(
            knowledge = knowledge(reverse = true),
            query = KnowledgeQuery(kind = KnowledgeNodeKind.CLASS),
        )

        assertEquals(
            first.nodes.map { it.id },
            second.nodes.map { it.id },
        )
        assertEquals(
            first.edges.map { it.id },
            second.edges.map { it.id },
        )
        assertEquals(
            first.evidence.map { it.id },
            second.evidence.map { it.id },
        )
    }

    @Test
    fun `rejects an empty query and nonpositive limit`() {
        assertFailsWith<IllegalArgumentException> {
            KnowledgeQuery()
        }
        assertFailsWith<IllegalArgumentException> {
            KnowledgeQuery(name = "task", limit = 0)
        }
    }

    private fun knowledge(
        reverse: Boolean = false,
    ): KnowledgeBuildResult {
        val repositoryEvidence = evidence(
            id = "evidence:repository",
            path = "core/data/TaskRepository.kt",
        )
        val viewModelEvidence = evidence(
            id = "evidence:view-model",
            path = "feature/tasks/TasksViewModel.kt",
        )
        val fileEvidence = evidence(
            id = "evidence:file",
            path = "core/data/TaskRepository.kt",
        )

        val repository = KnowledgeNode(
            id = "symbol:TaskRepository",
            name = "TaskRepository",
            kind = KnowledgeNodeKind.CLASS,
            evidenceRefs = setOf(repositoryEvidence.id.value),
        )
        val viewModel = KnowledgeNode(
            id = "symbol:TasksViewModel",
            name = "TasksViewModel",
            kind = KnowledgeNodeKind.CLASS,
            evidenceRefs = setOf(viewModelEvidence.id.value),
        )
        val file = KnowledgeNode(
            id = "file:TaskRepository.kt",
            name = "TaskRepository.kt",
            kind = KnowledgeNodeKind.FILE,
            attributes = mapOf(
                "relativePath" to "core/data/TaskRepository.kt",
            ),
            evidenceRefs = setOf(fileEvidence.id.value),
        )

        val repositoryEdge = KnowledgeEdge(
            id = "edge:file-repository",
            sourceNodeId = file.id,
            targetNodeId = repository.id,
            relationship = RelationshipType.DECLARES,
            evidenceRefs = setOf(repositoryEvidence.id.value),
        )
        val dependencyEdge = KnowledgeEdge(
            id = "edge:view-model-repository",
            sourceNodeId = viewModel.id,
            targetNodeId = repository.id,
            relationship = RelationshipType.DEPENDS_ON,
            evidenceRefs = setOf(
                viewModelEvidence.id.value,
                repositoryEvidence.id.value,
            ),
        )

        val nodes = listOf(viewModel, repository, file)
        val edges = listOf(dependencyEdge, repositoryEdge)
        val evidence = listOf(
            viewModelEvidence,
            repositoryEvidence,
            fileEvidence,
        )

        return KnowledgeBuildResult(
            graph = KnowledgeGraph(
                nodes = if (reverse) nodes.reversed() else nodes,
                edges = if (reverse) edges.reversed() else edges,
            ),
            evidence = EvidenceCollection(
                items = if (reverse) evidence.reversed() else evidence,
            ),
        )
    }

    private fun evidence(
        id: String,
        path: String,
    ): Evidence =
        Evidence(
            id = EvidenceId(id),
            type = EvidenceType.SYMBOL_DECLARATION,
            location = EvidenceLocation(relativePath = path),
            summary = "Evidence for $id",
        )
}
