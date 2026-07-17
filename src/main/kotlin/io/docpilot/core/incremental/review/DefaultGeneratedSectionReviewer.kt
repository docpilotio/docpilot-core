package io.docpilot.core.incremental.review

import io.docpilot.core.api.AiProvider
import io.docpilot.core.model.ai.AiGenerationResult
import io.docpilot.core.model.ai.AiModelId

class DefaultGeneratedSectionReviewer(
    private val aiProvider: AiProvider,
    private val deterministicValidator: DeterministicSectionValidator = DefaultDeterministicSectionValidator(),
    private val requestMapper: SectionReviewAiRequestMapper = DefaultSectionReviewAiRequestMapper(),
    private val responseNormalizer: ReviewResponseNormalizer = DefaultReviewResponseNormalizer(),
    private val decisionPolicy: ReviewDecisionPolicy = DefaultReviewDecisionPolicy(),
) : GeneratedSectionReviewer {
    override fun review(request: SectionReviewRequest, modelId: AiModelId): SectionReviewResult {
        val deterministicIssues = deterministicValidator.validate(request)
        if (deterministicIssues.any { it.severity == ReviewIssueSeverity.ERROR }) {
            return SectionReviewResult(
                ReviewDecision.REJECTED,
                scoreFor(deterministicIssues),
                deterministicIssues,
                "Correct deterministic validation errors before regeneration.",
            )
        }

        val aiResult = when (val generation = aiProvider.generate(requestMapper.map(request, modelId))) {
            is AiGenerationResult.Success -> responseNormalizer.normalize(generation.response.content)
            is AiGenerationResult.Failure -> throw SectionReviewException(
                "AI review failed: ${generation.error.message}",
                generation.error,
            )
        }
        val combined = (deterministicIssues + aiResult.issues)
            .distinct()
            .sortedWith(SectionReviewResult.REVIEW_ISSUE_ORDER)
        return aiResult.copy(
            decision = decisionPolicy.decide(combined),
            issues = combined,
        )
    }

    private fun scoreFor(issues: List<ReviewIssue>) = ReviewScore(
        evidenceSupport = if (issues.any { it.type == ReviewIssueType.MISSING_EVIDENCE }) 0 else 100,
        consistency = 100,
        completeness = if (issues.any { it.type == ReviewIssueType.PLACEHOLDER_CONTENT }) 0 else 100,
        contractCompliance = if (issues.any { it.severity == ReviewIssueSeverity.ERROR }) 0 else 100,
        overall = 0,
    )
}

class SectionReviewException(
    message: String,
    val aiError: io.docpilot.core.model.ai.AiError? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
