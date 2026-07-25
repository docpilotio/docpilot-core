package io.docpilot.release

import java.nio.file.Path
import kotlin.system.exitProcess

public fun main(args: Array<String>) {
    val exit = try {
        when (args.firstOrNull()) {
            "verify" -> verify(args.drop(1))
            "help", "--help", "-h", null -> {
                printUsage()
                0
            }
            else -> {
                System.err.println("Unknown command: ${args.first()}")
                printUsage()
                2
            }
        }
    } catch (failure: IllegalArgumentException) {
        System.err.println(failure.message ?: "Invalid evidence.")
        5
    } catch (failure: Exception) {
        System.err.println(failure.message ?: "Internal release verifier failure.")
        70
    }
    exitProcess(exit)
}

private fun verify(args: List<String>): Int {
    val options = parseOptions(args)
    val root = options["--repository"]?.let(Path::of)
        ?: throw IllegalArgumentException("--repository is required.")
    val manifest = options["--manifest"]?.let(Path::of)
        ?: throw IllegalArgumentException("--manifest is required.")
    val result = ReleaseEvidenceVerifier().verify(root, manifest)
    println("Release Gate: ${result.result}")
    println("Failures: ${if (result.failures.isEmpty()) "None" else result.failures.joinToString()}")
    return if (result.result == EvidenceResult.PASS) 0 else 3
}

private fun parseOptions(args: List<String>): Map<String, String> {
    require(args.size % 2 == 0) { "Options require values." }
    val result = linkedMapOf<String, String>()
    args.chunked(2).forEach { (key, value) ->
        require(key in setOf("--repository", "--manifest")) { "Unknown option: $key" }
        require(key !in result) { "Duplicate option: $key" }
        require(value.isNotBlank()) { "$key must not be blank." }
        result[key] = value
    }
    return result
}

private fun printUsage() {
    println("Usage: docpilot-release verify --repository <path> --manifest <path>")
}
