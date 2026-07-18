package io.docpilot.core.incremental.specification.ai

import io.docpilot.core.api.AiProvider
import io.docpilot.core.incremental.specification.ChangeKind
import io.docpilot.core.incremental.specification.IncrementalUpdateAction
import io.docpilot.core.incremental.specification.IncrementalUpdatePlan
import io.docpilot.core.incremental.specification.IncrementalUpdateTarget
import io.docpilot.core.model.ApiSpecification
import io.docpilot.core.model.ComponentSpecification
import io.docpilot.core.model.ProjectDescriptor
import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.model.ai.AiCapability
import io.docpilot.core.model.ai.AiExecutionLocation
import io.docpilot.core.model.ai.AiFinishReason
import io.docpilot.core.model.ai.AiGenerationResult
import io.docpilot.core.model.ai.AiModelId
import io.docpilot.core.model.ai.AiProviderDescriptor
import io.docpilot.core.model.ai.AiProviderId
import io.docpilot.core.model.ai.AiRequest
import io.docpilot.core.model.ai.AiResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiIncrementalDocumentationGeneratorTest {
    @Test
    fun `no changes skip provider`() {
        val provider = RecordingProvider("unused")
        val specification = specification("old purpose")
        val result = DefaultAiIncrementalDocumentationGenerator(provider).generate(
            AiIncrementalGenerationRequest(
                specification,
                specification,
                IncrementalUpdatePlan.EMPTY,
                "existing",
                AiModelId("test"),
            ),
        )

        assertEquals(AiIncrementalGenerationStatus.NO_CHANGES, result.status)
        assertEquals(0, provider.requests.size)
        assertEquals("existing", result.mergedDocumentation)
    }

    @Test
    fun `prompt contains only changed target and merge replaces managed block`() {
        val response = """
            <<<DOCPILOT_PATCH id=api:one>>>
            ### Updated API
            Documents the new behavior.
            <<<END_DOCPILOT_PATCH>>>
        """.trimIndent()
        val provider = RecordingProvider(response)
        val previous = specification("old purpose")
        val current = specification("new purpose")
        val plan = IncrementalUpdatePlan(
            actions = listOf(
                IncrementalUpdateAction(IncrementalUpdateTarget.API, "api:one", "type:one", ChangeKind.MODIFIED),
            ),
            changedTypeIds = listOf("type:one"),
        )
        val existing = """
            # Project

            ## AI Incremental Documentation

            <!-- DOCPILOT_AI_START id=api:one -->
            old generated text
            <!-- DOCPILOT_AI_END id=api:one -->
        """.trimIndent()

        val result = DefaultAiIncrementalDocumentationGenerator(provider).generate(
            AiIncrementalGenerationRequest(previous, current, plan, existing, AiModelId("test")),
        )

        assertEquals(AiIncrementalGenerationStatus.SUCCEEDED, result.status)
        assertTrue(result.mergedDocumentation.contains("Documents the new behavior."))
        assertFalse(result.mergedDocumentation.contains("old generated text"))
        assertEquals(1, result.mergedDocumentation.split("DOCPILOT_AI_START id=api:one").size - 1)
        val prompt = provider.requests.single().messages.last().content
        assertTrue(prompt.contains("api:one"))
        assertTrue(prompt.contains("new purpose"))
        assertFalse(prompt.contains("type:unrelated"))
        assertTrue(result.metrics!!.responseCharacters > 0)
    }

    @Test
    fun `unchanged target returned by provider is rejected`() {
        val provider = RecordingProvider(
            "<<<DOCPILOT_PATCH id=api:other>>>invalid<<<END_DOCPILOT_PATCH>>>",
        )
        val previous = specification("old")
        val current = specification("new")
        val plan = IncrementalUpdatePlan(
            actions = listOf(
                IncrementalUpdateAction(IncrementalUpdateTarget.API, "api:one", "type:one", ChangeKind.MODIFIED),
            ),
        )

        val result = DefaultAiIncrementalDocumentationGenerator(provider).generate(
            AiIncrementalGenerationRequest(previous, current, plan, "existing", AiModelId("test")),
        )

        assertEquals(AiIncrementalGenerationStatus.FAILED, result.status)
        assertEquals("existing", result.mergedDocumentation)
        assertTrue(result.errorMessage!!.contains("unchanged target"))
    }

    private fun specification(purpose: String): ProjectSpecification = ProjectSpecification(
        schemaVersion = "0.3",
        project = ProjectDescriptor("project", "Project"),
        components = listOf(
            ComponentSpecification(
                id = "type:one",
                name = "One",
                moduleId = "module:main",
                kind = "class",
                role = "service",
                apis = listOf(
                    ApiSpecification(
                        id = "api:one",
                        name = "run",
                        kind = "function",
                        signature = "run(): Unit",
                        purpose = purpose,
                    ),
                ),
            ),
            ComponentSpecification(
                id = "type:unrelated",
                name = "Unrelated",
                moduleId = "module:main",
                kind = "class",
                role = "unrelated",
            ),
        ),
    )

    private class RecordingProvider(private val content: String) : AiProvider {
        val requests = mutableListOf<AiRequest>()
        override val descriptor = AiProviderDescriptor(
            id = AiProviderId("test"),
            displayName = "Test",
            version = "1.0.0",
            executionLocation = AiExecutionLocation.LOCAL,
            capabilities = setOf(AiCapability.TEXT_GENERATION),
        )

        override fun generate(request: AiRequest): AiGenerationResult {
            requests += request
            return AiGenerationResult.Success(
                AiResponse(
                    providerId = descriptor.id,
                    modelId = request.modelId,
                    content = content,
                    finishReason = AiFinishReason.STOP,
                ),
            )
        }
    }
}
