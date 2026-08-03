package io.docpilot.cli.command

import io.docpilot.cli.bootstrap.CliBootstrap
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertTrue

class DefaultDocumentationGenerationWorkflowLoggingTest {
    @Test
    fun `logs operational start-completion lines and AI enrichment payloads`() {
        val project = createTempDirectory("docpilot-docs-logging-project")
        val sourceFile = project.resolve("src/main/kotlin/com/example/Sample.kt")
        Files.createDirectories(sourceFile.parent)
        sourceFile.writeText("package com.example\n\nclass Sample\n")
        val output = createTempDirectory("docpilot-docs-logging-output")

        val bootstrap = CliBootstrap()
        val workflow = DefaultDocumentationGenerationWorkflow(providerResolver = { bootstrap.createProvider(it) })
        val options = DocumentationGenerationOptions(
            projectRoot = project, outputRoot = output, profile = "kotlin-android@1",
            mode = DocumentationGenerationMode.PREVIEW, full = true, artifactIds = emptySet(),
            documentTypes = emptySet(), expectedPlanSha256 = null, json = false,
            enrich = true, provider = "fixture", model = "fixture-model",
        )

        val result = workflow.execute(options)

        assertTrue(result.status != DocumentationGenerationStatus.FAILED, "diagnostics=${result.diagnostics}")
        val logRoot = project.resolve("docpilot").resolve("log")
        assertTrue(Files.isDirectory(logRoot), "expected $logRoot to exist")
        val sessionDir = Files.list(logRoot).use { it.findFirst().orElseThrow() }
        val operationsLog = sessionDir.resolve("operations.log")
        assertTrue(Files.isRegularFile(operationsLog))
        val logText = Files.readString(operationsLog)
        assertTrue(logText.contains("Documentation generation started"), logText)
        assertTrue(logText.contains("Documentation generation completed"), logText)
        assertTrue(
            Files.list(sessionDir).use { stream -> stream.anyMatch { it.fileName.toString().startsWith("ai-") } },
            "expected at least one AI payload file to be logged",
        )
    }

    @Test
    fun `writes no log when the project path is not a directory`() {
        val bootstrap = CliBootstrap()
        val workflow = DefaultDocumentationGenerationWorkflow(providerResolver = { bootstrap.createProvider(it) })
        val missingProject = createTempDirectory("docpilot-docs-logging-missing").resolve("does-not-exist")
        val options = DocumentationGenerationOptions(
            projectRoot = missingProject, outputRoot = createTempDirectory("docpilot-docs-logging-output2"),
            profile = "kotlin-android@1", mode = DocumentationGenerationMode.PREVIEW, full = true,
            artifactIds = emptySet(), documentTypes = emptySet(), expectedPlanSha256 = null, json = false,
        )

        val result = workflow.execute(options)

        assertTrue(result.status == DocumentationGenerationStatus.BLOCKED)
        assertTrue(Files.notExists(missingProject.resolve("docpilot")))
    }
}
