package io.docpilot.core.incremental.review

import io.docpilot.core.incremental.generation.GeneratedSection
import io.docpilot.core.incremental.prompt.PromptPlan

data class SectionReviewRequest(
    val generatedSection: GeneratedSection,
    val promptPlan: PromptPlan,
)

enum class ReviewDecision { ACCEPTED, ACCEPTED_WITH_WARNINGS, REJECTED }
enum class ReviewIssueSeverity { INFO, WARNING, ERROR }
enum class ReviewIssueType {
    UNSUPPORTED_CLAIM,
    MISSING_EVIDENCE,
    EVIDENCE_CONTRADICTION,
    EXISTING_DOCUMENT_CONTRADICTION,
    UNRESOLVED_AS_FACT,
    OUTPUT_CONTRACT_VIOLATION,
    INCOMPLETE_COVERAGE,
    PLACEHOLDER_CONTENT,
    REVIEW_RESPONSE_INVALID,
}

data class ReviewIssue(
    val severity: ReviewIssueSeverity,
    val type: ReviewIssueType,
    val message: String,
    val evidenceIds: List<String> = emptyList(),
) {
    init {
        require(message.isNotBlank()) { "Review issue message must not be blank." }
        require(evidenceIds.none(String::isBlank)) { "Review evidence IDs must not be blank." }
        require(evidenceIds == evidenceIds.distinct().sorted()) {
            "Review evidence IDs must be unique and sorted."
        }
    }
}

data class ReviewScore(
    val evidenceSupport: Int,
    val consistency: Int,
    val completeness: Int,
    val contractCompliance: Int,
    val overall: Int,
) {
    init {
        listOf(evidenceSupport, consistency, completeness, contractCompliance, overall).forEach {
            require(it in 0..100) { "Review scores must be between 0 and 100." }
        }
    }
}

data class SectionReviewResult(
    val decision: ReviewDecision,
    val score: ReviewScore,
    val issues: List<ReviewIssue>,
    val feedback: String? = null,
) {
    init {
        require(feedback == null || feedback.isNotBlank()) { "Review feedback must be null or non-blank." }
        require(issues == issues.sortedWith(REVIEW_ISSUE_ORDER)) {
            "Review issues must use deterministic order."
        }
    }

    companion object {
        val REVIEW_ISSUE_ORDER = compareBy<ReviewIssue> { it.severity.ordinal }
            .thenBy { it.type.name }
            .thenBy { it.message }
    }
}
