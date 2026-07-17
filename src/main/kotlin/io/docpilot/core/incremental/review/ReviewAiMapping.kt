package io.docpilot.core.incremental.review

import io.docpilot.core.model.ai.AiMessage
import io.docpilot.core.model.ai.AiMessageRole
import io.docpilot.core.model.ai.AiModelId
import io.docpilot.core.model.ai.AiRequest
import io.docpilot.core.model.ai.AiResponseFormat

fun interface SectionReviewAiRequestMapper {
    fun map(request: SectionReviewRequest, modelId: AiModelId): AiRequest
}

class DefaultSectionReviewAiRequestMapper : SectionReviewAiRequestMapper {
    override fun map(request: SectionReviewRequest, modelId: AiModelId): AiRequest = AiRequest(
        modelId = modelId,
        messages = listOf(
            AiMessage(AiMessageRole.SYSTEM, SYSTEM_INSTRUCTION),
            AiMessage(AiMessageRole.USER, render(request)),
        ),
        temperature = 0.0,
        maxOutputTokens = 2_048,
        responseFormat = AiResponseFormat.TEXT,
        metadata = mapOf(
            "operation" to "section-review",
            "sectionId" to request.generatedSection.sectionId.value,
        ),
    )

    private fun render(request: SectionReviewRequest): String = buildString {
        appendLine("Review the generated architecture documentation section against only the supplied evidence and previous section.")
        appendLine("Do not assume the previous section is correct when it conflicts with evidence; report the conflict.")
        appendLine()
        appendLine("Section ID: ${request.generatedSection.sectionId.value}")
        appendLine("Expected heading: ${request.generatedSection.heading}")
        appendLine("Output contract: ${request.promptPlan.outputContract}")
        appendLine("Constraints:")
        request.promptPlan.constraints.forEach { appendLine("- [${it.id}] ${it.instruction}") }
        appendLine("Evidence:")
        if (request.promptPlan.context.evidence.isEmpty()) appendLine("- NONE")
        request.promptPlan.context.evidence.forEach {
            appendLine("- ${it.id} @ ${it.sourcePath}:${it.lineStart ?: "?"}; symbol=${it.symbol ?: "?"}; ${it.summary}")
        }
        appendLine("Previous section:")
        appendLine(request.promptPlan.context.previousSectionContent ?: "NONE")
        appendLine("Generated section:")
        appendLine(request.generatedSection.markdown)
        appendLine(RESPONSE_CONTRACT)
    }

    private companion object {
        const val SYSTEM_INSTRUCTION =
            "You are DocPilot's independent evidence-first architecture documentation reviewer. " +
                "Identify unsupported claims, contradictions, unresolved assumptions stated as facts, incomplete coverage, and contract violations."
        const val RESPONSE_CONTRACT = """
Return exactly this line-oriented format, without code fences:
DECISION: ACCEPTED|ACCEPTED_WITH_WARNINGS|REJECTED
SCORES: evidenceSupport=<0-100>;consistency=<0-100>;completeness=<0-100>;contractCompliance=<0-100>;overall=<0-100>
ISSUE: <INFO|WARNING|ERROR>|<ReviewIssueType>|<message>|<comma-separated evidence IDs or ->
ISSUE: ... (zero or more ISSUE lines)
FEEDBACK: <single-line correction guidance or ->
Allowed ReviewIssueType values: UNSUPPORTED_CLAIM, MISSING_EVIDENCE, EVIDENCE_CONTRADICTION, EXISTING_DOCUMENT_CONTRADICTION, UNRESOLVED_AS_FACT, OUTPUT_CONTRACT_VIOLATION, INCOMPLETE_COVERAGE.
"""
    }
}
