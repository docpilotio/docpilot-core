package io.docpilot.core.incremental.planning

import io.docpilot.core.generator.architecture.plan.ArchitectureGenerationPlan
import io.docpilot.core.generator.architecture.plan.ArchitectureSection
import io.docpilot.core.generator.architecture.plan.ArchitectureSectionId
import io.docpilot.core.incremental.ProjectChangeSet
import io.docpilot.core.incremental.ProjectFileChange
import io.docpilot.core.incremental.ProjectFileChangeType
import io.docpilot.core.incremental.SourceFileFingerprint
import io.docpilot.core.incremental.knowledge.IncrementalKnowledgeImpact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultIncrementalGenerationPlannerTest {
    private val planner = DefaultIncrementalGenerationPlanner()
    private val architecturePlan = ArchitectureGenerationPlan(
        listOf(
            section("executive-summary", 1),
            section("system-context", 2),
            section("components-and-responsibilities", 3),
            section("data-and-control-flow", 4),
            section("dependencies-and-integrations", 5),
            section("quality-attributes-and-constraints", 6),
            section("risks-and-recommendations", 7),
        ),
    )

    @Test
    fun `plans repository change in dependency order and refreshes summary last`() {
        val path = "src/main/kotlin/sample/UserRepository.kt"
        val plan = planner.plan(
            architecturePlan = architecturePlan,
            changes = changed(path),
            impact = impact(path, "class:sample.UserRepository"),
            constraints = PlanningConstraints(totalContextTokens = 4_096),
        )

        val actual = plan.sectionIds.map { it.value }
        assertEquals(
            listOf(
                "components-and-responsibilities",
                "data-and-control-flow",
                "executive-summary",
            ),
            actual,
            "Actual plan: $actual"
        )
        assertEquals(
            listOf("components-and-responsibilities"),
            plan.jobs[1].dependencies.map { it.value },
        )
        assertEquals(
            listOf(
                "components-and-responsibilities",
                "data-and-control-flow",
            ),
            plan.jobs.last().dependencies.map { it.value },
        )
        assertTrue(plan.jobs.first().priority == GenerationPriority.HIGH)
        assertTrue(plan.jobs.sumOf { it.contextTokenBudget } <= 4_096)
    }

    @Test
    fun `plans dependency metadata change without invoking provider concerns`() {
        val path = "build.gradle.kts"
        val plan = planner.plan(
            architecturePlan = architecturePlan,
            changes = changed(path),
            impact = impact(path, "project:sample"),
            constraints = PlanningConstraints(totalContextTokens = 2_048),
        )

        val actual = plan.sectionIds.map { it.value }
        assertEquals(
            listOf("dependencies-and-integrations", "executive-summary"),
            actual,
            "Actual plan: $actual"
        )
        assertFalse(plan.isEmpty)
        assertTrue(plan.jobs.all { it.contextTokenBudget >= 256 })
    }

    @Test
    fun `returns empty plan when there is no effective change`() {
        val plan = planner.plan(
            architecturePlan = architecturePlan,
            changes = ProjectChangeSet(emptyList()),
            impact = IncrementalKnowledgeImpact.EMPTY,
            constraints = PlanningConstraints(),
        )

        assertTrue(plan.isEmpty)
        assertEquals(0, plan.totalContextTokenBudget)
    }

    private fun section(id: String, order: Int) = ArchitectureSection(
        id = ArchitectureSectionId(id),
        title = id,
        instruction = "Generate $id.",
        order = order,
        maxOutputTokens = 768,
    )

    private fun changed(path: String): ProjectChangeSet {
        val previous = fingerprint(path, 'a')
        val current = fingerprint(path, 'b')
        return ProjectChangeSet(
            listOf(
                ProjectFileChange(
                    relativePath = path,
                    type = ProjectFileChangeType.MODIFIED,
                    previous = previous,
                    current = current,
                ),
            ),
        )
    }

    private fun impact(path: String, nodeId: String) = IncrementalKnowledgeImpact(
        changedRelativePaths = listOf(path),
        directlyAffectedNodeIds = listOf(nodeId),
        affectedNodeIds = listOf(nodeId),
        affectedEdgeIds = emptyList(),
        affectedEvidenceIds = listOf("evidence-1"),
    )

    private fun fingerprint(path: String, digit: Char) = SourceFileFingerprint(
        relativePath = path,
        contentSha256 = digit.toString().repeat(64),
        sizeBytes = 10,
    )
}
