package io.docpilot.cli.command

import io.docpilot.cli.bootstrap.ProjectKnowledgeLoader
import io.docpilot.core.api.DocumentationArtifactDescriptor
import io.docpilot.core.api.DocumentationArtifactId
import io.docpilot.core.api.DocumentationArtifactKind
import io.docpilot.core.documentation.profile.DefaultDocumentationProfileResolver
import io.docpilot.core.documentation.profile.DocumentationProfileId
import io.docpilot.core.documentation.profile.DocumentationProfileResolutionRequest
import io.docpilot.core.documentation.profile.DocumentationProfileVersion
import io.docpilot.core.documentation.profile.DocumentationRendererCapabilityProvider
import io.docpilot.core.documentation.profile.DocumentPlanningStatus
import io.docpilot.core.incremental.execution.DocumentationArtifactOperation
import io.docpilot.core.incremental.specification.snapshot.FileSpecificationSnapshotRepository
import io.docpilot.core.incremental.specification.snapshot.SpecificationSnapshotLoadResult
import io.docpilot.core.model.RenderedArtifact
import io.docpilot.core.render.ProjectSpecificationMarkdownRenderer
import io.docpilot.core.specification.DefaultSpecificationBuilder
import io.docpilot.core.specification.SpecificationBuildRequest
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

internal fun interface DocumentationGenerationWorkflow {
    fun execute(options: DocumentationGenerationOptions): DocumentationGenerationResult
}

internal class DefaultDocumentationGenerationWorkflow(
    private val knowledgeLoader: ProjectKnowledgeLoader = ProjectKnowledgeLoader(),
) : DocumentationGenerationWorkflow {
    override fun execute(options: DocumentationGenerationOptions): DocumentationGenerationResult = try {
        prepareAndExecute(options)
    } catch (e: DocumentationGenerationConflict) {
        failure(options, DocumentationGenerationStatus.CONFLICT, e.message.orEmpty())
    } catch (e: IllegalArgumentException) {
        failure(options, DocumentationGenerationStatus.BLOCKED, e.message.orEmpty())
    } catch (e: Exception) {
        failure(options, DocumentationGenerationStatus.FAILED, e.message ?: "Documentation generation failed.")
    }

    private fun prepareAndExecute(options: DocumentationGenerationOptions): DocumentationGenerationResult {
        val project = options.projectRoot.toAbsolutePath().normalize()
        val output = options.outputRoot.toAbsolutePath().normalize()
        require(Files.isDirectory(project)) { "Project path is not a directory: $project" }
        validateOutputRoot(project, output)
        val (profileId, profileVersion) = parseProfile(options.profile)
        val analysis = knowledgeLoader.analyze(project)
        val specification = DefaultSpecificationBuilder().build(
            SpecificationBuildRequest(analysis.project, analysis.knowledge, analysis.sourceIndex),
        )
        // Official documentation generation owns its state below the explicit output root;
        // the analyzed project remains read-only.
        val snapshotRepository = FileSpecificationSnapshotRepository(output)
        val snapshotLoad = snapshotRepository.load(specification.project.id)
        if (snapshotLoad is SpecificationSnapshotLoadResult.Invalid) {
            throw IllegalArgumentException("Invalid Specification Snapshot: ${snapshotLoad.reason}: ${snapshotLoad.message}")
        }
        val renderer = ProjectSpecificationMarkdownRenderer()
        val catalog = renderer.describe(specification)
        val resolution = DefaultDocumentationProfileResolver().resolve(
            DocumentationProfileResolutionRequest(
                DocumentationProfileId(profileId), DocumentationProfileVersion(profileVersion), specification,
                catalog, (renderer as DocumentationRendererCapabilityProvider).capabilities(),
            ),
        )
        val selected = select(catalog, options)
        require(selected.isNotEmpty()) { "Selection did not match any documentation artifact." }
        val blockedTypes = resolution.documents.filter {
            it.status == DocumentPlanningStatus.BLOCKED || it.status == DocumentPlanningStatus.UNSUPPORTED
        }.map { it.type.name }.toSet()
        require(selected.none { documentType(it.kind) in blockedTypes }) {
            "Selected documentation contains BLOCKED or UNSUPPORTED Profile artifacts."
        }
        val rendered = renderer.render(specification, selected.mapTo(linkedSetOf()) { it.artifactId })
        val manifest = loadManifest(output)
        val results = plan(selected, rendered, output, manifest)
        val planSha = planSha(specification.project.id, options.profile, output, results, snapshotLoad)
        val changed = results.filter { it.operation != DocumentationArtifactOperation.KEEP }
        if (options.mode == DocumentationGenerationMode.PREVIEW) {
            return result(options, specification.project.id, snapshotLoad, planSha, results,
                if (changed.isEmpty()) DocumentationGenerationStatus.NO_CHANGES else DocumentationGenerationStatus.PREVIEW_READY)
        }
        options.expectedPlanSha256?.let {
            require(it == planSha) { "Stale documentation Plan: expected $it but calculated $planSha." }
        }
        if (changed.isEmpty()) {
            return result(options, specification.project.id, snapshotLoad, planSha, results, DocumentationGenerationStatus.NO_CHANGES)
        }
        applyTransaction(output, rendered, results, manifest) { snapshotRepository.save(specification) }
        return result(options, specification.project.id, snapshotLoad, planSha, results,
            DocumentationGenerationStatus.APPLIED, snapshotWritten = true)
    }

    private fun select(catalog: List<DocumentationArtifactDescriptor>, options: DocumentationGenerationOptions): List<DocumentationArtifactDescriptor> {
        if (options.full) return catalog
        val byId = catalog.associateBy { it.artifactId.value }
        val unknown = options.artifactIds - byId.keys
        require(unknown.isEmpty()) { "Unknown Artifact ID: ${unknown.sorted().joinToString()}" }
        val directlySelected = catalog.filter {
            it.artifactId.value in options.artifactIds || documentType(it.kind) in options.documentTypes
        }.mapTo(linkedSetOf()) { it.artifactId }
        val byArtifactId = catalog.associateBy { it.artifactId }
        val queue = ArrayDeque(directlySelected)
        while (queue.isNotEmpty()) {
            byArtifactId.getValue(queue.removeFirst()).dependencyArtifactIds.forEach {
                if (directlySelected.add(it)) queue.add(it)
            }
        }
        return catalog.filter { it.artifactId in directlySelected }
    }

    private fun plan(
        selected: List<DocumentationArtifactDescriptor>, rendered: List<RenderedArtifact>, output: Path,
        manifest: Map<String, String>,
    ): List<DocumentationArtifactResult> {
        val renderedByPath = rendered.associateBy { it.relativePath }
        return selected.map { descriptor ->
            val relative = safeRelativePath(descriptor.relativePath)
            val target = output.resolve(relative).normalize()
            require(target.startsWith(output)) { "Artifact path escapes output root: $relative" }
            val generated = renderedByPath.getValue(descriptor.relativePath)
            val newHash = sha256(generated.content)
            val oldHash = if (Files.isRegularFile(target)) sha256(Files.readString(target, StandardCharsets.UTF_8)) else null
            val ownedHash = manifest[relative]
            if (oldHash != null && ownedHash == null) throw DocumentationGenerationConflict("Unknown ownership: $relative")
            if (oldHash != null && oldHash != ownedHash) throw DocumentationGenerationConflict("Locally modified generated file: $relative")
            val operation = when {
                oldHash == null -> DocumentationArtifactOperation.CREATE
                oldHash == newHash -> DocumentationArtifactOperation.KEEP
                else -> DocumentationArtifactOperation.UPDATE
            }
            DocumentationArtifactResult(descriptor.artifactId, documentType(descriptor.kind), relative, operation, newHash,
                when (operation) { DocumentationArtifactOperation.CREATE -> "MISSING"; DocumentationArtifactOperation.UPDATE -> "CONTENT_CHANGED"; else -> "UNCHANGED" })
        }.sortedBy { it.artifactId.value }
    }

    private fun apply(output: Path, rendered: List<RenderedArtifact>, results: List<DocumentationArtifactResult>) {
        val renderedByPath = rendered.associateBy { safeRelativePath(it.relativePath) }
        results.filter { it.operation != DocumentationArtifactOperation.KEEP }.forEach { item ->
            val target = output.resolve(item.relativePath).normalize()
            Files.createDirectories(target.parent)
            val temp = Files.createTempFile(target.parent, ".docpilot-docs-", ".tmp")
            try {
                Files.writeString(temp, renderedByPath.getValue(item.relativePath).content, StandardCharsets.UTF_8)
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
            } finally { Files.deleteIfExists(temp) }
        }
    }

    private fun applyTransaction(
        output: Path,
        rendered: List<RenderedArtifact>,
        results: List<DocumentationArtifactResult>,
        manifest: Map<String, String>,
        saveSnapshot: () -> Unit,
    ) {
        val changedPaths = results.filter { it.operation != DocumentationArtifactOperation.KEEP }
            .associate { item ->
                val path = output.resolve(item.relativePath)
                path to if (Files.isRegularFile(path)) Files.readString(path, StandardCharsets.UTF_8) else null
            }
        val manifestPath = output.resolve(MANIFEST_PATH)
        val previousManifest = if (Files.isRegularFile(manifestPath)) Files.readString(manifestPath, StandardCharsets.UTF_8) else null
        try {
            apply(output, rendered, results)
            saveManifest(output, manifest, results)
            saveSnapshot()
        } catch (failure: Exception) {
            val rollbackFailures = mutableListOf<String>()
            changedPaths.forEach { (path, previous) ->
                runCatching {
                    if (previous == null) Files.deleteIfExists(path)
                    else Files.writeString(path, previous, StandardCharsets.UTF_8)
                }.onFailure { rollbackFailures += "${path.fileName}: ${it.message}" }
            }
            runCatching {
                if (previousManifest == null) Files.deleteIfExists(manifestPath)
                else Files.writeString(manifestPath, previousManifest, StandardCharsets.UTF_8)
            }.onFailure { rollbackFailures += "manifest: ${it.message}" }
            if (rollbackFailures.isNotEmpty()) {
                throw IllegalStateException(
                    "Documentation apply failed and recovery is required: ${rollbackFailures.joinToString()}", failure,
                )
            }
            throw IllegalStateException("Documentation apply failed; written files were rolled back: ${failure.message}", failure)
        }
    }

    private fun validateOutputRoot(project: Path, output: Path) {
        require(output != project) { "Output root must not equal project root." }
        if (Files.exists(output)) require(Files.isDirectory(output) && !Files.isSymbolicLink(output)) {
            "Output root must be a real directory and not a symbolic link: $output"
        }
        val sourceRoots = listOf(project.resolve("src"), project.resolve("app/src"))
        require(sourceRoots.none { output.startsWith(it) || it.startsWith(output) }) { "Output root overlaps a project source root." }
    }

    private fun safeRelativePath(value: String): String {
        val normalized = value.replace('\\', '/')
        require(!Path.of(normalized).isAbsolute && !Regex("^[A-Za-z]:").containsMatchIn(normalized)) { "Absolute Artifact path: $value" }
        val path = Path.of(normalized).normalize()
        require(!path.startsWith("..")) { "Artifact path traversal: $value" }
        return path.toString().replace('\\', '/')
    }

    private fun parseProfile(value: String): Pair<String, Int> {
        val match = Regex("([a-z0-9-]+)@([1-9][0-9]*)").matchEntire(value)
            ?: throw IllegalArgumentException("Profile must use id@version format: $value")
        return match.groupValues[1] to match.groupValues[2].toInt()
    }

    private fun loadManifest(output: Path): Map<String, String> {
        val path = output.resolve(MANIFEST_PATH)
        if (!Files.isRegularFile(path)) return emptyMap()
        return Files.readAllLines(path, StandardCharsets.UTF_8).filter { it.isNotBlank() }.associate {
            val separator = it.indexOf('|'); require(separator > 0) { "Malformed documentation ownership manifest." }
            it.substring(0, separator) to it.substring(separator + 1)
        }
    }

    private fun saveManifest(output: Path, previous: Map<String, String>, results: List<DocumentationArtifactResult>) {
        val path = output.resolve(MANIFEST_PATH); Files.createDirectories(path.parent)
        val merged = previous.toMutableMap().apply { results.forEach { put(it.relativePath, it.contentSha256) } }
        val content = merged.entries.sortedBy { it.key }.joinToString("\n", postfix = "\n") { "${it.key}|${it.value}" }
        Files.writeString(path, content, StandardCharsets.UTF_8)
    }

    private fun planSha(projectId: String, profile: String, output: Path, artifacts: List<DocumentationArtifactResult>, snapshot: SpecificationSnapshotLoadResult): String =
        sha256(buildString {
            appendLine("documentation-plan|1"); appendLine("project|$projectId"); appendLine("profile|$profile")
            appendLine("output|${output.toString().replace('\\', '/')}")
            appendLine("snapshot|${snapshotStatus(snapshot)}")
            artifacts.forEach { appendLine("artifact|${it.artifactId.value}|${it.documentType}|${it.relativePath}|${it.operation}|${it.contentSha256}") }
        })

    private fun result(options: DocumentationGenerationOptions, projectId: String, snapshot: SpecificationSnapshotLoadResult,
        planSha: String, artifacts: List<DocumentationArtifactResult>, status: DocumentationGenerationStatus,
        snapshotWritten: Boolean = false) = DocumentationGenerationResult(status, options.mode, projectId,
        options.outputRoot.toAbsolutePath().normalize().toString(), options.profile, snapshotStatus(snapshot), planSha,
        artifacts, snapshotWritten = snapshotWritten)

    private fun failure(options: DocumentationGenerationOptions, status: DocumentationGenerationStatus, message: String) =
        DocumentationGenerationResult(status, options.mode, null, options.outputRoot.toAbsolutePath().normalize().toString(),
            options.profile, "UNKNOWN", null, diagnostics = listOf(message))

    private fun snapshotStatus(value: SpecificationSnapshotLoadResult): String = when (value) {
        SpecificationSnapshotLoadResult.NotFound -> "NOT_FOUND"
        is SpecificationSnapshotLoadResult.Valid -> "VALID"
        is SpecificationSnapshotLoadResult.Invalid -> "INVALID:${value.reason}"
    }

    private fun documentType(kind: DocumentationArtifactKind): String = when (kind) {
        DocumentationArtifactKind.FEATURE_DETAIL -> "FEATURE_SPECIFICATION"
        else -> kind.name
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private companion object { const val MANIFEST_PATH = ".docpilot/documentation-ownership.manifest" }
}

private class DocumentationGenerationConflict(message: String) : RuntimeException(message)
