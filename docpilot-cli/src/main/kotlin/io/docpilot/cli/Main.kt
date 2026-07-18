package io.docpilot.cli

import io.docpilot.cli.command.GenerateCommand
import io.docpilot.cli.io.ConsolePrinter
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val exitCode = runCli(args.toList())
    if (exitCode != 0) exitProcess(exitCode)
}

fun runCli(args: List<String>): Int {
    if (args.isNotEmpty() && args.first() == "generate") {
        return GenerateCommand().execute(args.drop(1))
    }

    printUsage()
    return if (args.isEmpty() || args == listOf("help") || args == listOf("--help")) 0 else 2
}

private fun printUsage() {
    ConsolePrinter().content(
        """
        DocPilot CLI

        Usage:
          docpilot generate architecture --project <path> --provider <id> --model <model> [--title <title>] [--output <file>]
          docpilot generate specification --project <path> [--output <directory>]
          docpilot generate adr --project <path> --provider <id> --model <model> --title <title> --context <text> --decision <text> --consequences <text> [--alternatives <text>] [--status <status>] [--output <file>]

        Providers bundled by the distribution: ollama, openai
        ADR statuses: proposed, accepted, deprecated, superseded
        """.trimIndent(),
    )
}
