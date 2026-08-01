package io.docpilot.cli.logging

import io.docpilot.core.api.AiProvider
import io.docpilot.core.model.ai.*
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectLogSessionTest {
    @Test
    fun `writes operational prompt and response logs under project`() {
        val project = Files.createTempDirectory("docpilot-project-log")
        val clock = Clock.fixed(Instant.parse("2026-08-01T03:04:05.006Z"), ZoneOffset.UTC)
        val session = ProjectLogSession.create(project, clock)
        val provider = session.logging(SuccessProvider)

        session.info("Generation started.")
        val result = provider.generate(
            AiRequest(
                modelId = AiModelId("test-model"),
                messages = listOf(AiMessage(AiMessageRole.USER, "full prompt")),
            ),
        )

        assertTrue(result is AiGenerationResult.Success)
        val sessionDirectory = project.resolve("docpilot/log/20260801-030405-006")
        assertContains(Files.readString(sessionDirectory.resolve("operations.log")), "Generation started.")
        assertContains(Files.readString(sessionDirectory.resolve("ai-001-prompt.txt")), "full prompt")
        assertContains(Files.readString(sessionDirectory.resolve("ai-001-response.txt")), "full response")
    }

    @Test
    fun `creates a distinct directory when timestamp collides`() {
        val project = Files.createTempDirectory("docpilot-project-log-collision")
        val clock = Clock.fixed(Instant.parse("2026-08-01T03:04:05.006Z"), ZoneOffset.UTC)

        ProjectLogSession.create(project, clock)
        ProjectLogSession.create(project, clock)

        Files.list(project.resolve("docpilot/log")).use { paths ->
            assertEquals(2, paths.count())
        }
    }

    private object SuccessProvider : AiProvider {
        override val descriptor = AiProviderDescriptor(
            id = AiProviderId("test-provider"),
            displayName = "Test Provider",
            version = "1.0.0",
            executionLocation = AiExecutionLocation.LOCAL,
            supportedModels = setOf(AiModelId("test-model")),
            capabilities = emptySet(),
        )

        override fun generate(request: AiRequest) = AiGenerationResult.Success(
            AiResponse(
                providerId = descriptor.id,
                modelId = request.modelId,
                content = "full response",
                finishReason = AiFinishReason.STOP,
            ),
        )
    }
}
