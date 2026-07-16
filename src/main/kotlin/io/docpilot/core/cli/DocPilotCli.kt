package io.docpilot.core.cli

import io.docpilot.core.extractor.SimpleKotlinSymbolExtractor
import io.docpilot.core.indexer.DefaultProjectSourceIndexer
import io.docpilot.core.lexer.SimpleKotlinLexer
import io.docpilot.core.loader.LocalProjectLoader
import io.docpilot.core.model.RenderedArtifact
import io.docpilot.core.render.ProjectSummaryMarkdownRenderer
import io.docpilot.core.render.SourceIndexMarkdownRenderer
import io.docpilot.core.scanner.LocalSourceScanner
import io.docpilot.core.summary.DefaultProjectSummaryBuilder
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val exitCode = runCli(args)

    if (exitCode != 0) {
        exitProcess(exitCode)
    }
}

fun runCli(
    args: Array<String>,
    out: PrintStream = System.out,
    err: PrintStream = System.err,
): Int {
    if (args.size != 2 || args[0] != "analyze") {
        printUsage(err)
        return 2
    }

    return try {
        val projectPath = Path.of(args[1])
            .toAbsolutePath()
            .normalize()

        val project = LocalProjectLoader().load(projectPath)
        val inventory = LocalSourceScanner().scan(project)

        val projectSummary =
            DefaultProjectSummaryBuilder().build(inventory)

        val sourceIndex =
            DefaultProjectSourceIndexer(
                lexer = SimpleKotlinLexer(),
                extractor = SimpleKotlinSymbolExtractor(),
            ).index(inventory)

        val artifacts = listOf(
            ProjectSummaryMarkdownRenderer()
                .render(projectSummary),
            SourceIndexMarkdownRenderer()
                .render(sourceIndex),
        )

        artifacts.forEach { artifact ->
            val outputPath = writeArtifact(
                projectPath = project.path,
                artifact = artifact,
            )

            out.println("Generated: $outputPath")
        }

        0
    } catch (exception: Exception) {
        err.println(
            "Analysis failed: ${
                exception.message ?: "Unknown error"
            }",
        )
        1
    }
}

private fun writeArtifact(
    projectPath: Path,
    artifact: RenderedArtifact,
): Path {
    val outputPath = projectPath
        .resolve(artifact.relativePath)
        .toAbsolutePath()
        .normalize()

    outputPath.parent?.createDirectories()

    Files.writeString(
        outputPath,
        artifact.content,
        StandardCharsets.UTF_8,
    )

    return outputPath
}

private fun printUsage(
    stream: PrintStream,
) {
    stream.println(
        "Usage: docpilot analyze <project-path>",
    )
}
