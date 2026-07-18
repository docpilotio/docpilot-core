package io.docpilot.cli

import io.docpilot.cli.command.GenerateCommand
import io.docpilot.cli.command.SpecificationGenerateWorkflow
import io.docpilot.cli.io.ConsolePrinter
import io.docpilot.core.incremental.execution.IncrementalDocumentationExecutionResult
import io.docpilot.core.incremental.execution.IncrementalExecutionMode
import io.docpilot.core.incremental.specification.snapshot.SnapshotExecutionFailureStage
import io.docpilot.core.incremental.specification.snapshot.SnapshotValidationFailure
import io.docpilot.core.incremental.specification.snapshot.SpecificationSnapshotExecutionResult
import io.docpilot.core.incremental.specification.snapshot.SpecificationSnapshotLoadResult
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenerateCommandSpecificationTest {
    @Test
    fun `reports full regeneration and successful snapshot save`() {
        val output = ByteArrayOutputStream()
        val error = ByteArrayOutputStream()
        val command = command(
            result = SpecificationSnapshotExecutionResult(
                execution = IncrementalDocumentationExecutionResult(
                    mode = IncrementalExecutionMode.FULL_REGENERATION,
                ),
                snapshotLoadResult = SpecificationSnapshotLoadResult.NotFound,
                snapshotSaved = true,
            ),
            output = output,
            error = error,
        )

        val exitCode = command.execute(listOf("specification", "--project", "."))

        assertEquals(0, exitCode)
        assertTrue(output.toString().contains("Execution Mode: FULL_REGENERATION"))
        assertTrue(output.toString().contains("Snapshot Validation: NOT_FOUND"))
        assertTrue(output.toString().contains("snapshot saved"))
        assertEquals("", error.toString())
    }

    @Test
    fun `reports no changes without replacing snapshot`() {
        val output = ByteArrayOutputStream()
        val command = command(
            result = SpecificationSnapshotExecutionResult(
                execution = IncrementalDocumentationExecutionResult(
                    mode = IncrementalExecutionMode.NO_CHANGES,
                ),
                snapshotLoadResult = validLoadResultPlaceholder(),
                snapshotSaved = false,
            ),
            output = output,
        )

        val exitCode = command.execute(listOf("specification", "--project", ".", "--output", "build/docs"))

        assertEquals(0, exitCode)
        assertTrue(output.toString().contains("Execution Mode: NO_CHANGES"))
        assertTrue(output.toString().contains("snapshot unchanged"))
    }

    @Test
    fun `uses snapshot exit code for unsupported snapshot version`() {
        val error = ByteArrayOutputStream()
        val invalid = SpecificationSnapshotLoadResult.Invalid(
            SnapshotValidationFailure.UNSUPPORTED_VERSION,
            "Unsupported snapshot format version: 2",
        )
        val command = command(
            result = SpecificationSnapshotExecutionResult(
                execution = IncrementalDocumentationExecutionResult(
                    mode = IncrementalExecutionMode.FAILED,
                    errorMessage = invalid.message,
                ),
                snapshotLoadResult = invalid,
                snapshotSaved = false,
                failureStage = SnapshotExecutionFailureStage.SNAPSHOT_LOAD,
                errorMessage = invalid.message,
            ),
            error = error,
        )

        val exitCode = command.execute(listOf("specification", "--project", "."))

        assertEquals(3, exitCode)
        assertTrue(error.toString().contains("Unsupported snapshot format version"))
        assertTrue(error.toString().contains("[ERROR]"))
    }

    private fun command(
        result: SpecificationSnapshotExecutionResult,
        output: ByteArrayOutputStream = ByteArrayOutputStream(),
        error: ByteArrayOutputStream = ByteArrayOutputStream(),
    ): GenerateCommand = GenerateCommand(
        printer = ConsolePrinter(PrintStream(output), PrintStream(error)),
        specificationWorkflow = SpecificationGenerateWorkflow { _: Path, _: Path -> result },
    )

    private fun validLoadResultPlaceholder(): SpecificationSnapshotLoadResult =
        SpecificationSnapshotLoadResult.NotFound
}
