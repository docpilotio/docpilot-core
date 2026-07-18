package io.docpilot.core.incremental.specification.ai

import io.docpilot.core.api.AiProvider
import io.docpilot.core.model.ai.AiGenerationResult
import io.docpilot.core.model.ai.AiMessage
import io.docpilot.core.model.ai.AiMessageRole
import io.docpilot.core.model.ai.AiRequest

public interface AiIncrementalDocumentationGenerator {
    public fun generate(request: AiIncrementalGenerationRequest): AiIncrementalGenerationResult
}

public class DefaultAiIncrementalDocumentationGenerator(
    private val provider: AiProvider,
    private val promptBuilder: SpecificationIncrementalPromptBuilder = DefaultSpecificationIncrementalPromptBuilder(),
    private val patchCodec: AiDocumentationPatchCodec = MarkerAiDocumentationPatchCodec(),
    private val merger: AiDocumentationMerger = ManagedBlockAiDocumentationMerger(),
) : AiIncrementalDocumentationGenerator {
    override fun generate(request: AiIncrementalGenerationRequest): AiIncrementalGenerationResult {
        if (!request.updatePlan.requiresUpdate) {
            return AiIncrementalGenerationResult(
                status = AiIncrementalGenerationStatus.NO_CHANGES,
                mergedDocumentation = request.existingDocumentation,
            )
        }
        val prompt = promptBuilder.build(
            request.previousSpecification,
            request.currentSpecification,
            request.updatePlan.actions,
        )
        val fullSize = request.currentSpecification.toString().length
        return when (val generated = provider.generate(
            AiRequest(
                modelId = request.modelId,
                messages = listOf(
                    AiMessage(AiMessageRole.SYSTEM, "You produce evidence-bound incremental documentation patches."),
                    AiMessage(AiMessageRole.USER, prompt),
                ),
                temperature = 0.0,
                metadata = mapOf(
                    "docpilot.mode" to "specification-incremental",
                    "docpilot.changedTargets" to request.updatePlan.actions.size.toString(),
                ),
            ),
        )) {
            is AiGenerationResult.Failure -> AiIncrementalGenerationResult(
                status = AiIncrementalGenerationStatus.FAILED,
                mergedDocumentation = request.existingDocumentation,
                metrics = AiIncrementalMetrics(prompt.length, 0, fullSize),
                errorMessage = generated.error.message,
            )
            is AiGenerationResult.Success -> try {
                val patches = patchCodec.decode(generated.response.content)
                val expectedIds = request.updatePlan.actions.map { it.id }.toSet()
                require(patches.all { it.targetId in expectedIds }) {
                    "AI response contains a patch for an unchanged target."
                }
                AiIncrementalGenerationResult(
                    status = AiIncrementalGenerationStatus.SUCCEEDED,
                    mergedDocumentation = merger.merge(request.existingDocumentation, patches),
                    patches = patches,
                    metrics = AiIncrementalMetrics(prompt.length, generated.response.content.length, fullSize),
                )
            } catch (error: Exception) {
                AiIncrementalGenerationResult(
                    status = AiIncrementalGenerationStatus.FAILED,
                    mergedDocumentation = request.existingDocumentation,
                    metrics = AiIncrementalMetrics(prompt.length, generated.response.content.length, fullSize),
                    errorMessage = error.message ?: "AI incremental generation failed.",
                )
            }
        }
    }
}
