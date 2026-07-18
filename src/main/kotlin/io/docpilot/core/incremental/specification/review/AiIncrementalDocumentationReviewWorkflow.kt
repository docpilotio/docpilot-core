package io.docpilot.core.incremental.specification.review

import io.docpilot.core.incremental.specification.ai.AiDocumentationPatch
import io.docpilot.core.incremental.specification.ai.AiIncrementalDocumentationGenerator
import io.docpilot.core.incremental.specification.ai.AiIncrementalGenerationRequest
import io.docpilot.core.incremental.specification.ai.AiIncrementalGenerationStatus
import io.docpilot.core.incremental.specification.ai.AiIncrementalMetrics

public enum class AiIncrementalReviewPreparationStatus {
    NO_CHANGES,
    READY_FOR_REVIEW,
    FAILED,
}

public data class AiIncrementalReviewPreparationResult(
    public val status: AiIncrementalReviewPreparationStatus,
    public val existingDocumentation: String,
    public val patches: List<AiDocumentationPatch> = emptyList(),
    public val proposal: DocumentationReviewProposal? = null,
    public val metrics: AiIncrementalMetrics? = null,
    public val errorMessage: String? = null,
) {
    init {
        when (status) {
            AiIncrementalReviewPreparationStatus.NO_CHANGES -> require(proposal == null && patches.isEmpty()) {
                "No-change review preparation must not contain patches or a proposal."
            }
            AiIncrementalReviewPreparationStatus.READY_FOR_REVIEW -> require(proposal != null) {
                "Ready review preparation must contain a proposal."
            }
            AiIncrementalReviewPreparationStatus.FAILED -> require(!errorMessage.isNullOrBlank()) {
                "Failed review preparation must contain an error message."
            }
        }
    }
}

public interface AiIncrementalDocumentationReviewWorkflow {
    public fun prepare(request: AiIncrementalGenerationRequest): AiIncrementalReviewPreparationResult

    public fun apply(
        preparation: AiIncrementalReviewPreparationResult,
        decisions: List<DocumentationReviewDecision>,
    ): DocumentationReviewResult
}

public class DefaultAiIncrementalDocumentationReviewWorkflow(
    private val generator: AiIncrementalDocumentationGenerator,
    private val reviewer: DocumentationDiffReviewer = DefaultDocumentationDiffReviewer(),
) : AiIncrementalDocumentationReviewWorkflow {
    override fun prepare(request: AiIncrementalGenerationRequest): AiIncrementalReviewPreparationResult {
        val generation = generator.generate(request)
        return when (generation.status) {
            AiIncrementalGenerationStatus.NO_CHANGES -> AiIncrementalReviewPreparationResult(
                status = AiIncrementalReviewPreparationStatus.NO_CHANGES,
                existingDocumentation = request.existingDocumentation,
                metrics = generation.metrics,
            )
            AiIncrementalGenerationStatus.FAILED -> AiIncrementalReviewPreparationResult(
                status = AiIncrementalReviewPreparationStatus.FAILED,
                existingDocumentation = request.existingDocumentation,
                metrics = generation.metrics,
                errorMessage = generation.errorMessage ?: "AI incremental generation failed.",
            )
            AiIncrementalGenerationStatus.SUCCEEDED -> {
                val proposal = reviewer.propose(
                    DocumentationReviewRequest(
                        previousSpecification = request.previousSpecification,
                        currentSpecification = request.currentSpecification,
                        updatePlan = request.updatePlan,
                        existingDocumentation = request.existingDocumentation,
                        patches = generation.patches,
                    ),
                )
                AiIncrementalReviewPreparationResult(
                    status = AiIncrementalReviewPreparationStatus.READY_FOR_REVIEW,
                    existingDocumentation = request.existingDocumentation,
                    patches = generation.patches,
                    proposal = proposal,
                    metrics = generation.metrics,
                )
            }
        }
    }

    override fun apply(
        preparation: AiIncrementalReviewPreparationResult,
        decisions: List<DocumentationReviewDecision>,
    ): DocumentationReviewResult {
        require(preparation.status == AiIncrementalReviewPreparationStatus.READY_FOR_REVIEW) {
            "Only a ready review preparation can be applied."
        }
        return reviewer.apply(
            preparation.existingDocumentation,
            requireNotNull(preparation.proposal),
            decisions,
        )
    }
}
