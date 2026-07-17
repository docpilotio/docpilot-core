package io.docpilot.core.incremental.knowledge

import io.docpilot.core.incremental.ProjectChangeSet
import io.docpilot.core.incremental.ProjectFileChange
import io.docpilot.core.incremental.ProjectFileChangeType
import io.docpilot.core.incremental.SourceFileFingerprint
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
import io.docpilot.core.model.knowledge.RelationshipType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultIncrementalKnowledgeAnalyzerTest {

    @Test
    fun `returns empty impact when only unchanged files exist`() {
        val result = analyzer().analyze(knowledge(), changes(ProjectFileChangeType.UNCHANGED))

        assertFalse(result.hasImpact)
        assertEquals(IncrementalKnowledgeImpact.EMPTY, result)
    }

    @Test
    fun `finds changed file nodes and expands one graph hop`() {
        val result = analyzer().analyze(knowledge(), changes(ProjectFileChangeType.MODIFIED))

        assertEquals(listOf("src/UserRepository.kt"), result.changedRelativePaths)
        assertEquals(
            listOf("class:UserRepository", "file:src/UserRepository.kt"),
            result.directlyAffectedNodeIds,
        )
        assertEquals(
            listOf(
                "class:UserRepository",
                "file:src/UserRepository.kt",
                "interface:UserStore",
            ),
            result.affectedNodeIds,
        )
        assertEquals(
            listOf("edge:declares", "edge:implements"),
            result.affectedEdgeIds,
        )
        assertTrue("evidence:file" in result.affectedEvidenceIds)
        assertTrue("evidence:symbol" in result.affectedEvidenceIds)
    }

    @Test
    fun `zero neighbor depth limits result to directly affected nodes`() {
        val result = DefaultIncrementalKnowledgeAnalyzer(neighborDepth = 0)
            .analyze(knowledge(), changes(ProjectFileChangeType.REMOVED))

        assertEquals(
            listOf("class:UserRepository", "file:src/UserRepository.kt"),
            result.affectedNodeIds,
        )
        assertEquals(
            listOf("edge:declares", "edge:implements"),
            result.affectedEdgeIds,
        )
    }

    private fun analyzer() = DefaultIncrementalKnowledgeAnalyzer()

    private fun changes(type: ProjectFileChangeType): ProjectChangeSet {
        val previous = fingerprint("a")
        val current = fingerprint("b")
        val change = when (type) {
            ProjectFileChangeType.ADDED -> ProjectFileChange(PATH, type, null, current)
            ProjectFileChangeType.REMOVED -> ProjectFileChange(PATH, type, previous, null)
            ProjectFileChangeType.MODIFIED,
            ProjectFileChangeType.UNCHANGED,
            -> ProjectFileChange(PATH, type, previous, current)
        }
        return ProjectChangeSet(listOf(change))
    }

    private fun fingerprint(seed: String) = SourceFileFingerprint(
        relativePath = PATH,
        contentSha256 = seed.repeat(64),
        sizeBytes = 10,
    )

    private fun knowledge(): KnowledgeBuildResult {
        val nodes = listOf(
            KnowledgeNode(
                id = "file:src/UserRepository.kt",
                name = "UserRepository.kt",
                kind = KnowledgeNodeKind.FILE,
                attributes = mapOf("relativePath" to PATH),
                evidenceRefs = setOf("evidence:file"),
            ),
            KnowledgeNode(
                id = "class:UserRepository",
                name = "UserRepository",
                kind = KnowledgeNodeKind.CLASS,
                evidenceRefs = setOf("evidence:symbol"),
            ),
            KnowledgeNode(
                id = "interface:UserStore",
                name = "UserStore",
                kind = KnowledgeNodeKind.INTERFACE,
                evidenceRefs = setOf("evidence:other"),
            ),
            KnowledgeNode(
                id = "class:Unrelated",
                name = "Unrelated",
                kind = KnowledgeNodeKind.CLASS,
                evidenceRefs = setOf("evidence:unrelated"),
            ),
        )
        val edges = listOf(
            KnowledgeEdge(
                id = "edge:declares",
                sourceNodeId = "file:src/UserRepository.kt",
                targetNodeId = "class:UserRepository",
                relationship = RelationshipType.DECLARES,
                evidenceRefs = setOf("evidence:symbol"),
            ),
            KnowledgeEdge(
                id = "edge:implements",
                sourceNodeId = "class:UserRepository",
                targetNodeId = "interface:UserStore",
                relationship = RelationshipType.IMPLEMENTS,
                evidenceRefs = setOf("evidence:symbol"),
            ),
        )
        val evidence = listOf(
            evidence("evidence:file", PATH),
            evidence("evidence:symbol", PATH),
            evidence("evidence:other", "src/UserStore.kt"),
            evidence("evidence:unrelated", "src/Unrelated.kt"),
        )
        return KnowledgeBuildResult(
            graph = KnowledgeGraph(nodes = nodes, edges = edges),
            evidence = EvidenceCollection(evidence),
        )
    }

    private fun evidence(id: String, path: String) = Evidence(
        id = EvidenceId(id),
        type = EvidenceType.SOURCE_FILE,
        location = EvidenceLocation(relativePath = path),
        summary = "Evidence for $path",
    )

    companion object {
        private const val PATH = "src/UserRepository.kt"
    }
}
