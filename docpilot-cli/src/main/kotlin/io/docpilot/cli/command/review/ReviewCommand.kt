package io.docpilot.cli.command.review

import io.docpilot.cli.bootstrap.CliBootstrap
import io.docpilot.cli.bootstrap.ProjectKnowledgeLoader
import io.docpilot.cli.io.AtomicDocumentationFileWriter
import io.docpilot.cli.logging.ProjectLogSession
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
        val commandName = if (subcommand == "lifecycle" && tokens.size > 1) {
            "review lifecycle ${tokens[1]}"
        } else {
            "review $subcommand"
        }
        return try {
            if (subcommand == "lifecycle") {
                lifecycle(tokens.drop(1))
            } else {
                val arguments = ReviewArguments.parse(tokens.drop(1))
                when (subcommand) {
                    "prepare" -> prepare(arguments)
                    "inspect" -> inspect(arguments)
                    "status" -> status(arguments)
                    "decide" -> decide(arguments)
                    "apply" -> apply(arguments)
                    else -> renderFailure(commandName, 2, "CLI_USAGE_ERROR", "Unknown review subcommand.", arguments.json)
                }
            }
        } catch (error: ReviewCliFailure) {
            renderFailure(commandName, error.exitCode, error.status, error.message.orEmpty(), error.json)
        } catch (error: IllegalArgumentException) {
            renderFailure(commandName, 2, "CLI_USAGE_ERROR", error.message.orEmpty(), tokens.contains("--json"))
        } catch (error: Exception) {
            renderFailure(commandName, 70, "INTERNAL_ERROR", error.message ?: "Unexpected CLI failure.", tokens.contains("--json"))
        }
    }

    private fun lifecycle(tokens: List<String>): Int {
        require(tokens.isNotEmpty()) { "Missing lifecycle subcommand." }
        val subcommand = tokens.first()
        val args = ReviewArguments.parse(tokens.drop(1))
        return when (subcommand) {
            "status" -> lifecycleStatus(args)
            "verify" -> lifecycleVerify(args)
            "recover" -> lifecycleRecover(args)
            "supersede" -> lifecycleSupersede(args)
            "archive" -> lifecycleArchive(args)
            else -> renderFailure(
                "review lifecycle $subcommand",
                2,
                "CLI_USAGE_ERROR",
                "Unknown lifecycle subcommand.",
                args.json,
            )
        }
    }

    private fun lifecycleStatus(args: ReviewArguments): Int {
        args.requireOnly(setOf("project", "proposal", "bundle", "documentation"), setOf("json"))
        val context = load(args)
        val result = lifecycleOperations(context).status(
            LifecycleStatusRequest(
                context.projectId,
                context.bundle.proposalId,
                args.optional("documentation")?.let { FileDocumentationResource(regularFile(it)) },
            ),
        )
        return when (result) {
            is LifecycleStatusResult.Available -> render(
                "review lifecycle status",
                "LIFECYCLE_STATUS",
                0,
                context.bundle,
                context.path,
                args.json,
                statusData(result.status),
            )
            is LifecycleStatusResult.Failure -> renderLifecycleFailure(
                "review lifecycle status", result.reason, result.message, context, args.json,
            )
        }
    }

    private fun lifecycleVerify(args: ReviewArguments): Int {
        args.requireOnly(setOf("project", "proposal", "bundle", "documentation"), setOf("json"))
        val context = load(args)
        return when (val result = lifecycleOperations(context).verify(
            LifecycleStatusRequest(
                context.projectId,
                context.bundle.proposalId,
                args.optional("documentation")?.let { FileDocumentationResource(regularFile(it)) },
            ),
        )) {
            is LifecycleVerificationResult.Verified -> render(
                "review lifecycle verify", "VERIFIED", 0, context.bundle, context.path, args.json,
                statusData(result.status),
            )
            is LifecycleVerificationResult.Invalid -> renderFailure(
                "review lifecycle verify", 5, "INTEGRITY_FAILURE", result.message, args.json,
                context.bundle.proposalId, context.path, context.bundle.integrity.payloadSha256,
            )
        }
    }

    private fun lifecycleRecover(args: ReviewArguments): Int {
        args.requireOnly(
            setOf("project", "proposal", "bundle", "documentation", "plan-sha256"),
            setOf("json", "dry-run", "confirm"),
        )
        requireMutationMode(args)
        val context = load(args)
        val request = LifecycleOperationRequest(
            context.projectId,
            context.bundle.proposalId,
            documentation = FileDocumentationResource(regularFile(args.required("documentation"))),
        )
        return lifecycleMutation(
            "review lifecycle recover",
            context,
            args,
            request,
            lifecycleOperations(context)::planRecovery,
            lifecycleOperations(context)::confirmRecovery,
            "RECOVERED",
        )
    }

    private fun lifecycleSupersede(args: ReviewArguments): Int {
        args.requireOnly(
            setOf("project", "proposal", "bundle", "replacement-proposal", "plan-sha256"),
            setOf("json", "dry-run", "confirm"),
        )
        requireMutationMode(args)
        val context = load(args)
        val request = LifecycleOperationRequest(
            context.projectId,
            context.bundle.proposalId,
            replacementProposalId = args.required("replacement-proposal"),
        )
        val operations = lifecycleOperations(context)
        return lifecycleMutation(
            "review lifecycle supersede", context, args, request,
            operations::planSupersede, operations::confirmSupersede, "SUPERSEDED",
        )
    }

    private fun lifecycleArchive(args: ReviewArguments): Int {
        args.requireOnly(
            setOf("project", "proposal", "bundle", "plan-sha256"),
            setOf("json", "dry-run", "confirm"),
        )
        requireMutationMode(args)
        val context = load(args)
        val request = LifecycleOperationRequest(context.projectId, context.bundle.proposalId)
        val operations = lifecycleOperations(context)
        return lifecycleMutation(
            "review lifecycle archive", context, args, request,
            operations::planArchive, operations::confirmArchive, "ARCHIVED",
        )
    }

    private fun lifecycleMutation(
        command: String,
        context: BundleContext,
        args: ReviewArguments,
        request: LifecycleOperationRequest,
        planner: (LifecycleOperationRequest) -> LifecycleOperationPlanResult,
        confirmer: (ConfirmLifecycleOperationRequest) -> LifecycleOperationResult,
        changedStatus: String,
    ): Int {
        if (!args.flag("confirm")) {
            return when (val result = planner(request)) {
                is LifecycleOperationPlanResult.Ready ->
                    render(command, "DRY_RUN_READY", 0, context.bundle, context.path, args.json, planData(result.plan, false))
                is LifecycleOperationPlanResult.NoChange ->
                    render(command, "DRY_RUN_NO_CHANGE", 0, context.bundle, context.path, args.json, planData(result.plan, false))
                is LifecycleOperationPlanResult.Blocked -> renderFailure(
                    command, exitCode(result.reason), "DRY_RUN_BLOCKED", result.message, args.json,
                    context.bundle.proposalId, context.path, context.bundle.integrity.payloadSha256,
                    result.plan?.let { planData(it, false) } ?: emptyMap(),
                )
                is LifecycleOperationPlanResult.Failure -> renderLifecycleFailure(
                    command, result.reason, result.message, context, args.json,
                )
            }
        }
        val expected = args.optional("plan-sha256")
        if (expected != null) require(expected.matches(Regex("[0-9a-f]{64}"))) { "Invalid --plan-sha256." }
        return when (val result = confirmer(ConfirmLifecycleOperationRequest(request, expected))) {
            is LifecycleOperationResult.Changed -> render(
                command, changedStatus, 0, context.bundle, context.path, args.json,
                planData(result.plan, true) + mapOf(
                    "resultGeneration" to result.aggregate.metadata.generation,
                    "resultState" to result.aggregate.metadata.state.name,
                    "resultReceiptId" to result.aggregate.receipt?.receiptId,
                ),
            )
            is LifecycleOperationResult.NoChange -> render(
                command, "ALREADY_APPLIED", 0, context.bundle, context.path, args.json,
                planData(result.plan, false) + mapOf(
                    "resultGeneration" to result.aggregate.metadata.generation,
                    "resultState" to result.aggregate.metadata.state.name,
                ),
            )
            is LifecycleOperationResult.Failure -> renderFailure(
                command, exitCode(result.reason), statusName(result.reason), result.message, args.json,
                context.bundle.proposalId, context.path, context.bundle.integrity.payloadSha256,
                result.plan?.let { planData(it, false) } ?: emptyMap(),
            )
        }
    }

    private fun requireMutationMode(args: ReviewArguments) {
        require(!(args.flag("dry-run") && args.flag("confirm"))) {
            "--dry-run and --confirm are mutually exclusive."
        }
        require(args.optional("plan-sha256") == null || args.flag("confirm")) {
            "--plan-sha256 requires --confirm."
        }
    }

    private fun lifecycleOperations(context: BundleContext): ReviewLifecycleOperations =
        DefaultReviewLifecycleOperations(
            context.repository,
            FileReviewLifecycleRepository(context.projectRoot),
        )

    private fun statusData(status: ReviewLifecycleStatus): Map<String, Any?> = mapOf(
        "documentationRelation" to status.documentationRelation.name,
        "lifecycleGeneration" to status.generation,
        "lifecycleState" to status.state.name,
        "receiptId" to status.receiptId,
        "resultDocumentationSha256" to status.resultDocumentationSha256,
        "transactionId" to status.transactionId,
        "transactionPhase" to status.transactionPhase?.name,
    )

    private fun planData(plan: ReviewLifecycleOperationPlan, mutationPerformed: Boolean): Map<String, Any?> = mapOf(
        "action" to plan.action.name,
        "expectedResultState" to plan.expectedResultState.name,
        "lifecycleGeneration" to plan.observedLifecycleGeneration,
        "lifecycleState" to plan.observedLifecycleState.name,
        "mutationPerformed" to mutationPerformed,
        "planSha256" to plan.planSha256,
        "receiptId" to plan.receiptId,
        "recoveryDisposition" to plan.recoveryDisposition?.name,
        "replacementProposalId" to plan.replacementProposalId,
        "transactionId" to plan.transactionId,
    )

    private fun renderLifecycleFailure(
        command: String,
        reason: LifecycleOperationFailure,
        message: String,
        context: BundleContext,
        json: Boolean,
    ): Int = renderFailure(
        command, exitCode(reason), statusName(reason), message, json,
        context.bundle.proposalId, context.path, context.bundle.integrity.payloadSha256,
    )

    private fun exitCode(reason: LifecycleOperationFailure): Int = when (reason) {
        LifecycleOperationFailure.NOT_FOUND, LifecycleOperationFailure.INVALID -> 5
        LifecycleOperationFailure.CONFLICT -> 4
        LifecycleOperationFailure.BLOCKED -> 3
        LifecycleOperationFailure.RECOVERY_REQUIRED -> 9
        LifecycleOperationFailure.STORAGE_FAILURE -> 8
    }

    private fun statusName(reason: LifecycleOperationFailure): String = when (reason) {
        LifecycleOperationFailure.NOT_FOUND, LifecycleOperationFailure.INVALID -> "INVALID_LIFECYCLE"
        LifecycleOperationFailure.CONFLICT -> "STALE_PLAN"
        LifecycleOperationFailure.BLOCKED -> "DRY_RUN_BLOCKED"
        LifecycleOperationFailure.RECOVERY_REQUIRED -> "RECOVERY_REQUIRED"
        LifecycleOperationFailure.STORAGE_FAILURE -> "STORAGE_FAILURE"
    }

    private fun prepare(args: ReviewArguments): Int {
        args.requireOnly(
            setOf("project", "documentation", "provider", "model", "bundle"),
            setOf("json"),
        )
        val projectRoot = directory(args.required("project"))
        val log = ProjectLogSession.create(projectRoot)
        log.info("Incremental review analysis and AI generation started for $projectRoot.")
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
            DefaultAiIncrementalDocumentationGenerator(
                log.logging(bootstrap.createProvider(args.required("provider"))),
            )
        val lifecycleRepository = FileReviewLifecycleRepository(projectRoot)
        val workflow = DefaultPersistentDocumentationReviewWorkflow(
            generator, repository, codec, lifecycleRepository = lifecycleRepository,
        )
        val result = workflow.prepareAndSave(
            AiIncrementalGenerationRequest(
                previous,
                current,
                plan,
                documentation,
                AiModelId(args.required("model")),
            ),
        )
        log.info("Incremental review preparation completed with ${result::class.simpleName}.")
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
        val workflow = workflow(context)
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
        val expected = args.optional("payload-sha256") ?: context.bundle.integrity.payloadSha256
        val lifecycleRepository = FileReviewLifecycleRepository(context.projectRoot)
        val lifecycle = lifecycleRepository.load(context.projectId, context.bundle.proposalId)
        if (lifecycle !is ReviewLifecycleResult.Success) {
            return renderFailure(
                "review apply", 5, "INVALID_LIFECYCLE", "Review lifecycle is missing or invalid.", args.json,
                context.bundle.proposalId, context.path, context.bundle.integrity.payloadSha256,
            )
        }
        val result = ReviewLifecycleApplyWorkflow(context.repository, lifecycleRepository).apply(
            context.projectId,
            context.bundle.proposalId,
            expected,
            lifecycle.value.metadata.generation,
            FileDocumentationResource(documentationPath),
        )
        return when (result) {
            is LifecycleApplyResult.Applied, is LifecycleApplyResult.AlreadyApplied -> {
                val receipt = when (result) {
                    is LifecycleApplyResult.Applied -> result.receipt
                    is LifecycleApplyResult.AlreadyApplied -> result.receipt
                    else -> error("unreachable")
                }
                render("review apply", if (result is LifecycleApplyResult.Applied) "APPLIED" else "ALREADY_APPLIED",
                    0, context.bundle, context.path, args.json,
                    mapOf(
                        "receiptId" to receipt.receiptId,
                        "acceptedCount" to receipt.acceptedTargetIds.size,
                        "rejectedCount" to receipt.rejectedTargetIds.size,
                        "resultDocumentationSha256" to receipt.resultDocumentationSha256,
                    ))
            }
            is LifecycleApplyResult.Pending -> render(
                "review apply", "PENDING_REVIEW", 3, context.bundle, context.path, args.json,
                mapOf("pendingCount" to result.targetIds.size),
            )
            is LifecycleApplyResult.Conflict -> renderFailure(
                "review apply", 4, "APPLY_CONFLICT",
                result.message, args.json, context.bundle.proposalId, context.path, context.bundle.integrity.payloadSha256,
            )
            is LifecycleApplyResult.RecoveryRequired -> renderFailure(
                "review apply", 9, "RECOVERY_REQUIRED", result.message, args.json,
                context.bundle.proposalId, context.path, context.bundle.integrity.payloadSha256,
            )
            is LifecycleApplyResult.Failed -> renderFailure(
                "review apply", 5, "APPLY_FAILED", result.message, args.json,
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
        return BundleContext(projectRoot, projectId, loaded.bundle, repository, path)
    }

    private fun workflow(context: BundleContext) =
        DefaultPersistentDocumentationReviewWorkflow(
            UnusedGenerator,
            context.repository,
            codec,
            lifecycleRepository = FileReviewLifecycleRepository(context.projectRoot),
        )

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
        data: Map<String, Any?> = emptyMap(),
    ): Int {
        if (json) {
            println(jsonEnvelope(command, status, exitCode, proposalId, bundlePath, payloadSha256, data, message))
        } else {
            println("Command: $command")
            println("Status: $status")
            println("Proposal ID: ${proposalId ?: "unavailable"}")
            println("Bundle Path: ${bundlePath?.toAbsolutePath()?.normalize() ?: "unavailable"}")
            println("Payload SHA-256: ${payloadSha256 ?: "unavailable"}")
            data.toSortedMap().forEach { (key, value) -> println("${key.toDisplayName()}: $value") }
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
        val projectRoot: Path,
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
            val booleanFlags = setOf("json", "accept", "reject", "dry-run", "confirm")
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
