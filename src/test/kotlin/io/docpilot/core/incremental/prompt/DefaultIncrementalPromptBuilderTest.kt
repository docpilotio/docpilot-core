package io.docpilot.core.incremental.prompt

import io.docpilot.core.generator.architecture.plan.ArchitectureSection
import io.docpilot.core.generator.architecture.plan.ArchitectureSectionId
import io.docpilot.core.incremental.ProjectChangeSet
import io.docpilot.core.incremental.ProjectFileChange
import io.docpilot.core.incremental.ProjectFileChangeType
import io.docpilot.core.incremental.SourceFileFingerprint
import io.docpilot.core.incremental.planning.GenerationJob
import io.docpilot.core.incremental.planning.GenerationPriority
import io.docpilot.core.incremental.planning.GenerationReason
import io.docpilot.core.model.evidence.Evidence
import io.docpilot.core.model.evidence.EvidenceCollection
import io.docpilot.core.model.evidence.EvidenceId
import io.docpilot.core.model.evidence.EvidenceLocation
import io.docpilot.core.model.evidence.EvidenceType
import io.docpilot.core.model.knowledge.KnowledgeBuildResult
import io.docpilot.core.model.knowledge.KnowledgeGraph
import io.docpilot.core.model.knowledge.KnowledgeNode
import io.docpilot.core.model.knowledge.KnowledgeNodeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultIncrementalPromptBuilderTest {
    private val builder = DefaultIncrementalPromptBuilder()

    @Test
    fun `builds provider-neutral prompt for a new section`() {
        val request = request()

        val plan = builder.build(request)

        assertTrue(plan.taskInstruction.startsWith("Create only"))
        assertEquals("components-and-responsibilities", plan.outputContract.sectionId.value)
        assertEquals(PromptOutputFormat.MARKDOWN_SECTION, plan.outputContract.format)
        assertTrue(plan.outputContract.includeHeading)
        assertFalse(plan.outputContract.allowAdditionalSections)
        assertEquals(listOf("class:sample.UserRepository"), plan.context.affectedKnowledge.map { it.id })
        assertEquals(listOf("evidence-user-repository"), plan.context.evidence.map { it.id })
        assertTrue(plan.estimatedInputTokens <= plan.inputTokenBudget)
    }

    @Test
    fun `is deterministic regardless of graph and evidence input order`() {
        val first = request(
            knowledge = knowledge(
                nodes = listOf(repositoryNode(), helperNode()),
                evidence = listOf(repositoryEvidence(), helperEvidence()),
            ),
            affectedNodeIds = listOf("class:sample.Helper", "class:sample.UserRepository"),
            affectedEvidenceIds = listOf("evidence-helper", "evidence-user-repository"),
        )
        val second = request(
            knowledge = knowledge(
                nodes = listOf(helperNode(), repositoryNode()),
                evidence = listOf(helperEvidence(), repositoryEvidence()),
            ),
            affectedNodeIds = listOf("class:sample.Helper", "class:sample.UserRepository"),
            affectedEvidenceIds = listOf("evidence-helper", "evidence-user-repository"),
        )

        assertEquals(builder.build(first), builder.build(second))
    }

    @Test
    fun `excludes evidence unrelated to affected nodes and changed files`() {
        val plan = builder.build(
            request(
                knowledge = knowledge(
                    nodes = listOf(repositoryNode()),
                    evidence = listOf(repositoryEvidence(), unrelatedEvidence()),
                ),
            ),
        )

        assertEquals(listOf("evidence-user-repository"), plan.context.evidence.map { it.id })
    }

    @Test
    fun `updates existing section and bounds previous content`() {
        val previous = "Existing architecture content. ".repeat(600)
        val plan = builder.build(
            request(
                contextTokenBudget = 512,
                previousSectionContent = previous,
            ),
        )

        assertTrue(plan.taskInstruction.startsWith("Update only"))
        assertTrue(plan.context.previousSectionContent != null)
        assertTrue(plan.context.previousSectionTruncated)
        assertTrue(
            plan.warnings.any {
                it.code == PromptBuildWarningCode.PREVIOUS_SECTION_TRUNCATED
            },
        )
        assertTrue(plan.estimatedInputTokens <= 512)
    }

    @Test
    fun `builds deletion prompt when current graph no longer has affected data`() {
        val path = "src/main/kotlin/sample/RemovedRepository.kt"
        val plan = builder.build(
            request(
                path = path,
                changeType = ProjectFileChangeType.REMOVED,
                knowledge = knowledge(emptyList(), emptyList()),
                affectedNodeIds = listOf("class:sample.RemovedRepository"),
                affectedEvidenceIds = listOf("evidence-removed"),
            ),
        )

        assertEquals(listOf(path), plan.context.changedFiles.map { it.relativePath })
        assertTrue(plan.context.affectedKnowledge.isEmpty())
        assertTrue(plan.context.evidence.isEmpty())
        assertTrue(
            plan.warnings.any {
                it.code == PromptBuildWarningCode.MISSING_KNOWLEDGE_NODE
            },
        )
        assertTrue(
            plan.warnings.any {
                it.code == PromptBuildWarningCode.MISSING_EVIDENCE
            },
        )
    }

    @Test
    fun `adds dependency-specific accuracy constraints`() {
        val plan = builder.build(
            request(
                sectionId = "dependencies-and-integrations",
                sectionTitle = "Dependencies and Integrations",
                path = "build.gradle.kts",
                contextTokenBudget = 768,
            ),
        )

        assertTrue(plan.constraints.any { it.id == "dependency-accuracy" })
        assertTrue(plan.constraints.any { it.id == "direct-vs-transitive" })
    }

    @Test
    fun `rejects a budget smaller than fixed prompt requirements`() {
        assertFailsWith<PromptBuildException.InsufficientTokenBudget> {
            builder.build(request(contextTokenBudget = 64))
        }
    }

    private fun request(
        sectionId: String = "components-and-responsibilities",
        sectionTitle: String = "Components and Responsibilities",
        path: String = "src/main/kotlin/sample/UserRepository.kt",
        changeType: ProjectFileChangeType = ProjectFileChangeType.MODIFIED,
        knowledge: KnowledgeBuildResult = knowledge(
            nodes = listOf(repositoryNode()),
            evidence = listOf(repositoryEvidence(), unrelatedEvidence()),
        ),
        affectedNodeIds: List<String> = listOf("class:sample.UserRepository"),
        affectedEvidenceIds: List<String> = listOf("evidence-user-repository"),
        contextTokenBudget: Int = 1_024,
        previousSectionContent: String? = null,
    ): PromptBuildRequest = PromptBuildRequest(
        job = GenerationJob(
            section = ArchitectureSection(
                id = ArchitectureSectionId(sectionId),
                title = sectionTitle,
                instruction = "Describe the selected architecture section.",
                order = 3,
                maxOutputTokens = 768,
            ),
            priority = GenerationPriority.HIGH,
            reasons = setOf(
                GenerationReason.SOURCE_CHANGED,
                GenerationReason.KNOWLEDGE_UPDATED,
            ),
            affectedNodeIds = affectedNodeIds.sorted(),
            affectedEvidenceIds = affectedEvidenceIds.sorted(),
            dependencies = emptyList(),
            contextTokenBudget = contextTokenBudget,
        ),
        knowledge = knowledge,
        changes = changeSet(path, changeType),
        previousSectionContent = previousSectionContent,
    )

    private fun knowledge(
        nodes: List<KnowledgeNode>,
        evidence: List<Evidence>,
    ): KnowledgeBuildResult = KnowledgeBuildResult(
        graph = KnowledgeGraph(nodes = nodes, edges = emptyList()),
        evidence = EvidenceCollection(evidence),
    )

    private fun repositoryNode() = KnowledgeNode(
        id = "class:sample.UserRepository",
        name = "UserRepository",
        kind = KnowledgeNodeKind.CLASS,
        attributes = mapOf("visibility" to "PUBLIC"),
        evidenceRefs = setOf("evidence-user-repository"),
    )

    private fun helperNode() = KnowledgeNode(
        id = "class:sample.Helper",
        name = "Helper",
        kind = KnowledgeNodeKind.CLASS,
        evidenceRefs = setOf("evidence-helper"),
    )

    private fun repositoryEvidence() = Evidence(
        id = EvidenceId("evidence-user-repository"),
        type = EvidenceType.SYMBOL_DECLARATION,
        location = EvidenceLocation(
            relativePath = "src/main/kotlin/sample/UserRepository.kt",
            lineStart = 12,
        ),
        summary = "CLASS UserRepository is declared.",
        attributes = mapOf("symbolName" to "UserRepository"),
    )

    private fun helperEvidence() = Evidence(
        id = EvidenceId("evidence-helper"),
        type = EvidenceType.SYMBOL_DECLARATION,
        location = EvidenceLocation(
            relativePath = "src/main/kotlin/sample/Helper.kt",
            lineStart = 4,
        ),
        summary = "CLASS Helper is declared.",
        attributes = mapOf("symbolName" to "Helper"),
    )

    private fun unrelatedEvidence() = Evidence(
        id = EvidenceId("evidence-unrelated"),
        type = EvidenceType.SOURCE_FILE,
        location = EvidenceLocation("src/main/kotlin/sample/Unrelated.kt"),
        summary = "An unrelated source file was indexed.",
    )

    private fun changeSet(
        path: String,
        type: ProjectFileChangeType,
    ): ProjectChangeSet {
        val previous = if (type == ProjectFileChangeType.ADDED) null else fingerprint(path, 'a')
        val current = if (type == ProjectFileChangeType.REMOVED) null else fingerprint(path, 'b')
        return ProjectChangeSet(
            listOf(
                ProjectFileChange(
                    relativePath = path,
                    type = type,
                    previous = previous,
                    current = current,
                ),
            ),
        )
    }

    private fun fingerprint(path: String, digit: Char) = SourceFileFingerprint(
        relativePath = path,
        contentSha256 = digit.toString().repeat(64),
        sizeBytes = 100,
    )
}
