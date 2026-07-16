package io.docpilot.core.cli

import io.docpilot.core.loader.LocalProjectLoader
import io.docpilot.core.render.ProjectSummaryMarkdownRenderer
import io.docpilot.core.scanner.LocalSourceScanner
import io.docpilot.core.summary.DefaultProjectSummaryBuilder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.size !in 2..3 || args[0] != "analyze") {
        System.err.println("Usage: docpilot analyze <project-path> [output-path]")
        exitProcess(2)
    }

    val projectPath = Path.of(args[1]).toAbsolutePath().normalize()
    val project = LocalProjectLoader().load(projectPath)
    val inventory = LocalSourceScanner().scan(project)
    val summary = DefaultProjectSummaryBuilder().build(inventory)
    val artifact = ProjectSummaryMarkdownRenderer().render(summary)

    val output = args.getOrNull(2)?.let { Path.of(it).toAbsolutePath().normalize() }
        ?: project.path.resolve(artifact.relativePath)

    output.parent?.createDirectories()
    Files.writeString(output, artifact.content, StandardCharsets.UTF_8)

    println("Project summary generated:")
    println(output)
}
