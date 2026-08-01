package io.docpilot.cli.command

import io.docpilot.cli.io.ConsolePrinter
import java.nio.file.Path

internal class BundleCommand(private val printer: ConsolePrinter = ConsolePrinter()) {
    fun execute(args: List<String>): Int = try {
        require(args.firstOrNull() == "verify") { "Expected: bundle verify" }
        var bundle: String? = null; var json = false; var index = 1
        while (index < args.size) when (val token = args[index++]) {
            "--bundle" -> { require(index < args.size) { "Missing --bundle value." }; bundle = args[index++] }
            "--json" -> json = true
            "--strict" -> Unit
            else -> error("Unknown bundle option: $token")
        }
        val result = DocumentationBundleVerifier.verify(Path.of(requireNotNull(bundle) { "Missing --bundle." }))
        if (json) printer.content("{\"schemaVersion\":1,\"status\":\"${result.status}\",\"missingFiles\":${result.missing},\"changedFiles\":${result.changed},\"brokenLinks\":${result.brokenLinks},\"diagnostics\":[${result.diagnostics.joinToString(",") { "\"${it.replace("\\", "\\\\").replace("\"", "\\\"")}\"" }}]}")
        else {
            printer.content("Bundle Status: ${result.status}"); printer.content("Missing Files: ${result.missing}")
            printer.content("Changed Files: ${result.changed}"); printer.content("Broken Links: ${result.brokenLinks}")
            result.diagnostics.forEach { printer.content("Diagnostic: $it") }; printer.content("Verification Result: ${result.status}")
        }
        result.exitCode
    } catch (e: Exception) { printer.error(e.message ?: "Bundle verification failed."); 2 }
}
