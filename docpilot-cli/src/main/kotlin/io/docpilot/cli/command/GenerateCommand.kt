package io.docpilot.cli.command

import io.docpilot.cli.bootstrap.CliBootstrap
import io.docpilot.cli.bootstrap.ProjectKnowledgeLoader
import io.docpilot.cli.command.finding.FindingCommands
import io.docpilot.cli.io.ConsolePrinter
import io.docpilot.cli.io.OutputWriter
import io.docpilot.cli.logging.ProjectLogSession
import io.docpilot.core.document.DocumentRenderer
import io.docpilot.core.generator.adr.AdrGenerationRequest
import io.docpilot.core.generator.adr.AdrStatus
import io.docpilot.core.generator.architecture.ArchitectureGenerationRequest
import io.docpilot.core.model.ai.AiModelId
import java.nio.file.Path

class GenerateCommand internal constructor(
    private val bootstrap: CliBootstrap = CliBootstrap(),
    private val knowledgeLoader: ProjectKnowledgeLoader = ProjectKnowledgeLoader(),
    private val renderer: DocumentRenderer = DocumentRenderer(),
    private val writer: OutputWriter = OutputWriter(),
    private val printer: ConsolePrinter = ConsolePrinter(),
    private val specificationWorkflow: SpecificationGenerateWorkflow = DefaultSpecificationGenerateWorkflow(),
    private val documentationWorkflow: DocumentationGenerationWorkflow = DefaultDocumentationGenerationWorkflow(
        providerResolver = { bootstrap.createProvider(it) },
    ),
    private val findingCommands: FindingCommands = FindingCommands(bootstrap, knowledgeLoader, writer, printer),
) {
    fun execute(args: List<String>): Int =
        try {
            require(args.isNotEmpty()) { "Missing generation type." }
            if (args.first() == "docs") return documentation(args.drop(1))
            when (args.first()) {
                "architecture" -> architecture(CliArguments.parse(args.drop(1)))
                "adr" -> adr(CliArguments.parse(args.drop(1)))
                "specification" -> specification(CliArguments.parse(args.drop(1)))
                "findings" -> findingCommands.findings(CliArguments.parse(args.drop(1)))
                "known-issues" -> findingCommands.knownIssues(CliArguments.parse(args.drop(1)))
                "roadmap" -> findingCommands.roadmap(CliArguments.parse(args.drop(1)))
                "executive-summary" -> findingCommands.executiveSummary(CliArguments.parse(args.drop(1)))
                "adr-propose" -> findingCommands.adrPropose(CliArguments.parse(args.drop(1)))
                "adr-adopt" -> findingCommands.adrAdopt(CliArguments.parse(args.drop(1)))
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

    private fun documentation(tokens: List<String>): Int {
        val options = GenerateDocsOptionsParser.parse(tokens)
        val result = documentationWorkflow.execute(options)
        if (options.json) printer.content(renderJson(result)) else renderText(result)
        return result.exitCode
    }

    private fun renderText(result: DocumentationGenerationResult) {
        printer.content("Command: generate docs")
        printer.content("Mode: ${result.mode}")
        printer.content("Status: ${result.status}")
        printer.content("Project: ${result.projectId ?: "UNKNOWN"}")
        printer.content("Output: ${result.outputRoot}")
        printer.content("Profile: ${result.profile}")
        printer.content("Snapshot Status: ${result.snapshotStatus}")
        printer.content("Artifact Plan: ${result.artifacts.size}")
        printer.content("Created: ${result.artifacts.count { it.operation.name == "CREATE" }}")
        printer.content("Updated: ${result.artifacts.count { it.operation.name == "UPDATE" }}")
        printer.content("Retained: ${result.artifacts.count { it.operation.name == "KEEP" }}")
        printer.content("Plan SHA-256: ${result.planSha256 ?: "NONE"}")
        printer.content("Bundle Status: ${result.verification ?: "NOT_PERSISTED"}")
        printer.content("Bundle ID: ${result.bundle?.bundleId ?: "NONE"}")
        printer.content("Manifest Path: ${result.bundle?.let { "${result.outputRoot}/${DocumentationBundleFormat.MANIFEST_PATH}" } ?: "NONE"}")
        printer.content("Manifest SHA-256: ${result.bundle?.manifestSha256 ?: "NONE"}")
        printer.content("Snapshot SHA-256: ${result.bundle?.snapshotSha256 ?: "NONE"}")
        printer.content("Profile SHA-256: ${result.bundle?.profileSha256 ?: "NONE"}")
        printer.content("Artifact Aggregate SHA-256: ${result.bundle?.artifactAggregateSha256 ?: "NONE"}")
        printer.content("Receipt ID: ${result.bundle?.receiptId ?: "NONE"}")
        printer.content("Receipt SHA-256: ${result.bundle?.receiptSha256 ?: "NONE"}")
        printer.content("Link Status: ${result.bundle?.linkStatus ?: "UNKNOWN"}")
        result.artifacts.forEach { printer.content("${it.operation} ${it.artifactId.value} ${it.relativePath}") }
        result.diagnostics.forEach { printer.content("Diagnostic: $it") }
        printer.content("Result: ${result.status}")
    }

    private fun renderJson(result: DocumentationGenerationResult): String = buildString {
        append('{')
        append("\"schemaVersion\":1,")
        append("\"status\":\"").append(result.status).append("\",")
        append("\"mode\":\"").append(result.mode).append("\",")
        append("\"projectId\":").append(result.projectId?.let { "\"${json(it)}\"" } ?: "null").append(',')
        append("\"outputRoot\":\"").append(json(result.outputRoot)).append("\",")
        append("\"profile\":\"").append(json(result.profile)).append("\",")
        append("\"snapshotStatus\":\"").append(json(result.snapshotStatus)).append("\",")
        append("\"planSha256\":").append(result.planSha256?.let { "\"$it\"" } ?: "null").append(',')
        append("\"snapshotWritten\":").append(result.snapshotWritten).append(',')
        append("\"bundleId\":").append(result.bundle?.let { "\"${json(it.bundleId)}\"" } ?: "null").append(',')
        append("\"manifestSha256\":").append(result.bundle?.let { "\"${it.manifestSha256}\"" } ?: "null").append(',')
        append("\"artifactAggregateSha256\":").append(result.bundle?.let { "\"${it.artifactAggregateSha256}\"" } ?: "null").append(',')
        append("\"receiptId\":").append(result.bundle?.let { "\"${json(it.receiptId)}\"" } ?: "null").append(',')
        append("\"receiptSha256\":").append(result.bundle?.let { "\"${it.receiptSha256}\"" } ?: "null").append(',')
        append("\"linkStatus\":").append(result.bundle?.let { "\"${it.linkStatus}\"" } ?: "null").append(',')
        append("\"verification\":").append(result.verification?.let { "\"$it\"" } ?: "null").append(',')
        append("\"enrichments\":[")
        result.enrichments.forEachIndexed { index, record ->
            if (index > 0) append(',')
            append("{\"artifactId\":\"").append(json(record.targetArtifactId)).append("\",\"sectionId\":\"")
                .append(json(record.targetSectionId)).append("\",\"provider\":")
                .append(record.providerId?.let { "\"${json(it)}\"" } ?: "null").append(",\"model\":")
                .append(record.model?.let { "\"${json(it)}\"" } ?: "null").append(",\"status\":\"")
                .append(record.status).append("\",\"canonicalInputIdentity\":\"").append(record.canonicalInputIdentity)
                .append("\",\"promptTemplateIdentity\":\"").append(record.promptTemplateIdentity)
                .append("\",\"narrativeSha256\":").append(record.narrativeSha256?.let { "\"$it\"" } ?: "null")
                .append(",\"providerInvoked\":").append(record.providerInvoked).append(",\"cached\":").append(record.cached).append('}')
        }
        append("],")
        append("\"artifacts\":[")
        result.artifacts.forEachIndexed { index, artifact ->
            if (index > 0) append(',')
            append("{\"artifactId\":\"").append(json(artifact.artifactId.value))
            append("\",\"documentType\":\"").append(artifact.documentType)
            append("\",\"relativePath\":\"").append(json(artifact.relativePath))
            append("\",\"action\":\"").append(artifact.operation)
            append("\",\"contentSha256\":\"").append(artifact.contentSha256).append("\"}")
        }
        append("],\"diagnostics\":[")
        result.diagnostics.forEachIndexed { index, diagnostic ->
            if (index > 0) append(','); append('"').append(json(diagnostic)).append('"')
        }
        append("]}")
    }

    private fun json(value: String): String = buildString {
        value.forEach { character -> when (character) {
            '\\' -> append("\\\\"); '"' -> append("\\\""); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
            else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
        } }
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
        val outputRoot = args.optional("output")?.let { Path.of(it) }
            ?: project.resolve(DEFAULT_OUTPUT_ROOT)
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

    private companion object {
        const val DEFAULT_OUTPUT_ROOT = "docpilot"
    }
}

private class SpecificationWorkflowException(message: String, val exitCode: Int) : RuntimeException(message)
