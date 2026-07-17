package io.docpilot.core.incremental.generation

import io.docpilot.core.api.AiProvider
import io.docpilot.core.incremental.prompt.IncrementalPromptBuilder
import io.docpilot.core.incremental.prompt.PromptBuildRequest
import io.docpilot.core.incremental.review.DefaultGeneratedSectionReviewer
import io.docpilot.core.incremental.review.GeneratedSectionReviewer
import io.docpilot.core.incremental.review.ReviewDecision
import io.docpilot.core.incremental.review.SectionReviewException
import io.docpilot.core.incremental.review.SectionReviewRequest
import io.docpilot.core.model.ai.AiGenerationResult

class DefaultIncrementalDocumentationGenerator(
    private val promptBuilder: IncrementalPromptBuilder,
    private val aiProvider: AiProvider,
    private val requestMapper: PromptPlanAiRequestMapper = DefaultPromptPlanAiRequestMapper(),
    private val normalizer: GeneratedSectionNormalizer = DefaultGeneratedSectionNormalizer(),
    private val sectionStore: DocumentationSectionStore = FileDocumentationSectionStore(),
    private val reviewer: GeneratedSectionReviewer = DefaultGeneratedSectionReviewer(aiProvider),
) : IncrementalDocumentationGenerator {
    override fun generate(request: DocumentationGenerationRequest): DocumentationGenerationResult {
        if (request.plan.isEmpty) return DocumentationGenerationResult(
            DocumentationGenerationStatus.NO_CHANGES, emptyList(), null, false,
        )

        val original = sectionStore.readDocument(request.targetDocument)
        val results = mutableListOf<GenerationJobResult>()
        val staged = mutableListOf<GeneratedSection>()
        var failed = false

        request.plan.jobs.forEach { job ->
            if (failed) {
                results += GenerationJobResult(job.section.id, GenerationJobStatus.SKIPPED)
                return@forEach
            }
            try {
                val previous = sectionStore.read(original, job.section)
                val plan = promptBuilder.build(PromptBuildRequest(job, request.knowledge, request.changeSet, previous))
                when (val ai = aiProvider.generate(requestMapper.map(plan, job, request.modelId))) {
                    is AiGenerationResult.Success -> {
                        val generated = normalizer.normalize(ai.response.content, job.section.title, plan.outputContract)
                        val review = reviewer.review(SectionReviewRequest(generated, plan), request.modelId)
                        if (review.decision == ReviewDecision.REJECTED) {
                            failed = true
                            results += GenerationJobResult(
                                sectionId = job.section.id,
                                status = GenerationJobStatus.FAILED,
                                generatedSection = generated,
                                review = review,
                                failure = GenerationFailure(
                                    message = review.feedback ?: "Generated section was rejected by review.",
                                    causeType = "${ReviewDecision::class.qualifiedName}.${review.decision.name}",
                                ),
                            )
                        } else {
                            staged += generated
                            results += GenerationJobResult(
                                sectionId = job.section.id,
                                status = GenerationJobStatus.SUCCEEDED,
                                generatedSection = generated,
                                review = review,
                            )
                        }
                    }
                    is AiGenerationResult.Failure -> {
                        failed = true
                        results += GenerationJobResult(
                            job.section.id,
                            GenerationJobStatus.FAILED,
                            failure = GenerationFailure(ai.error.message, ai.error.causeType, ai.error),
                        )
                    }
                }
            } catch (error: Exception) {
                failed = true
                val aiError = (error as? SectionReviewException)?.aiError
                results += GenerationJobResult(
                    job.section.id,
                    GenerationJobStatus.FAILED,
                    failure = GenerationFailure(
                        error.message ?: "Generation failed.",
                        error::class.qualifiedName,
                        aiError,
                    ),
                )
            }
        }

        if (failed) return DocumentationGenerationResult(
            DocumentationGenerationStatus.FAILED, results, null, false,
        )

        val merged = sectionStore.replace(original, staged)
        val written = request.mode == GenerationExecutionMode.WRITE
        if (written) sectionStore.writeAtomically(request.targetDocument, merged)
        return DocumentationGenerationResult(
            DocumentationGenerationStatus.SUCCEEDED, results, merged, written,
        )
    }
}
