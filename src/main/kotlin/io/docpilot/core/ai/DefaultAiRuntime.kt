package io.docpilot.core.ai

import io.docpilot.core.api.AiProvider
import io.docpilot.core.api.AiProviderRegistry
import io.docpilot.core.api.AiRuntime
import io.docpilot.core.api.SelectionPolicy
import io.docpilot.core.model.ai.AiError
import io.docpilot.core.model.ai.AiErrorCode
import io.docpilot.core.model.ai.AiGenerationResult
import io.docpilot.core.model.ai.AiProviderId
import io.docpilot.core.model.ai.AiRequest
import io.docpilot.core.model.selection.SelectionContext
import io.docpilot.core.model.selection.SelectionResult
import io.docpilot.core.selection.DefaultSelectionPolicy

class DefaultAiRuntime(
    private val registry: AiProviderRegistry,
    private val selectionPolicy:
        SelectionPolicy<AiProvider, AiProviderId> =
        DefaultSelectionPolicy<AiProvider, AiProviderId>(
            candidateId = { provider: AiProvider ->
                provider.descriptor.id
            },
        ),
) : AiRuntime {

    override fun generate(
        request: AiRequest,
        selectionContext: SelectionContext<AiProviderId>,
    ): AiGenerationResult {
        selectionContext.explicitCandidateId?.let { providerId ->
            val explicitProvider = registry.find(providerId)
                ?: return AiGenerationResult.Failure(
                    AiError(
                        code = AiErrorCode.PROVIDER_NOT_FOUND,
                        message = "Explicit AI provider is unavailable: $providerId",
                        retryable = false,
                        providerId = providerId,
                    ),
                )

            if (!explicitProvider.descriptor.supports(request.modelId)) {
                return AiGenerationResult.Failure(
                    AiError(
                        code = AiErrorCode.MODEL_NOT_SUPPORTED,
                        message = "Provider $providerId does not support model ${request.modelId}.",
                        retryable = false,
                        providerId = providerId,
                    ),
                )
            }
        }

        val compatibleProviders = registry.all().filter {
            it.descriptor.supports(request.modelId)
        }

        return when (
            val selection = selectionPolicy.select(
                candidates = compatibleProviders,
                context = selectionContext,
            )
        ) {
            is SelectionResult.Selected ->
                executeSafely(
                    provider = selection.candidate,
                    request = request,
                )

            is SelectionResult.Unavailable ->
                AiGenerationResult.Failure(
                    AiError(
                        code = if (registry.all().isEmpty()) {
                            AiErrorCode.PROVIDER_NOT_FOUND
                        } else {
                            AiErrorCode.MODEL_NOT_SUPPORTED
                        },
                        message = selection.reasons.joinToString("; "),
                        retryable = false,
                    ),
                )
        }
    }

    private fun executeSafely(
        provider: AiProvider,
        request: AiRequest,
    ): AiGenerationResult =
        try {
            provider.generate(request)
        } catch (exception: Exception) {
            AiGenerationResult.Failure(
                AiError(
                    code = AiErrorCode.PROVIDER_FAILURE,
                    message = exception.message
                        ?: "AI provider execution failed.",
                    retryable = false,
                    providerId = provider.descriptor.id,
                    causeType = exception::class.qualifiedName,
                ),
            )
        }
}
