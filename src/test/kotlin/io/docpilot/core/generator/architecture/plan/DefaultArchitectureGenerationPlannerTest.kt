package io.docpilot.core.generator.architecture.plan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DefaultArchitectureGenerationPlannerTest {

    @Test
    fun `default sections are deterministic and ordered`() {
        val sections =
            DefaultArchitectureGenerationPlanner.DEFAULT_SECTIONS

        assertEquals(7, sections.size)
        assertEquals(
            sections.sortedBy { it.order },
            sections,
        )
        assertEquals(
            sections.size,
            sections.map { it.id }.distinct().size,
        )
        assertEquals(
            sections.size,
            sections.map { it.order }.distinct().size,
        )
        assertTrue(sections.all { it.maxOutputTokens > 0 })
    }

    @Test
    fun `plan rejects duplicate section ids`() {
        val section =
            DefaultArchitectureGenerationPlanner.DEFAULT_SECTIONS.first()

        assertFailsWith<IllegalArgumentException> {
            ArchitectureGenerationPlan(
                sections = listOf(
                    section,
                    section.copy(order = section.order + 1),
                ),
            )
        }
    }

    @Test
    fun `plan rejects unsorted sections`() {
        val sections =
            DefaultArchitectureGenerationPlanner.DEFAULT_SECTIONS
                .take(2)
                .reversed()

        assertFailsWith<IllegalArgumentException> {
            ArchitectureGenerationPlan(sections)
        }
    }

    @Test
    fun `total token budget is the section sum`() {
        val sections =
            DefaultArchitectureGenerationPlanner.DEFAULT_SECTIONS
        val plan = ArchitectureGenerationPlan(sections)

        assertEquals(
            sections.sumOf { it.maxOutputTokens },
            plan.totalMaxOutputTokens,
        )
    }
}
