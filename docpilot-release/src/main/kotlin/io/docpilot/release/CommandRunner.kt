package io.docpilot.release

import java.nio.file.Files
import java.nio.file.Path

public data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

public class CommandRunner {
    public fun run(
        workingDirectory: Path,
        arguments: List<String>,
        captureOutput: Boolean = true,
    ): CommandResult {
        require(arguments.isNotEmpty())
        require(Files.isDirectory(workingDirectory))
        val process = ProcessBuilder(arguments)
            .directory(workingDirectory.toFile())
            .redirectErrorStream(true)
            .start()
        val stdout = if (captureOutput) process.inputStream.bufferedReader().readText() else ""
        return CommandResult(process.waitFor(), stdout, "")
    }
}
