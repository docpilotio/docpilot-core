package io.docpilot.core.incremental.generation

import io.docpilot.core.generator.architecture.plan.ArchitectureSectionId
import io.docpilot.core.incremental.prompt.PromptOutputContract
import io.docpilot.core.incremental.prompt.PromptOutputFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GeneratedSectionNormalizerTest {
    private val contract = PromptOutputContract(
        PromptOutputFormat.MARKDOWN_SECTION,
        ArchitectureSectionId("dependencies-and-integrations"),
        includeHeading = true,
        allowAdditionalSections = false,
    )

    @Test
    fun `unwraps markdown fence and normalizes trailing newline`() {
        val result = DefaultGeneratedSectionNormalizer().normalize(
            "```markdown\n## Dependencies and Integrations\nUpdated\n```",
            "Dependencies and Integrations",
            contract,
        )
        assertEquals("## Dependencies and Integrations\nUpdated\n", result.markdown)
    }

    @Test
    fun `rejects additional same-level section`() {
        assertFailsWith<IllegalArgumentException> {
            DefaultGeneratedSectionNormalizer().normalize(
                "## Dependencies and Integrations\nUpdated\n\n## Executive Summary\nUnexpected",
                "Dependencies and Integrations",
                contract,
            )
        }
    }
}
