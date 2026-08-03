package io.docpilot.cli

import io.docpilot.cli.command.GenerateCommand
import io.docpilot.cli.command.BundleCommand
import io.docpilot.cli.command.ReconcileCommand
import io.docpilot.cli.command.review.ReviewCommand
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
    if (args.isNotEmpty() && args.first() == "review") {
        return ReviewCommand().execute(args.drop(1))
    }
    if (args.isNotEmpty() && args.first() == "reconcile") {
        return ReconcileCommand().execute(args.drop(1))
    }
    if (args.isNotEmpty() && args.first() == "bundle") return BundleCommand().execute(args.drop(1))

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
          docpilot generate docs --project <path> --output <directory> [--dry-run|--confirm] [--full]
            [--enrich --provider <id> --model <model> [--enrichment-target <artifact-or-section>]]
          docpilot bundle verify --bundle <bundle-root-or-manifest> [--strict] [--json]
          docpilot generate adr --project <path> --provider <id> --model <model> --title <title> --context <text> --decision <text> --consequences <text> [--alternatives <text>] [--status <status>] [--output <file>]
          docpilot generate findings --project <path> --input <file> --output <file>
          docpilot generate known-issues --project <path> --findings <file> --output <file>
          docpilot generate roadmap --project <path> --findings <file> --output <file> [--decisions <file>]
          docpilot generate executive-summary --project <path> --findings <file> --provider <id> --model <model> --output <file>
          docpilot generate adr-propose --project <path> --findings <file> --provider <id> --model <model> --output <file>
          docpilot generate adr-adopt --proposal <file> --decision accept|reject [--comment <text>] [--project <path>] --output <file>
          docpilot review prepare --project <path> --documentation <file> --provider <id> --model <model> [--bundle <file>] [--json]
          docpilot review inspect|status --project <path> (--proposal <id> | --bundle <file>) [--documentation <file>] [--json]
          docpilot review decide --project <path> (--proposal <id> | --bundle <file>) --target <id> (--accept | --reject) [--comment <text> | --comment-file <file>] [--payload-sha256 <sha>] [--json]
          docpilot review apply --project <path> (--proposal <id> | --bundle <file>) --documentation <file> [--payload-sha256 <sha>] [--json]
          docpilot review lifecycle status|verify --project <path> (--proposal <id> | --bundle <file>) [--documentation <file>] [--json]
          docpilot review lifecycle recover --project <path> (--proposal <id> | --bundle <file>) --documentation <file> [--dry-run | --confirm] [--plan-sha256 <sha>] [--json]
          docpilot review lifecycle supersede --project <path> (--proposal <id> | --bundle <file>) --replacement-proposal <id> [--dry-run | --confirm] [--plan-sha256 <sha>] [--json]
          docpilot review lifecycle archive --project <path> (--proposal <id> | --bundle <file>) [--dry-run | --confirm] [--plan-sha256 <sha>] [--json]
          docpilot reconcile preview --project <path> --project-id <id> --artifact-id <id> --path <relative-path> --base <file> --candidate <file> --manifest <file> --plan <file>
          docpilot reconcile inspect --plan <file>
          docpilot reconcile apply --project <path> --plan <file> --decision <accept-generated|keep-current|reject>
          docpilot reconcile recover --project <path>
          docpilot reconcile verify --project <path> --plan-sha256 <sha>

        Providers bundled by the distribution: fixture, fixture-failure, ollama, openai
        ADR statuses: proposed, accepted, deprecated, superseded
        """.trimIndent(),
    )
}
