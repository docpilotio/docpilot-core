package io.docpilot.core.incremental.specification.ai

import io.docpilot.core.incremental.specification.IncrementalUpdatePlan
import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.model.ai.AiModelId

public data class AiIncrementalGenerationRequest(
    public val previousSpecification: ProjectSpecification,
    public val currentSpecification: ProjectSpecification,
    public val updatePlan: IncrementalUpdatePlan,
    public val existingDocumentation: String,
    public val modelId: AiModelId,
)

public enum class AiIncrementalGenerationStatus { NO_CHANGES, SUCCEEDED, FAILED }

public data class AiDocumentationPatch(
    public val targetId: String,
    public val markdown: String,
)

public data class AiIncrementalMetrics(
    public val promptCharacters: Int,
    public val responseCharacters: Int,
    public val fullSpecificationCharacters: Int,
) {
    public val promptReductionRatio: Double
        get() = if (fullSpecificationCharacters == 0) 0.0
        else 1.0 - (promptCharacters.toDouble() / fullSpecificationCharacters.toDouble())
}

public data class AiIncrementalGenerationResult(
    public val status: AiIncrementalGenerationStatus,
    public val mergedDocumentation: String,
    public val patches: List<AiDocumentationPatch> = emptyList(),
    public val metrics: AiIncrementalMetrics? = null,
    public val errorMessage: String? = null,
)
