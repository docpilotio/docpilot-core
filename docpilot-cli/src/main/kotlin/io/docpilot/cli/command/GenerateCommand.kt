package io.docpilot.cli.command

import io.docpilot.cli.bootstrap.CliBootstrap
import io.docpilot.cli.bootstrap.ProjectKnowledgeLoader
import io.docpilot.cli.io.ConsolePrinter
import io.docpilot.cli.io.OutputWriter
import io.docpilot.cli.logging.ProjectLogSession
import io.docpilot.core.document.DocumentRenderer
import io.docpilot.core.generator.adr.AdrGenerationRequest
import io.docpilot.core.generator.adr.AdrStatus
import io.docpilot.core.generator.architecture.ArchitectureGenerationRequest
import io.docpilot.core.model.ai.AiModelId
import java.nio.file.Path

class GenerateCommand(
    private val bootstrap: CliBootstrap = CliBootstrap(),
    private val knowledgeLoader: ProjectKnowledgeLoader = ProjectKnowledgeLoader(),
    private val renderer: DocumentRenderer = DocumentRenderer(),
    private val writer: OutputWriter = OutputWriter(),
    private val printer: ConsolePrinter = ConsolePrinter(),
    private val specificationWorkflow: SpecificationGenerateWorkflow = DefaultSpecificationGenerateWorkflow(),
) {
    fun execute(args: List<String>): Int =
        try {
            require(args.isNotEmpty()) { "Missing generation type." }
            when (args.first()) {
                "architecture" -> architecture(CliArguments.parse(args.drop(1)))
                "adr" -> adr(CliArguments.parse(args.drop(1)))
                "specification" -> specification(CliArguments.parse(args.drop(1)))
                else -> throw IllegalArgumentException("Unknown generation type: ${args.first()}")
            }
            0
        } catch (exception: SpecificationWorkflowException) {
            printer.error(exception.message ?: "Generation failed.")
            exception.exitCode
        } catch (exception: Exception) {
            printer.error(exception.message ?: "Generation failed.")
            1
        }

    private fun architecture(args: CliArguments) {
        val project = Path.of(args.required("project"))
        val log = ProjectLogSession.create(project)
        log.info("Architecture generation started for ${project.toAbsolutePath().normalize()}.")
        val provider = args.required("provider")
        val model = AiModelId(args.required("model"))
        val facade = bootstrap.create(provider, log)
        val document = facade.generateArchitecture(
            ArchitectureGenerationRequest(
                knowledge = knowledgeLoader.load(project),
                modelId = model,
                title = args.optional("title") ?: ArchitectureGenerationRequest.DEFAULT_TITLE,
            ),
        )
        emit(renderer.render(document), args.optional("output"))
        log.info("Architecture generation completed.")
    }

    private fun adr(args: CliArguments) {
        val project = Path.of(args.required("project"))
        val log = ProjectLogSession.create(project)
        log.info("ADR generation started for ${project.toAbsolutePath().normalize()}.")
        val provider = args.required("provider")
        val model = AiModelId(args.required("model"))
        val facade = bootstrap.create(provider, log)
        val status = args.optional("status")?.let(::parseAdrStatus) ?: AdrStatus.ACCEPTED
        val document = facade.generateAdr(
            AdrGenerationRequest(
                knowledge = knowledgeLoader.load(project),
                modelId = model,
                title = args.required("title"),
                context = args.required("context"),
                decision = args.required("decision"),
                consequences = args.required("consequences"),
                alternatives = args.optional("alternatives") ?: "No alternatives were supplied.",
                status = status,
            ),
        )
        emit(renderer.render(document), args.optional("output"))
        log.info("ADR generation completed.")
    }


    private fun specification(args: CliArguments) {
        val project = Path.of(args.required("project"))
        val log = ProjectLogSession.create(project)
        log.info("Specification analysis and generation started for ${project.toAbsolutePath().normalize()}.")
        val outputRoot = Path.of(args.optional("output") ?: project.toString())
        val result = specificationWorkflow.execute(project, outputRoot)

        printer.content("Execution Mode: ${result.execution.mode}")
        printer.content("Snapshot Validation: ${snapshotValidation(result.snapshotLoadResult)}")
        result.execution.fallbackReason?.let { printer.content("Fallback Reason: $it") }
        result.execution.warnings.forEach { printer.content("Warning: $it") }

        if (result.execution.mode == io.docpilot.core.incremental.execution.IncrementalExecutionMode.FAILED) {
            throw SpecificationWorkflowException(
                message = result.errorMessage ?: result.execution.errorMessage ?: "Specification generation failed.",
                exitCode = when (result.failureStage) {
                    io.docpilot.core.incremental.specification.snapshot.SnapshotExecutionFailureStage.SNAPSHOT_LOAD -> 3
                    io.docpilot.core.incremental.specification.snapshot.SnapshotExecutionFailureStage.SNAPSHOT_SAVE -> 4
                    else -> 1
                },
            )
        }

        printer.success(
            if (result.snapshotSaved) "Specification generated and snapshot saved."
            else "Specification generation completed; snapshot unchanged.",
        )
        log.info("Specification analysis and generation completed in ${result.execution.mode} mode.")
    }

    private fun snapshotValidation(
        loadResult: io.docpilot.core.incremental.specification.snapshot.SpecificationSnapshotLoadResult,
    ): String = when (loadResult) {
        io.docpilot.core.incremental.specification.snapshot.SpecificationSnapshotLoadResult.NotFound -> "NOT_FOUND"
        is io.docpilot.core.incremental.specification.snapshot.SpecificationSnapshotLoadResult.Valid -> "VALID"
        is io.docpilot.core.incremental.specification.snapshot.SpecificationSnapshotLoadResult.Invalid ->
            "${loadResult.reason}: ${loadResult.message}"
    }

    private fun parseAdrStatus(value: String): AdrStatus =
        AdrStatus.entries.firstOrNull { it.value == value.lowercase() }
            ?: throw IllegalArgumentException(
                "Unknown ADR status: $value. Expected one of: " +
                    AdrStatus.entries.joinToString { it.value },
            )

    private fun emit(content: String, output: String?) {
        if (output == null) {
            printer.content(content)
        } else {
            val path = writer.write(Path.of(output), content)
            printer.success("Generated $path")
        }
    }
}

private class SpecificationWorkflowException(message: String, val exitCode: Int) : RuntimeException(message)
