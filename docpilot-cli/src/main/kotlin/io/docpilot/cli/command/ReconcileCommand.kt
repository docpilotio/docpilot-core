package io.docpilot.cli.command

import io.docpilot.cli.io.ConsolePrinter
import io.docpilot.core.api.DocumentationArtifactId
import io.docpilot.core.reconciliation.DefaultDocumentationReconciler
import io.docpilot.core.reconciliation.DocumentationReconciliationDecision
import io.docpilot.core.reconciliation.DocumentationReconciliationRequest
import io.docpilot.core.reconciliation.ReconciliationApplyResult
import io.docpilot.core.reconciliation.ReconciliationCodec
import io.docpilot.core.reconciliation.ReconciliationDecisionDisposition
import io.docpilot.core.reconciliation.ReconciliationDocumentInput
import io.docpilot.core.reconciliation.ReconciliationVerifier
import io.docpilot.core.reconciliation.FileReconciliationDocumentStore
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class ReconcileCommand(
    private val printer: ConsolePrinter = ConsolePrinter(),
    private val codec: ReconciliationCodec = ReconciliationCodec(),
) {
    fun execute(args: List<String>): Int = try {
        require(args.isNotEmpty()) { "Missing reconcile operation." }
        when (args.first()) {
            "preview" -> preview(CliArguments.parse(args.drop(1)))
            "inspect" -> inspect(CliArguments.parse(args.drop(1)))
            "apply" -> apply(CliArguments.parse(args.drop(1)))
            "recover" -> recover(CliArguments.parse(args.drop(1)))
            "verify" -> verify(CliArguments.parse(args.drop(1)))
            else -> error("Unknown reconcile operation: ${args.first()}")
        }
    } catch (error: IllegalArgumentException) {
        printer.error(error.message ?: "Invalid reconciliation input.")
        INVALID_INPUT
    } catch (error: Exception) {
        printer.error(error.message ?: "Reconciliation failed.")
        FAILED
    }

    private fun preview(args: CliArguments): Int {
        val project = root(args)
        val relativePath = args.required("path").replace('\\', '/')
        val currentPath = project.resolve(relativePath).normalize()
        require(currentPath.startsWith(project)) { "Reconciliation path escapes project root." }
        val manifest = args.optional("manifest")?.let { path ->
            codec.decodeManifest(Files.readString(Path.of(path), StandardCharsets.UTF_8))
        }
        val request = DocumentationReconciliationRequest(
            projectId = args.required("project-id"),
            documents = listOf(
                ReconciliationDocumentInput(
                    artifactId = DocumentationArtifactId(args.required("artifact-id")),
                    relativePath = relativePath,
                    reviewedBase = args.optional("base")?.let { Files.readString(Path.of(it)) },
                    current = if (Files.isRegularFile(currentPath)) Files.readString(currentPath) else null,
                    candidate = Files.readString(Path.of(args.required("candidate"))),
                    manifest = manifest,
                ),
            ),
        )
        val plan = DefaultDocumentationReconciler().preview(request)
        val planPath = Path.of(args.required("plan")).toAbsolutePath().normalize()
        planPath.parent?.let(Files::createDirectories)
        Files.writeString(planPath, codec.encodePlan(plan), StandardCharsets.UTF_8)
        renderPlan(plan)
        printer.content("Plan Path: $planPath")
        return if (plan.applicable) OK else CONFLICT
    }

    private fun inspect(args: CliArguments): Int {
        val plan = codec.decodePlan(Files.readString(Path.of(args.required("plan"))))
        renderPlan(plan)
        return if (plan.applicable) OK else CONFLICT
    }

    private fun apply(args: CliArguments): Int {
        val project = root(args)
        val plan = codec.decodePlan(Files.readString(Path.of(args.required("plan"))))
        val disposition = when (args.required("decision")) {
            "accept-generated" -> ReconciliationDecisionDisposition.ACCEPT_GENERATED
            "keep-current" -> ReconciliationDecisionDisposition.KEEP_CURRENT
            "reject" -> ReconciliationDecisionDisposition.REJECT
            else -> throw IllegalArgumentException(
                "Unknown --decision. Expected accept-generated, keep-current, or reject.",
            )
        }
        val decisions = plan.operations.filter { it.requiresDecision }.map {
            DocumentationReconciliationDecision(it.operationId, disposition)
        }
        return when (val result = DefaultDocumentationReconciler().apply(
            plan, decisions, emptyList(), FileReconciliationDocumentStore(project),
        )) {
            is ReconciliationApplyResult.Applied -> {
                printer.success("Reconciliation applied.")
                printer.content("Plan SHA: ${result.result.planSha256}")
                printer.content("Result SHA: ${result.result.resultSha256}")
                OK
            }
            is ReconciliationApplyResult.AlreadyApplied -> {
                printer.success("Reconciliation was already applied.")
                printer.content("Result SHA: ${result.result.resultSha256}")
                OK
            }
            is ReconciliationApplyResult.Pending -> {
                printer.error("Pending operations: ${result.operationIds.joinToString()}")
                PENDING
            }
            is ReconciliationApplyResult.Conflict -> {
                printer.error(result.message)
                CONFLICT
            }
            is ReconciliationApplyResult.Failed -> {
                printer.error(result.message)
                FAILED
            }
        }
    }

    private fun recover(args: CliArguments): Int {
        val results = FileReconciliationDocumentStore(root(args)).recover()
        if (results.isEmpty()) printer.content("Recovery: NONE")
        results.forEach { printer.content("Recovery: ${it.status} Plan SHA: ${it.planSha256}") }
        return if (results.any { it.status.name == "BLOCKED" }) CONFLICT else OK
    }

    private fun verify(args: CliArguments): Int {
        val store = FileReconciliationDocumentStore(root(args))
        val planSha = args.required("plan-sha256")
        val plan = store.findPlan(planSha) ?: run {
            printer.error("Reconciliation Plan not found.")
            return NOT_FOUND
        }
        val result = store.findResult(planSha) ?: run {
            printer.error("Reconciliation Result not found.")
            return NOT_FOUND
        }
        val documents = result.afterDocumentShaByPath.keys.associateWith { store.read(it).orEmpty() }
        val manifests = plan.operations.mapNotNull { store.readManifest(it.artifactId.value) }.distinct()
        val valid = ReconciliationVerifier().verify(plan) &&
            ReconciliationVerifier().verifyOffline(result, documents, manifests)
        printer.content("Verification: ${if (valid) "PASS" else "FAIL"}")
        return if (valid) OK else INTEGRITY_FAILURE
    }

    private fun renderPlan(plan: io.docpilot.core.reconciliation.DocumentationReconciliationPlan) {
        printer.content("Plan SHA: ${plan.planSha256}")
        printer.content("Applicable: ${plan.applicable}")
        plan.operations.forEach {
            printer.content("Operation: ${it.operationId} ${it.kind} ${it.relativePath}")
        }
        plan.conflicts.forEach { printer.content("Conflict: ${it.kind} ${it.relativePath}") }
    }

    private fun root(args: CliArguments): Path =
        Path.of(args.required("project")).toAbsolutePath().normalize()

    companion object {
        const val OK = 0
        const val FAILED = 1
        const val INVALID_INPUT = 2
        const val NOT_FOUND = 3
        const val CONFLICT = 4
        const val PENDING = 5
        const val INTEGRITY_FAILURE = 6
    }
}
