package io.docpilot.cli.command.review

import io.docpilot.cli.bootstrap.CliBootstrap
import io.docpilot.cli.bootstrap.ProjectKnowledgeLoader
import io.docpilot.cli.io.AtomicDocumentationFileWriter
import io.docpilot.core.incremental.specification.IncrementalDocumentationEngine
import io.docpilot.core.incremental.specification.ai.AiIncrementalDocumentationGenerator
import io.docpilot.core.incremental.specification.ai.AiIncrementalGenerationRequest
import io.docpilot.core.incremental.specification.ai.DefaultAiIncrementalDocumentationGenerator
import io.docpilot.core.incremental.specification.review.*
import io.docpilot.core.incremental.specification.snapshot.FileSpecificationSnapshotRepository
import io.docpilot.core.incremental.specification.snapshot.SpecificationSnapshotLoadResult
import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.model.ai.AiModelId
import io.docpilot.core.specification.DefaultSpecificationBuilder
import io.docpilot.core.specification.SpecificationBuildRequest
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class ReviewCommand(
    private val bootstrap: CliBootstrap = CliBootstrap(),
    private val knowledgeLoader: ProjectKnowledgeLoader = ProjectKnowledgeLoader(),
    private val codec: JsonReviewBundleCodec = JsonReviewBundleCodec(),
    private val statusEvaluator: ReviewBundleStatusEvaluator = ReviewBundleStatusEvaluator(),
    private val writer: AtomicDocumentationFileWriter = AtomicDocumentationFileWriter(),
) {
    fun execute(tokens: List<String>): Int {
        if (tokens.isEmpty()) return renderFailure("review", 2, "CLI_USAGE_ERROR", "Missing review subcommand.", false)
        val subcommand = tokens.first()
        return try {
            val arguments = ReviewArguments.parse(tokens.drop(1))
            when (subcommand) {
                "prepare" -> prepare(arguments)
                "inspect" -> inspect(arguments)
                "status" -> status(arguments)
                "decide" -> decide(arguments)
                "apply" -> apply(arguments)
                else -> renderFailure("review $subcommand", 2, "CLI_USAGE_ERROR", "Unknown review subcommand.", arguments.json)
            }
        } catch (error: ReviewCliFailure) {
            renderFailure("review $subcommand", error.exitCode, error.status, error.message.orEmpty(), error.json)
        } catch (error: IllegalArgumentException) {
            renderFailure("review $subcommand", 2, "CLI_USAGE_ERROR", error.message.orEmpty(), tokens.contains("--json"))
        } catch (error: Exception) {
            renderFailure("review $subcommand", 70, "INTERNAL_ERROR", error.message ?: "Unexpected CLI failure.", tokens.contains("--json"))
        }
    }

    private fun prepare(args: ReviewArguments): Int {
        args.requireOnly(
            setOf("project", "documentation", "provider", "model", "bundle"),
            setOf("json"),
        )
        val projectRoot = directory(args.required("project"))
        val documentationPath = regularFile(args.required("documentation"))
        val documentation = Files.readString(documentationPath, StandardCharsets.UTF_8)
        val analysis = knowledgeLoader.analyze(projectRoot)
        val current = DefaultSpecificationBuilder().build(
            SpecificationBuildRequest(analysis.project, analysis.knowledge, analysis.sourceIndex),
        )
        val snapshot = FileSpecificationSnapshotRepository(projectRoot).load(current.project.id)
        val previous = (snapshot as? SpecificationSnapshotLoadResult.Valid)?.snapshot?.specification
            ?: throw ReviewCliFailure(5, "INVALID_BUNDLE", "A valid previous specification snapshot is required.", args.json)
        val plan = IncrementalDocumentationEngine().analyze(previous, current).plan
        val bundlePath = args.optional("bundle")?.let(::outputPath)
        val repository = bundlePath?.let(FileReviewBundleRepository::atPath)
            ?: FileReviewBundleRepository(projectRoot)
        val generator: AiIncrementalDocumentationGenerator =
            DefaultAiIncrementalDocumentationGenerator(bootstrap.createProvider(args.required("provider")))
        val workflow = DefaultPersistentDocumentationReviewWorkflow(generator, repository, codec)
        val result = workflow.prepareAndSave(
            AiIncrementalGenerationRequest(
                previous,
                current,
                plan,
                documentation,
                AiModelId(args.required("model")),
            ),
        )
        return when (result) {
            is PersistentReviewPreparationResult.Saved -> {
                val path = bundlePath ?: defaultBundlePath(projectRoot, result.bundle.proposalId)
                render(
                    "review prepare", "READY_FOR_REVIEW", 0, result.bundle, path, args.json,
                    mapOf(
                        "entryCount" to result.bundle.proposal.entries.size,
                        "missingPatchCount" to result.bundle.proposal.missingPatchTargetIds.size,
                    ),
                )
            }
            PersistentReviewPreparationResult.NoChanges ->
                render("review prepare", "NO_CHANGES", 0, null, bundlePath, args.json)
            is PersistentReviewPreparationResult.Failed ->
                renderFailure("review prepare", 6, "GENERATION_FAILED", result.message, args.json, bundlePath = bundlePath)
        }
    }

    private fun inspect(args: ReviewArguments): Int {
        args.requireOnly(setOf("project", "proposal", "bundle"), setOf("json"))
        val context = load(args)
        val report = MarkdownDocumentationReviewReportRenderer().render(
            context.bundle.proposal,
            context.bundle.decisions,
        )
        return render(
            "review inspect", "INSPECTED", 0, context.bundle, context.path, args.json,
            mapOf("report" to report),
        )
    }

    private fun status(args: ReviewArguments): Int {
        args.requireOnly(setOf("project", "proposal", "bundle", "documentation"), setOf("json"))
        val context = load(args)
        val documentation = args.optional("documentation")?.let { Files.readString(regularFile(it), StandardCharsets.UTF_8) }
        val status = statusEvaluator.evaluate(context.bundle, documentation)
        val exit = when (status.status) {
            ReviewBundleStatus.READY_TO_APPLY -> 0
            ReviewBundleStatus.PENDING_REVIEW -> 3
            ReviewBundleStatus.STALE_DOCUMENTATION -> 4
        }
        return render(
            "review status", status.status.name, exit, context.bundle, context.path, args.json,
            mapOf(
                "proposalComplete" to status.proposalComplete,
                "entryCount" to status.entryCount,
                "acceptedCount" to status.acceptedCount,
                "rejectedCount" to status.rejectedCount,
                "pendingCount" to status.pendingTargetIds.size,
                "missingPatchCount" to status.missingPatchTargetIds.size,
                "documentationFreshness" to status.documentationFreshness.name,
            ),
        )
    }

    private fun decide(args: ReviewArguments): Int {
        args.requireOnly(
            setOf("project", "proposal", "bundle", "target", "comment", "comment-file", "payload-sha256"),
            setOf("json", "accept", "reject"),
        )
        require(args.flag("accept") xor args.flag("reject")) { "Exactly one of --accept or --reject is required." }
        require(!(args.optional("comment") != null && args.optional("comment-file") != null)) {
            "--comment and --comment-file are mutually exclusive."
        }
        val context = load(args)
        val expected = args.optional("payload-sha256") ?: context.bundle.integrity.payloadSha256
        require(expected.matches(Regex("[0-9a-f]{64}"))) { "Invalid --payload-sha256." }
        val comment = when {
            args.optional("comment") != null -> args.optional("comment")
            args.optional("comment-file") != null -> readCommentFile(args.optional("comment-file")!!)
            else -> null
        }
        require(comment == null || comment.isNotBlank()) { "Decision comment must not be blank." }
        val decision = DocumentationReviewDecision(
            args.required("target"),
            if (args.flag("accept")) DocumentationReviewDisposition.ACCEPTED else DocumentationReviewDisposition.REJECTED,
            comment,
        )
        val workflow = workflow(context.repository)
        return when (val result = workflow.recordDecisions(
            context.projectId,
            context.bundle.proposalId,
            expected,
            listOf(decision),
        )) {
            is PersistentReviewUpdateResult.Saved -> render(
                "review decide", "DECISION_RECORDED", 0, result.bundle, context.path, args.json,
                mapOf("targetId" to decision.targetId, "disposition" to decision.disposition.name),
            )
            is PersistentReviewUpdateResult.Conflict -> renderFailure(
                "review decide", 4, "BUNDLE_CHANGED", "Review bundle changed.", args.json,
                context.bundle.proposalId, context.path, result.actualPayloadSha256,
            )
            is PersistentReviewUpdateResult.Failed ->
                renderFailure(
                    "review decide", 5, "INVALID_BUNDLE", result.message, args.json,
                    context.bundle.proposalId, context.path, context.bundle.integrity.payloadSha256,
                )
        }
    }

    private fun apply(args: ReviewArguments): Int {
        args.requireOnly(setOf("project", "proposal", "bundle", "documentation", "payload-sha256"), setOf("json"))
        val context = load(args)
        val documentationPath = regularFile(args.required("documentation"))
        val existing = Files.readString(documentationPath, StandardCharsets.UTF_8)
        val expected = args.optional("payload-sha256") ?: context.bundle.integrity.payloadSha256
        val result = workflow(context.repository).resumeApply(
            ResumableReviewApplyRequest(context.projectId, context.bundle.proposalId, existing, expected),
        )
        return when (result) {
            is ResumableReviewApplyResult.Applied -> {
                try {
                    writer.replace(documentationPath, existing, result.mergedDocumentation)
                } catch (error: IllegalArgumentException) {
                    return renderFailure(
                        "review apply", 4, "STALE_DOCUMENTATION", error.message.orEmpty(), args.json,
                        context.bundle.proposalId, context.path, context.bundle.integrity.payloadSha256,
                    )
                } catch (error: Exception) {
                    return renderFailure(
                        "review apply", 8, "DOCUMENT_WRITE_FAILED", error.message.orEmpty(), args.json,
                        context.bundle.proposalId, context.path, context.bundle.integrity.payloadSha256,
                    )
                }
                render(
                    "review apply", "APPLIED", 0, context.bundle, context.path, args.json,
                    mapOf(
                        "acceptedCount" to result.acceptedTargetIds.size,
                        "rejectedCount" to result.rejectedTargetIds.size,
                        "resultDocumentationSha256" to result.mergedDocumentationSha256,
                    ),
                )
            }
            is ResumableReviewApplyResult.Pending -> render(
                "review apply", "PENDING_REVIEW", 3, context.bundle, context.path, args.json,
                mapOf(
                    "pendingCount" to result.pendingTargetIds.size,
                    "missingPatchCount" to result.missingPatchTargetIds.size,
                ),
            )
            is ResumableReviewApplyResult.Conflict -> renderFailure(
                "review apply", 4,
                if (result.reason == ResumableReviewConflict.BUNDLE_CHANGED) "BUNDLE_CHANGED" else "STALE_DOCUMENTATION",
                result.message, args.json, context.bundle.proposalId, context.path, context.bundle.integrity.payloadSha256,
            )
            is ResumableReviewApplyResult.InvalidBundle -> renderFailure(
                "review apply", 5, "INVALID_BUNDLE", result.message, args.json,
                context.bundle.proposalId, context.path, context.bundle.integrity.payloadSha256,
            )
        }
    }

    private fun load(args: ReviewArguments): BundleContext {
        val projectRoot = directory(args.required("project"))
        val projectId = ProjectKnowledgeLoader().analyze(projectRoot).project.id
        val proposal = args.optional("proposal")
        val explicitPath = args.optional("bundle")?.let(::regularFile)
        require((proposal == null) xor (explicitPath == null)) {
            "Exactly one of --proposal or --bundle is required."
        }
        val repository: ReviewBundleRepository
        val proposalId: String
        val path: Path
        if (explicitPath != null) {
            val decoded = codec.decode(Files.readString(explicitPath, StandardCharsets.UTF_8), projectId)
            if (decoded !is ReviewBundleLoadResult.Valid) {
                throw ReviewCliFailure(5, "INVALID_BUNDLE", "Review bundle is invalid.", args.json)
            }
            proposalId = decoded.bundle.proposalId
            repository = FileReviewBundleRepository.atPath(explicitPath, codec)
            path = explicitPath
        } else {
            proposalId = requireNotNull(proposal)
            repository = FileReviewBundleRepository(projectRoot, codec)
            path = defaultBundlePath(projectRoot, proposalId)
        }
        val loaded = repository.load(projectId, proposalId)
        if (loaded !is ReviewBundleLoadResult.Valid) {
            throw ReviewCliFailure(5, "INVALID_BUNDLE", "Review bundle is missing or invalid.", args.json)
        }
        return BundleContext(projectId, loaded.bundle, repository, path)
    }

    private fun workflow(repository: ReviewBundleRepository) =
        DefaultPersistentDocumentationReviewWorkflow(UnusedGenerator, repository, codec)

    private fun defaultBundlePath(projectRoot: Path, proposalId: String): Path =
        projectRoot.resolve(ReviewBundleFormat.DEFAULT_DIRECTORY)
            .resolve("review-${proposalId.removePrefix("review:")}.json").toAbsolutePath().normalize()

    private fun directory(value: String): Path = Path.of(value).toAbsolutePath().normalize().also {
        require(Files.isDirectory(it)) { "Project path is not a directory: $it" }
    }
    private fun regularFile(value: String): Path = Path.of(value).toAbsolutePath().normalize().also {
        require(Files.isRegularFile(it)) { "Path is not a regular file: $it" }
    }
    private fun outputPath(value: String): Path = Path.of(value).toAbsolutePath().normalize()
    private fun readCommentFile(value: String): String =
        Files.readString(regularFile(value), StandardCharsets.UTF_8).removeSuffix("\r\n").removeSuffix("\n")

    private fun render(
        command: String,
        status: String,
        exitCode: Int,
        bundle: StoredReviewBundle?,
        path: Path?,
        json: Boolean,
        data: Map<String, Any?> = emptyMap(),
    ): Int {
        val proposal = bundle?.proposalId
        val sha = bundle?.integrity?.payloadSha256
        if (json) {
            println(jsonEnvelope(command, status, exitCode, proposal, path, sha, data, null))
        } else {
            println("Command: $command")
            println("Status: $status")
            println("Proposal ID: ${proposal ?: "unavailable"}")
            println("Bundle Path: ${path?.toAbsolutePath()?.normalize() ?: "unavailable"}")
            println("Payload SHA-256: ${sha ?: "unavailable"}")
            data.toSortedMap().forEach { (key, value) -> println("${key.toDisplayName()}: $value") }
        }
        return exitCode
    }

    private fun renderFailure(
        command: String,
        exitCode: Int,
        status: String,
        message: String,
        json: Boolean,
        proposalId: String? = null,
        bundlePath: Path? = null,
        payloadSha256: String? = null,
    ): Int {
        if (json) {
            println(jsonEnvelope(command, status, exitCode, proposalId, bundlePath, payloadSha256, emptyMap(), message))
        } else {
            println("Command: $command")
            println("Status: $status")
            println("Proposal ID: ${proposalId ?: "unavailable"}")
            println("Bundle Path: ${bundlePath?.toAbsolutePath()?.normalize() ?: "unavailable"}")
            println("Payload SHA-256: ${payloadSha256 ?: "unavailable"}")
            System.err.println("[ERROR] $message")
        }
        return exitCode
    }

    private fun jsonEnvelope(
        command: String,
        status: String,
        exitCode: Int,
        proposalId: String?,
        bundlePath: Path?,
        payloadSha256: String?,
        data: Map<String, Any?>,
        error: String?,
    ): String = buildString {
        append('{')
        append("\"outputFormatVersion\":1,")
        append("\"command\":").appendJson(command).append(',')
        append("\"status\":").appendJson(status).append(',')
        append("\"exitCode\":").append(exitCode).append(',')
        append("\"proposalId\":").appendNullable(proposalId).append(',')
        append("\"bundlePath\":").appendNullable(bundlePath?.toAbsolutePath()?.normalize()?.toString()).append(',')
        append("\"payloadSha256\":").appendNullable(payloadSha256).append(',')
        append("\"data\":{")
        data.toSortedMap().entries.forEachIndexed { index, entry ->
            if (index > 0) append(',')
            appendJson(entry.key).append(':').appendJsonValue(entry.value)
        }
        append("},\"error\":")
        if (error == null) append("null") else {
            append("{\"code\":").appendJson(status).append(",\"message\":").appendJson(error).append('}')
        }
        append('}')
    }

    private fun String.toDisplayName(): String =
        replace(Regex("([a-z])([A-Z])"), "$1 $2").replaceFirstChar(Char::uppercase)
    private fun StringBuilder.appendNullable(value: String?): StringBuilder =
        if (value == null) append("null") else appendJson(value)
    private fun StringBuilder.appendJsonValue(value: Any?): StringBuilder = when (value) {
        null -> append("null")
        is Number, is Boolean -> append(value)
        else -> appendJson(value.toString())
    }
    private fun StringBuilder.appendJson(value: String): StringBuilder {
        append('"')
        value.forEach { c -> when (c) {
            '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n"); '\r' -> append("\\r")
            '\t' -> append("\\t"); else -> append(c)
        } }
        return append('"')
    }

    private data class BundleContext(
        val projectId: String,
        val bundle: StoredReviewBundle,
        val repository: ReviewBundleRepository,
        val path: Path,
    )

    private class ReviewCliFailure(
        val exitCode: Int,
        val status: String,
        override val message: String,
        val json: Boolean,
    ) : RuntimeException(message)

    private object UnusedGenerator : AiIncrementalDocumentationGenerator {
        override fun generate(request: AiIncrementalGenerationRequest) =
            throw UnsupportedOperationException("Generator is not used by this command.")
    }
}

private class ReviewArguments private constructor(
    private val values: Map<String, String>,
    private val flags: Set<String>,
) {
    val json: Boolean get() = flag("json")
    fun required(name: String): String = optional(name)
        ?: throw IllegalArgumentException("Missing required option: --$name")
    fun optional(name: String): String? = values[name]?.takeIf(String::isNotBlank)
    fun flag(name: String): Boolean = name in flags
    fun requireOnly(valueNames: Set<String>, flagNames: Set<String>) {
        val unknown = (values.keys - valueNames) + (flags - flagNames)
        require(unknown.isEmpty()) { "Unknown option: --${unknown.sorted().first()}" }
    }

    companion object {
        fun parse(tokens: List<String>): ReviewArguments {
            val values = linkedMapOf<String, String>()
            val flags = linkedSetOf<String>()
            val booleanFlags = setOf("json", "accept", "reject")
            var index = 0
            while (index < tokens.size) {
                val token = tokens[index]
                require(token.startsWith("--") && token.length > 2) { "Unexpected argument: $token" }
                val name = token.removePrefix("--")
                require(name !in values && name !in flags) { "Duplicate option: --$name" }
                if (name in booleanFlags) {
                    flags += name
                    index++
                } else {
                    require(index + 1 < tokens.size && !tokens[index + 1].startsWith("--")) {
                        "Missing value for option: --$name"
                    }
                    values[name] = tokens[index + 1]
                    index += 2
                }
            }
            return ReviewArguments(values, flags)
        }
    }
}
