package io.docpilot.core.reconciliation

import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64

public enum class ReconciliationRecoveryStatus { NONE, RECOVERED, BLOCKED }

public data class ReconciliationRecoveryResult(
    public val status: ReconciliationRecoveryStatus,
    public val planSha256: String? = null,
    public val message: String? = null,
)

public enum class ReconciliationTransactionPhase { DOCUMENTS_WRITTEN, MANIFESTS_WRITTEN, RESULT_WRITTEN }

public fun interface ReconciliationFailureInjector {
    public fun after(phase: ReconciliationTransactionPhase)
}

public class FileReconciliationDocumentStore(
    private val projectRoot: Path,
    private val codec: ReconciliationCodec = ReconciliationCodec(),
    private val failureInjector: ReconciliationFailureInjector = ReconciliationFailureInjector {},
) : ReconciliationDocumentStore {
    private val controlRoot = projectRoot.resolve(".docpilot/reconciliation")
    private val manifestsRoot = controlRoot.resolve("manifests")
    private val plansRoot = controlRoot.resolve("plans")
    private val resultsRoot = controlRoot.resolve("results")
    private val transactionsRoot = controlRoot.resolve("transactions")

    override fun read(relativePath: String): String? {
        val path = resolveDocument(relativePath)
        return if (Files.exists(path)) Files.readString(path, StandardCharsets.UTF_8) else null
    }

    public fun readManifest(artifactId: String): DocumentationOwnershipManifest? {
        val path = manifestPath(artifactId)
        return if (Files.exists(path)) codec.decodeManifest(Files.readString(path)) else null
    }

    override fun manifestSha256(artifactId: String): String? = readManifest(artifactId)?.manifestSha256

    override fun savePlan(plan: DocumentationReconciliationPlan): Boolean {
        require(ReconciliationVerifier().verify(plan)) { "Invalid reconciliation plan." }
        val path = plansRoot.resolve("${plan.planSha256}.reconciliation")
        val encoded = codec.encodePlan(plan)
        if (Files.exists(path)) {
            require(Files.readString(path) == encoded) { "Stored reconciliation plan conflicts with input." }
        } else {
            atomicWrite(path, encoded)
        }
        return true
    }

    override fun findPlan(planSha256: String): DocumentationReconciliationPlan? {
        requireSha(planSha256)
        val path = plansRoot.resolve("$planSha256.reconciliation")
        return if (Files.exists(path)) codec.decodePlan(Files.readString(path)) else null
    }

    override fun findResult(planSha256: String): DocumentationReconciliationResult? {
        requireSha(planSha256)
        val path = resultsRoot.resolve("$planSha256.reconciliation")
        return if (Files.exists(path)) codec.decodeResult(Files.readString(path)) else null
    }

    override fun applyAtomically(
        expectedCurrentShaByPath: Map<String, String?>,
        documents: Map<String, String>,
        manifests: Map<String, DocumentationOwnershipManifest>,
        result: DocumentationReconciliationResult,
    ): Boolean {
        if (!ReconciliationVerifier().verify(result)) return false
        if (findResult(result.planSha256) != null) return true
        if (!matchesExpected(expectedCurrentShaByPath, documents)) return false
        manifests.values.forEach {
            require(ReconciliationVerifier().verify(it)) { "Invalid reconciliation manifest." }
        }
        val journal = encodeJournal(expectedCurrentShaByPath, documents, manifests, result)
        val journalPath = transactionsRoot.resolve("${result.planSha256}.prepared")
        atomicWrite(journalPath, journal)
        return when (finishJournal(journalPath)) {
            ReconciliationRecoveryStatus.RECOVERED -> true
            else -> false
        }
    }

    public fun recover(): List<ReconciliationRecoveryResult> {
        if (!Files.exists(transactionsRoot)) return emptyList()
        return Files.list(transactionsRoot).use { paths ->
            paths.filter { it.fileName.toString().endsWith(".prepared") }
                .sorted()
                .map { journal ->
                    val plan = journal.fileName.toString().removeSuffix(".prepared")
                    try {
                        ReconciliationRecoveryResult(finishJournal(journal), plan)
                    } catch (error: RuntimeException) {
                        ReconciliationRecoveryResult(
                            ReconciliationRecoveryStatus.BLOCKED,
                            plan,
                            error.message ?: error::class.simpleName,
                        )
                    }
                }.toList()
        }
    }

    private fun finishJournal(path: Path): ReconciliationRecoveryStatus {
        val journal = decodeJournal(Files.readString(path))
        findResult(journal.result.planSha256)?.let {
            require(it == journal.result) { "Existing reconciliation result conflicts with journal." }
            Files.deleteIfExists(path)
            return ReconciliationRecoveryStatus.RECOVERED
        }
        require(matchesExpected(journal.expected, journal.documents)) {
            "Recovery blocked because a document matches neither expected nor intended content."
        }
        journal.documents.toSortedMap().forEach { (relativePath, content) ->
            atomicWrite(resolveDocument(relativePath), content)
        }
        failureInjector.after(ReconciliationTransactionPhase.DOCUMENTS_WRITTEN)
        journal.manifests.toSortedMap().forEach { (artifactId, manifest) ->
            atomicWrite(manifestPath(artifactId), codec.encodeManifest(manifest))
        }
        failureInjector.after(ReconciliationTransactionPhase.MANIFESTS_WRITTEN)
        atomicWrite(
            resultsRoot.resolve("${journal.result.planSha256}.reconciliation"),
            codec.encodeResult(journal.result),
        )
        failureInjector.after(ReconciliationTransactionPhase.RESULT_WRITTEN)
        Files.deleteIfExists(path)
        return ReconciliationRecoveryStatus.RECOVERED
    }

    private fun matchesExpected(expected: Map<String, String?>, intended: Map<String, String>): Boolean =
        expected.all { (relativePath, expectedSha) ->
            require(ReconciliationIntegrity.safePath(relativePath)) { "Unsafe reconciliation path." }
            val actual = read(relativePath)?.let(ReconciliationIntegrity::sha256)
            val intendedSha = intended[relativePath]?.let(ReconciliationIntegrity::sha256)
            actual == expectedSha || actual == intendedSha
        }

    private fun resolveDocument(relativePath: String): Path {
        require(ReconciliationIntegrity.safePath(relativePath)) { "Unsafe reconciliation path." }
        val resolved = projectRoot.resolve(relativePath).normalize()
        require(resolved.startsWith(projectRoot.normalize())) { "Reconciliation path escapes project root." }
        return resolved
    }

    private fun manifestPath(artifactId: String): Path =
        manifestsRoot.resolve("${ReconciliationIntegrity.sha256(artifactId)}.manifest")

    private fun atomicWrite(path: Path, value: String) {
        Files.createDirectories(path.parent)
        val temporary = Files.createTempFile(path.parent, ".docpilot-", ".tmp")
        try {
            Files.writeString(temporary, value, StandardCharsets.UTF_8)
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private data class Journal(
        val expected: Map<String, String?>,
        val documents: Map<String, String>,
        val manifests: Map<String, DocumentationOwnershipManifest>,
        val result: DocumentationReconciliationResult,
    )

    private fun encodeJournal(
        expected: Map<String, String?>,
        documents: Map<String, String>,
        manifests: Map<String, DocumentationOwnershipManifest>,
        result: DocumentationReconciliationResult,
    ): String = buildString {
        append("format|1\n")
        expected.toSortedMap().forEach { (path, sha) -> record("expected", path, sha.orEmpty()) }
        documents.toSortedMap().forEach { (path, content) -> record("document", path, content) }
        manifests.toSortedMap().forEach { (id, manifest) -> record("manifest", id, codec.encodeManifest(manifest)) }
        record("result", codec.encodeResult(result))
        val payload = toString()
        append("sha|").append(ReconciliationIntegrity.sha256(payload)).append('\n')
    }

    private fun decodeJournal(value: String): Journal {
        val lines = value.lines().filter(String::isNotEmpty)
        require(lines.firstOrNull() == "format|1" && lines.last().startsWith("sha|")) {
            "Unsupported reconciliation journal."
        }
        val payload = lines.dropLast(1).joinToString("\n", postfix = "\n")
        require(lines.last() == "sha|${ReconciliationIntegrity.sha256(payload)}") {
            "Reconciliation journal integrity mismatch."
        }
        val records = lines.drop(1).dropLast(1).map { line ->
            val fields = line.split('|')
            fields.first() to fields.drop(1).map {
                String(Base64.getDecoder().decode(it), Charsets.UTF_8)
            }
        }
        val expected = records.filter { it.first == "expected" }.associate {
            require(it.second.size == 2)
            it.second[0] to it.second[1].ifEmpty { null }
        }
        val documents = records.filter { it.first == "document" }.associate {
            require(it.second.size == 2)
            it.second[0] to it.second[1]
        }
        val manifests = records.filter { it.first == "manifest" }.associate {
            require(it.second.size == 2)
            it.second[0] to codec.decodeManifest(it.second[1])
        }
        val result = codec.decodeResult(records.single { it.first == "result" }.second.single())
        return Journal(expected, documents, manifests, result)
    }

    private fun StringBuilder.record(kind: String, vararg values: String) {
        append(kind)
        values.forEach { append('|').append(Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8))) }
        append('\n')
    }

    private fun requireSha(value: String) {
        require(value.matches(Regex("[0-9a-f]{64}"))) { "Invalid reconciliation SHA-256." }
    }
}
