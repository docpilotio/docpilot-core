package io.docpilot.cli.command

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression test for RFC-0085: markdownItems() fed full descriptive Evidence/Unresolved bullets
 * (summary, type, confidence, file, line, symbol) into the --enrich AI prompt as "evidenceRefs"/
 * "unresolvedRefs", instead of the short stable-ID references those fields are named and treated as.
 */
class MarkdownItemsTest {
    private val workflow = DefaultDocumentationGenerationWorkflow()

    @Test
    fun `extracts only the leading stable ID token, not the full bullet text`() {
        val content = """
            # Title

            ## Evidence

            - `evidence:9f8a3b2c` — Type declaration; SOURCE_SYMBOL/HIGH; `app/Foo.kt`, lines 3-12, symbol `Foo`
            - `evidence:aa11bb22` — API declaration; SOURCE_SYMBOL/HIGH; `app/Foo.kt`, lines 20, symbol `bar(): Unit`

            ## Unresolved
        """.trimIndent()

        val items = workflow.markdownItems(content, "Evidence")

        assertEquals(listOf("evidence:9f8a3b2c", "evidence:aa11bb22"), items)
    }

    @Test
    fun `treats a None bullet as absent, not a stable ID`() {
        val content = """
            # Title

            ## Evidence

            - None
        """.trimIndent()

        assertEquals(emptyList(), workflow.markdownItems(content, "Evidence"))
    }

    @Test
    fun `deduplicates repeated stable IDs and caps at 50`() {
        val bullets = (1..60).joinToString("\n") { "- `evidence:id$it` — summary $it" } +
            "\n- `evidence:id1` — duplicate of the first"
        val content = "## Evidence\n\n$bullets"

        val items = workflow.markdownItems(content, "Evidence")

        assertEquals(50, items.size)
        assertEquals("evidence:id1", items.first())
    }

    @Test
    fun `returns empty when the heading is absent`() {
        assertEquals(emptyList(), workflow.markdownItems("# Title\n\nNo sections here.", "Evidence"))
    }
}
