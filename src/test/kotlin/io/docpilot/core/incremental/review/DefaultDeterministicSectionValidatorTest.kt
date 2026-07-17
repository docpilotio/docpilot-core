package io.docpilot.core.incremental.review

import io.docpilot.core.generator.architecture.plan.ArchitectureSectionId
import io.docpilot.core.incremental.generation.GeneratedSection
import io.docpilot.core.incremental.prompt.PromptContext
import io.docpilot.core.incremental.prompt.PromptEvidence
import io.docpilot.core.incremental.prompt.PromptOutputContract
import io.docpilot.core.incremental.prompt.PromptOutputFormat
import io.docpilot.core.incremental.prompt.PromptPlan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultDeterministicSectionValidatorTest {
    @Test
    fun `rejects placeholder content`() {
        val issues = DefaultDeterministicSectionValidator().validate(request("## System Context\nTODO: describe this.\n"))
        assertEquals(ReviewIssueSeverity.ERROR, issues.single().severity)
        assertEquals(ReviewIssueType.PLACEHOLDER_CONTENT, issues.single().type)
    }

    @Test
    fun `rejects unknown evidence reference`() {
        val issues = DefaultDeterministicSectionValidator().validate(
            request("## System Context\nUses a local provider. [evidence:missing]\n"),
        )
        assertTrue(issues.any { it.type == ReviewIssueType.MISSING_EVIDENCE && it.severity == ReviewIssueSeverity.ERROR })
    }

    private fun request(markdown: String) = SectionReviewRequest(
        GeneratedSection(ArchitectureSectionId("system-context"), "System Context", markdown),
        promptPlan(),
    )

    private fun promptPlan() = PromptPlan(
        systemInstruction = "system",
        taskInstruction = "task",
        context = PromptContext(
            changedFiles = emptyList(),
            affectedKnowledge = emptyList(),
            evidence = listOf(PromptEvidence("ev-1", "src/App.kt", 1, "App", "Application entry point.")),
            previousSectionContent = null,
            omittedChangedFileCount = 0,
            omittedKnowledgeCount = 0,
            omittedEvidenceCount = 0,
            previousSectionTruncated = false,
        ),
        constraints = listOf(io.docpilot.core.incremental.prompt.PromptConstraint("evidence-only", "Use evidence.")),
        outputContract = PromptOutputContract(
            PromptOutputFormat.MARKDOWN_SECTION,
            ArchitectureSectionId("system-context"),
            true,
            false,
        ),
        estimatedInputTokens = 1,
        inputTokenBudget = 1,
        warnings = emptyList(),
    )
}
