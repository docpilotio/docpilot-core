package io.docpilot.cli.command

import io.docpilot.cli.io.ConsolePrinter
import io.docpilot.core.api.DocumentationArtifactId
import io.docpilot.core.incremental.execution.DocumentationArtifactOperation
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GenerateDocsCommandTest {
    @Test
    fun `parses preview as the default and repeated selectors`() {
        val options = GenerateDocsOptionsParser.parse(listOf(
            "--project", ".", "--output", "out", "--artifact", "a", "--artifact", "b", "--json",
        ))
        assertEquals(DocumentationGenerationMode.PREVIEW, options.mode)
        assertEquals(setOf("a", "b"), options.artifactIds)
        assertTrue(options.json)
    }

    @Test
    fun `rejects conflicting modes and full selective generation`() {
        assertFailsWith<IllegalArgumentException> {
            GenerateDocsOptionsParser.parse(listOf("--project", ".", "--output", "out", "--dry-run", "--confirm"))
        }
        assertFailsWith<IllegalArgumentException> {
            GenerateDocsOptionsParser.parse(listOf("--project", ".", "--output", "out", "--full", "--artifact", "a"))
        }
    }

    @Test
    fun `json mode writes only one machine readable result to stdout`() {
        val out = ByteArrayOutputStream(); val err = ByteArrayOutputStream()
        val result = DocumentationGenerationResult(
            DocumentationGenerationStatus.PREVIEW_READY, DocumentationGenerationMode.PREVIEW, "project", "out",
            "kotlin-android@1", "NOT_FOUND", "a".repeat(64),
            listOf(DocumentationArtifactResult(DocumentationArtifactId("project:p"), "PROJECT_OVERVIEW", "docs/project.md",
                DocumentationArtifactOperation.CREATE, "b".repeat(64), "MISSING")),
        )
        val command = GenerateCommand(
            printer = ConsolePrinter(PrintStream(out), PrintStream(err)),
            documentationWorkflow = DocumentationGenerationWorkflow { result },
        )
        val exit = command.execute(listOf("docs", "--project", ".", "--output", "out", "--json"))
        assertEquals(0, exit)
        assertTrue(out.toString().trim().startsWith("{"))
        assertTrue(out.toString().trim().endsWith("}"))
        assertEquals("", err.toString())
    }

    @Test
    fun `conflict result maps to stable exit code`() {
        val result = DocumentationGenerationResult(
            DocumentationGenerationStatus.CONFLICT, DocumentationGenerationMode.APPLY, null, "out",
            "kotlin-android@1", "VALID", null, diagnostics = listOf("modified"),
        )
        val command = GenerateCommand(documentationWorkflow = DocumentationGenerationWorkflow { result })
        assertEquals(5, command.execute(listOf("docs", "--project", ".", "--output", "out", "--confirm")))
    }
}
