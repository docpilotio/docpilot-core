package io.docpilot.core.incremental.generation

import io.docpilot.core.api.AiProvider
import io.docpilot.core.incremental.prompt.IncrementalPromptBuilder
import io.docpilot.core.incremental.prompt.PromptBuildRequest
import io.docpilot.core.model.ai.AiGenerationResult

class DefaultIncrementalDocumentationGenerator(
    private val promptBuilder: IncrementalPromptBuilder,
    private val aiProvider: AiProvider,
    private val requestMapper: PromptPlanAiRequestMapper = DefaultPromptPlanAiRequestMapper(),
    private val normalizer: GeneratedSectionNormalizer = DefaultGeneratedSectionNormalizer(),
    private val sectionStore: DocumentationSectionStore = FileDocumentationSectionStore(),
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
                        staged += generated
                        results += GenerationJobResult(job.section.id, GenerationJobStatus.SUCCEEDED, generated)
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
                results += GenerationJobResult(
                    job.section.id,
                    GenerationJobStatus.FAILED,
                    failure = GenerationFailure(error.message ?: "Generation failed.", error::class.qualifiedName),
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
