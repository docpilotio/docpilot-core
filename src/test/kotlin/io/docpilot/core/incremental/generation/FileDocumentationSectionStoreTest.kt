package io.docpilot.core.incremental.generation

import io.docpilot.core.generator.architecture.plan.ArchitectureSectionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileDocumentationSectionStoreTest {
    @Test
    fun `replaces only requested section`() {
        val original = """# Architecture

## System Context
Keep this.

## Dependencies and Integrations
Old.

## Risks and Recommendations
Keep this too.
"""
        val merged = FileDocumentationSectionStore().replace(
            original,
            listOf(
                GeneratedSection(
                    ArchitectureSectionId("dependencies-and-integrations"),
                    "Dependencies and Integrations",
                    "## Dependencies and Integrations\nNew.\n",
                ),
            ),
        )
        assertTrue("Keep this." in merged)
        assertTrue("Keep this too." in merged)
        assertTrue("New." in merged)
        assertTrue("Old." !in merged)
        assertEquals(1, Regex("## Dependencies and Integrations").findAll(merged).count())
    }
}
