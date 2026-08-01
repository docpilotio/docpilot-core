package io.docpilot.cli.logging

import io.docpilot.core.api.AiProvider
import io.docpilot.core.model.ai.AiGenerationResult
import io.docpilot.core.model.ai.AiRequest
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger

/** Writes one CLI execution's operational log and AI payloads below the target project. */
class ProjectLogSession private constructor(
    private val directory: Path,
    private val clock: Clock,
) {
    private val sequence = AtomicInteger()
    private val operationLog = directory.resolve("operations.log")

    fun info(message: String) = append("INFO", message)

    fun error(message: String) = append("ERROR", message)

    fun logging(provider: AiProvider): AiProvider = object : AiProvider {
        override val descriptor = provider.descriptor

        override fun generate(request: AiRequest): AiGenerationResult {
            val call = sequence.incrementAndGet().toString().padStart(3, '0')
            write(directory.resolve("ai-$call-prompt.txt"), renderRequest(request))
            info("AI request $call sent to ${descriptor.id} using model ${request.modelId}.")

            val result = try {
                provider.generate(request)
            } catch (exception: Exception) {
                write(
                    directory.resolve("ai-$call-response.txt"),
                    "status=exception\ntype=${exception::class.qualifiedName}\nmessage=${exception.message.orEmpty()}\n",
                )
                error("AI request $call raised ${exception::class.simpleName}.")
                throw exception
            }

            write(directory.resolve("ai-$call-response.txt"), renderResult(result))
            when (result) {
                is AiGenerationResult.Success -> info("AI request $call completed: ${result.response.finishReason}.")
                is AiGenerationResult.Failure -> error("AI request $call failed: ${result.error.code}.")
            }
            return result
        }
    }

    private fun append(level: String, message: String) {
        val line = "${Instant.now(clock)} [$level] $message${System.lineSeparator()}"
        Files.writeString(
            operationLog,
            line,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )
    }

    private fun write(path: Path, content: String) {
        Files.writeString(
            path,
            content,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE_NEW,
        )
    }

    private fun renderRequest(request: AiRequest): String = buildString {
        appendLine("model=${request.modelId}")
        appendLine("temperature=${request.temperature ?: "default"}")
        appendLine("maxOutputTokens=${request.maxOutputTokens ?: "default"}")
        appendLine("responseFormat=${request.responseFormat}")
        request.messages.forEachIndexed { index, message ->
            appendLine()
            appendLine("--- message ${index + 1} (${message.role}) ---")
            appendLine(message.content)
        }
    }

    private fun renderResult(result: AiGenerationResult): String = when (result) {
        is AiGenerationResult.Success -> buildString {
            appendLine("status=success")
            appendLine("provider=${result.response.providerId}")
            appendLine("model=${result.response.modelId}")
            appendLine("finishReason=${result.response.finishReason}")
            result.response.usage?.let {
                appendLine("inputTokens=${it.inputTokens}")
                appendLine("outputTokens=${it.outputTokens}")
            }
            appendLine()
            append(result.response.content)
        }
        is AiGenerationResult.Failure -> buildString {
            appendLine("status=failure")
            appendLine("code=${result.error.code}")
            appendLine("provider=${result.error.providerId ?: "unknown"}")
            appendLine("retryable=${result.error.retryable}")
            appendLine("message=${result.error.message}")
        }
    }

    companion object {
        private val SESSION_TIME = DateTimeFormatter
            .ofPattern("uuuuMMdd-HHmmss-SSS")
            .withZone(ZoneOffset.UTC)

        fun create(projectRoot: Path, clock: Clock = Clock.systemUTC()): ProjectLogSession {
            val root = projectRoot.toAbsolutePath().normalize()
            require(Files.isDirectory(root)) { "Project path is not a directory: $root" }
            val logRoot = root.resolve("docpilot").resolve("log")
            Files.createDirectories(logRoot)
            val baseName = SESSION_TIME.format(Instant.now(clock))
            var suffix = 0
            while (true) {
                val name = if (suffix == 0) baseName else "$baseName-$suffix"
                val directory = logRoot.resolve(name)
                try {
                    Files.createDirectory(directory)
                    return ProjectLogSession(directory, clock)
                } catch (_: FileAlreadyExistsException) {
                    suffix++
                }
            }
        }
    }
}
