package io.docpilot.cli.command

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression test for a bug found while smoke-testing RFC-0083's `generate docs --enrich` logging
 * against a real Ollama model: `canonicalNarrative()` only stripped the `## Evidence`/`## Unresolved`
 * heading *line*, not the section *body*, so raw Unresolved bullet text (including directive-like
 * `requiredAction` phrasing) leaked into the enrichment prompt ahead of the actual narrative and
 * derailed the model into responding to it as if it were a task, instead of writing bounded prose.
 */
class CanonicalNarrativeTest {
    private val workflow = DefaultDocumentationGenerationWorkflow()

    @Test
    fun `excludes the full Unresolved and Evidence section bodies, not just their headings`() {
        val content = """
            # Project Overview

            This project renders todo list screens using Compose.

            ## Unresolved

            - subject: symbol:foo, question: what should this do?, requiredAction: Add a top-level declaration model in a future RFC if renderer support is required.
            - subject: symbol:bar, question: is this dead code?, requiredAction: Investigate usage.

            ## Evidence

            - evidence:SOURCE_FILE:app/Foo.kt

            ## Next Steps

            Consider extracting a repository layer.
        """.trimIndent()

        val narrative = workflow.canonicalNarrative(content)

        assertFalse(narrative.contains("requiredAction"), narrative)
        assertFalse(narrative.contains("Add a top-level declaration model"), narrative)
        assertFalse(narrative.contains("evidence:SOURCE_FILE"), narrative)
        assertTrue(narrative.contains("renders todo list screens"), narrative)
        assertTrue(narrative.contains("Consider extracting a repository layer"), narrative)
    }

    @Test
    fun `handles content with no Evidence or Unresolved sections unchanged`() {
        val content = "# Title\n\nPlain narrative body.\n"

        val narrative = workflow.canonicalNarrative(content)

        assertTrue(narrative.contains("Plain narrative body."), narrative)
    }
}
