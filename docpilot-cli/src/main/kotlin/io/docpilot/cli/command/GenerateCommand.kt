package io.docpilot.cli.command

import io.docpilot.cli.bootstrap.CliBootstrap
import io.docpilot.cli.bootstrap.ProjectKnowledgeLoader
import io.docpilot.cli.io.ConsolePrinter
import io.docpilot.cli.io.OutputWriter
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
) {
    fun execute(args: List<String>): Int =
        try {
            require(args.isNotEmpty()) { "Missing generation type." }
            when (args.first()) {
                "architecture" -> architecture(CliArguments.parse(args.drop(1)))
                "adr" -> adr(CliArguments.parse(args.drop(1)))
                else -> throw IllegalArgumentException("Unknown generation type: ${args.first()}")
            }
            0
        } catch (exception: Exception) {
            printer.error(exception.message ?: "Generation failed.")
            1
        }

    private fun architecture(args: CliArguments) {
        val project = Path.of(args.required("project"))
        val provider = args.required("provider")
        val model = AiModelId(args.required("model"))
        val facade = bootstrap.create(provider)
        val document = facade.generateArchitecture(
            ArchitectureGenerationRequest(
                knowledge = knowledgeLoader.load(project),
                modelId = model,
                title = args.optional("title") ?: ArchitectureGenerationRequest.DEFAULT_TITLE,
            ),
        )
        emit(renderer.render(document), args.optional("output"))
    }

    private fun adr(args: CliArguments) {
        val project = Path.of(args.required("project"))
        val provider = args.required("provider")
        val model = AiModelId(args.required("model"))
        val facade = bootstrap.create(provider)
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
