package io.docpilot.core.cli

import io.docpilot.core.extractor.SimpleKotlinSymbolExtractor
import io.docpilot.core.indexer.DefaultProjectSourceIndexer
import io.docpilot.core.knowledge.DefaultKnowledgeGraphBuilder
import io.docpilot.core.lexer.SimpleKotlinLexer
import io.docpilot.core.loader.LocalProjectLoader
import io.docpilot.core.model.RenderedArtifact
import io.docpilot.core.model.plugin.PluginCategory
import io.docpilot.core.model.plugin.PluginContext
import io.docpilot.core.model.plugin.PluginMessageLevel
import io.docpilot.core.model.plugin.PluginPipelineResult
import io.docpilot.core.model.plugin.PluginStatus
import io.docpilot.core.plugin.DefaultPluginRuntime
import io.docpilot.core.prompt.DefaultPromptPackageBuilder
import io.docpilot.core.render.KnowledgeGraphJsonRenderer
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
): Int =
    when {
        args.contentEquals(arrayOf("plugins")) ->
            runPluginsCommand(out)

        args.size == 2 && args[0] == "analyze" ->
            runAnalyzeCommand(
                projectArgument = args[1],
                out = out,
                err = err,
            )

        else -> {
            printUsage(err)
            2
        }
    }

internal fun runAnalyzeCommand(
    projectArgument: String,
    out: PrintStream,
    err: PrintStream,
    pluginRuntime: DefaultPluginRuntime =
        DefaultPluginRuntime.discover(),
): Int =
    try {
        val projectPath = Path.of(projectArgument)
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

        val analysisPlugins = pluginRuntime.pipeline.execute(
            category = PluginCategory.ANALYSIS,
            context = PluginContext(
                sourceIndex = sourceIndex,
            ),
        )

        printPluginResult(
            result = analysisPlugins,
            out = out,
            err = err,
        )

        if (analysisPlugins.status == PluginStatus.FAILED) {
            return 1
        }

        val knowledge =
            DefaultKnowledgeGraphBuilder()
                .buildWithEvidence(sourceIndex)

        val promptPackage =
            DefaultPromptPackageBuilder().build(knowledge)

        val outputPlugins = pluginRuntime.pipeline.execute(
            category = PluginCategory.OUTPUT,
            context = PluginContext(
                sourceIndex = sourceIndex,
                knowledge = knowledge,
                promptPackage = promptPackage,
            ),
        )

        val artifacts = buildList {
            add(
                ProjectSummaryMarkdownRenderer()
                    .render(projectSummary),
            )
            add(
                SourceIndexMarkdownRenderer()
                    .render(sourceIndex),
            )
            add(
                KnowledgeGraphJsonRenderer()
                    .render(knowledge.graph),
            )
            addAll(promptPackage.artifacts)
            addAll(analysisPlugins.artifacts)
            addAll(outputPlugins.artifacts)
        }

        artifacts.forEach { artifact ->
            val outputPath = writeArtifact(
                projectPath = project.path,
                artifact = artifact,
            )

            out.println("Generated: $outputPath")
        }

        printPluginResult(
            result = outputPlugins,
            out = out,
            err = err,
        )

        if (outputPlugins.status == PluginStatus.FAILED) {
            1
        } else {
            0
        }
    } catch (exception: Exception) {
        err.println(
            "Analysis failed: ${
                exception.message ?: "Unknown error"
            }",
        )
        1
    }

private fun printPluginResult(
    result: PluginPipelineResult,
    out: PrintStream,
    err: PrintStream,
) {
    result.executions.forEach { execution ->
        out.println(
            "Plugin: ${execution.pluginId} " +
                "[${execution.result.status}]",
        )
    }

    result.messages.forEach { message ->
        val stream =
            if (message.level == PluginMessageLevel.ERROR) {
                err
            } else {
                out
            }

        stream.println(
            "Plugin ${message.level}: ${message.text}",
        )
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
    stream.println("Usage: docpilot analyze <project-path>")
    stream.println("       docpilot plugins")
}
