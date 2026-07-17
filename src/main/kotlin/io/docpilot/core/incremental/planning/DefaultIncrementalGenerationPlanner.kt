package io.docpilot.core.incremental.planning

import io.docpilot.core.generator.architecture.plan.ArchitectureGenerationPlan
import io.docpilot.core.generator.architecture.plan.ArchitectureSection
import io.docpilot.core.generator.architecture.plan.ArchitectureSectionId
import io.docpilot.core.incremental.ProjectChangeSet
import io.docpilot.core.incremental.ProjectFileChangeType
import io.docpilot.core.incremental.knowledge.IncrementalKnowledgeImpact
import kotlin.math.min

/**
 * Deterministic AI planning engine for incremental architecture generation.
 *
 * The planner remains provider-independent: it selects sections, calculates
 * dependencies and distributes context tokens, but does not render prompts or
 * invoke an AI provider.
 */
class DefaultIncrementalGenerationPlanner : IncrementalGenerationPlanner {

    override fun plan(
        architecturePlan: ArchitectureGenerationPlan,
        changes: ProjectChangeSet,
        impact: IncrementalKnowledgeImpact,
        constraints: PlanningConstraints,
    ): IncrementalGenerationPlan {
        if (!changes.hasChanges || !impact.hasImpact) {
            return IncrementalGenerationPlan.EMPTY
        }

        val selected = selectSections(architecturePlan.sections, changes, impact)
        if (selected.isEmpty()) return IncrementalGenerationPlan.EMPTY

        val dependencies = dependenciesFor(selected)
        val ordered = topologicalOrder(selected, dependencies)
        val priorities = ordered.associateWith { priorityFor(it, changes, impact) }
        val budgets = allocateBudgets(ordered, priorities, constraints)
        val directIds = impact.directlyAffectedNodeIds.toSet()

        val jobs = ordered.map { section ->
            val reasonSet = buildSet {
                add(GenerationReason.SOURCE_CHANGED)
                if (impact.affectedNodeIds.isNotEmpty()) add(GenerationReason.KNOWLEDGE_UPDATED)
                if (dependencies.getValue(section.id).isNotEmpty() &&
                    score(section, changes, impact) == 0
                ) add(GenerationReason.DEPENDENCY_REQUIRED)
            }
            val affectedNodes = impact.affectedNodeIds
                .sortedWith(compareByDescending<String> { it in directIds }.thenBy { it })
                .distinct()
                .sorted()

            GenerationJob(
                section = section,
                priority = priorities.getValue(section),
                reasons = reasonSet,
                affectedNodeIds = affectedNodes,
                affectedEvidenceIds = impact.affectedEvidenceIds,
                dependencies = dependencies.getValue(section.id),
                contextTokenBudget = budgets.getValue(section.id),
            )
        }

        return IncrementalGenerationPlan(
            jobs = jobs,
            totalContextTokenBudget = constraints.totalContextTokens,
        )
    }

    private fun selectSections(
        sections: List<ArchitectureSection>,
        changes: ProjectChangeSet,
        impact: IncrementalKnowledgeImpact,
    ): Set<ArchitectureSection> {
        val scored = sections.associateWith { score(it, changes, impact) }
        val matched = scored.filterValues { it > 0 }.keys.toMutableSet()

        if (matched.isEmpty()) {
            sections.firstOrNull { it.id.value == COMPONENTS }?.let(matched::add)
        }

        // A summary must be refreshed whenever any architecture section changes.
        sections.firstOrNull { it.id.value == SUMMARY }?.let(matched::add)
        return matched
    }

    private fun score(
        section: ArchitectureSection,
        changes: ProjectChangeSet,
        impact: IncrementalKnowledgeImpact,
    ): Int {
        val corpus = buildString {
            changes.changes
                .filter { it.type != ProjectFileChangeType.UNCHANGED }
                .forEach { append(' ').append(it.relativePath.lowercase()) }
            impact.affectedNodeIds.forEach { append(' ').append(it.lowercase()) }
        }
        val keywords = SECTION_KEYWORDS[section.id.value].orEmpty()
        return keywords.count(corpus::contains)
    }

    private fun dependenciesFor(
        selected: Set<ArchitectureSection>,
    ): Map<ArchitectureSectionId, List<ArchitectureSectionId>> {
        val selectedIds = selected.mapTo(mutableSetOf()) { it.id }
        val byValue = selected.associateBy { it.id.value }

        return selected.associate { section ->
            val dependencyValues = when (section.id.value) {
                COMPONENTS -> listOf(CONTEXT)
                DATA_FLOW -> listOf(COMPONENTS)
                DEPENDENCIES -> listOf(COMPONENTS)
                QUALITY -> listOf(COMPONENTS)
                RISKS -> listOf(QUALITY, DEPENDENCIES, DATA_FLOW, COMPONENTS, CONTEXT)
                SUMMARY -> selected
                    .map { it.id.value }
                    .filterNot { it == SUMMARY }
                else -> emptyList()
            }
            section.id to dependencyValues
                .mapNotNull(byValue::get)
                .map { it.id }
                .filter(selectedIds::contains)
                .sortedBy { byValue.getValue(it.value).order }
        }
    }

    private fun topologicalOrder(
        sections: Set<ArchitectureSection>,
        dependencies: Map<ArchitectureSectionId, List<ArchitectureSectionId>>,
    ): List<ArchitectureSection> {
        val remaining = sections.toMutableSet()
        val result = mutableListOf<ArchitectureSection>()

        while (remaining.isNotEmpty()) {
            val ready = remaining
                .filter { section -> dependencies.getValue(section.id).all { it in result.map(ArchitectureSection::id) } }
                .sortedWith(compareByDescending<ArchitectureSection> { priorityRank(it.id.value) }.thenBy { it.order })
            check(ready.isNotEmpty()) { "Architecture section dependency cycle detected." }
            ready.forEach {
                result += it
                remaining -= it
            }
        }
        return result
    }

    private fun priorityFor(
        section: ArchitectureSection,
        changes: ProjectChangeSet,
        impact: IncrementalKnowledgeImpact,
    ): GenerationPriority {
        val score = score(section, changes, impact)
        return when {
            section.id.value == SUMMARY -> GenerationPriority.LOW
            score >= 2 || section.id.value in HIGH_PRIORITY_SECTIONS -> GenerationPriority.HIGH
            score == 1 -> GenerationPriority.MEDIUM
            else -> GenerationPriority.LOW
        }
    }

    private fun allocateBudgets(
        sections: List<ArchitectureSection>,
        priorities: Map<ArchitectureSection, GenerationPriority>,
        constraints: PlanningConstraints,
    ): Map<ArchitectureSectionId, Int> {
        require(
            constraints.totalContextTokens >=
                sections.size * constraints.minimumContextTokensPerJob,
        ) {
            "totalContextTokens is too small for ${sections.size} generation jobs."
        }

        val budgets = sections.associateWith {
            constraints.minimumContextTokensPerJob
        }.toMutableMap()
        var remaining = constraints.totalContextTokens - budgets.values.sum()

        val weighted = sections.sortedWith(
            compareByDescending<ArchitectureSection> { priorities.getValue(it).weight }
                .thenBy { it.order },
        )
        while (remaining > 0) {
            var allocated = false
            for (section in weighted) {
                val current = budgets.getValue(section)
                val desiredMaximum = min(
                    constraints.maximumContextTokensPerJob,
                    section.maxOutputTokens * 2,
                )
                if (current < desiredMaximum) {
                    val addition = min(remaining, min(128, desiredMaximum - current))
                    budgets[section] = current + addition
                    remaining -= addition
                    allocated = true
                    if (remaining == 0) break
                }
            }
            if (!allocated) break
        }
        return budgets.mapKeys { it.key.id }
    }

    private fun priorityRank(sectionId: String): Int = when (sectionId) {
        CONTEXT -> 6
        COMPONENTS -> 5
        DATA_FLOW -> 4
        DEPENDENCIES -> 3
        QUALITY -> 2
        RISKS -> 1
        SUMMARY -> 0
        else -> 0
    }

    companion object {
        private const val SUMMARY = "executive-summary"
        private const val CONTEXT = "system-context"
        private const val COMPONENTS = "components-and-responsibilities"
        private const val DATA_FLOW = "data-and-control-flow"
        private const val DEPENDENCIES = "dependencies-and-integrations"
        private const val QUALITY = "quality-attributes-and-constraints"
        private const val RISKS = "risks-and-recommendations"

        private val HIGH_PRIORITY_SECTIONS = setOf(COMPONENTS, DATA_FLOW, DEPENDENCIES)
        private val SECTION_KEYWORDS = mapOf(
            CONTEXT to listOf("manifest", "application", "module", "project", "main", "cli"),
            COMPONENTS to listOf("class", "interface", "service", "controller", "manager", "module", "repository"),
            DATA_FLOW to listOf("flow", "state", "event", "usecase", "use-case", "repository", "database", "dao"),
            DEPENDENCIES to listOf("build.gradle", "settings.gradle", "libs.versions", "pom.xml", "dependency", "provider", "plugin"),
            QUALITY to listOf("test", "security", "performance", "error", "validation", "config"),
            RISKS to listOf("deprecated", "legacy", "unsafe", "todo", "fixme"),
        )
    }
}
