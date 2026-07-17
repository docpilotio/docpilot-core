package io.docpilot.core.incremental.generation

import io.docpilot.core.generator.architecture.plan.ArchitectureSectionId
import io.docpilot.core.incremental.ProjectChangeSet
import io.docpilot.core.incremental.planning.IncrementalGenerationPlan
import io.docpilot.core.model.ai.AiError
import io.docpilot.core.model.ai.AiModelId
import io.docpilot.core.model.knowledge.KnowledgeBuildResult
import io.docpilot.core.incremental.review.SectionReviewResult
import java.nio.file.Path

data class DocumentationGenerationRequest(
    val plan: IncrementalGenerationPlan,
    val knowledge: KnowledgeBuildResult,
    val changeSet: ProjectChangeSet,
    val targetDocument: Path,
    val modelId: AiModelId,
    val mode: GenerationExecutionMode = GenerationExecutionMode.WRITE,
)

enum class GenerationExecutionMode { PREVIEW, WRITE }
enum class DocumentationGenerationStatus { NO_CHANGES, SUCCEEDED, FAILED }
enum class GenerationJobStatus { SUCCEEDED, FAILED, SKIPPED }

data class GeneratedSection(
    val sectionId: ArchitectureSectionId,
    val heading: String,
    val markdown: String,
)

data class GenerationFailure(
    val message: String,
    val causeType: String? = null,
    val aiError: AiError? = null,
)

data class GenerationJobResult(
    val sectionId: ArchitectureSectionId,
    val status: GenerationJobStatus,
    val generatedSection: GeneratedSection? = null,
    val review: SectionReviewResult? = null,
    val failure: GenerationFailure? = null,
)

data class DocumentationGenerationResult(
    val status: DocumentationGenerationStatus,
    val jobs: List<GenerationJobResult>,
    val generatedDocument: String?,
    val written: Boolean,
)
