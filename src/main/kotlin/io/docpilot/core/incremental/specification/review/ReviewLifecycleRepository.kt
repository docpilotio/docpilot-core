package io.docpilot.core.incremental.specification.review

import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

public interface ReviewLifecycleRepository {
    public fun initialize(bundle: StoredReviewBundle): ReviewLifecycleResult<ReviewLifecycleAggregate>
    public fun load(projectId: String, proposalId: String): ReviewLifecycleResult<ReviewLifecycleAggregate>
    public fun observeBundle(
        current: ReviewLifecycleMetadata,
        bundle: StoredReviewBundle,
    ): ReviewLifecycleResult<ReviewLifecycleAggregate>
    public fun beginApply(
        current: ReviewLifecycleMetadata,
        transaction: ReviewApplyTransaction,
        receipt: ApplyReceipt,
    ): ReviewLifecycleResult<ReviewLifecycleAggregate>
    public fun updateTransaction(
        transaction: ReviewApplyTransaction,
    ): ReviewLifecycleResult<ReviewApplyTransaction>
    public fun commitApply(
        applying: ReviewLifecycleMetadata,
        receipt: ApplyReceipt,
    ): ReviewLifecycleResult<ReviewLifecycleAggregate>
    public fun loadTransaction(proposalId: String): ReviewLifecycleResult<Pair<ReviewApplyTransaction, ApplyReceipt>>
    public fun transition(
        current: ReviewLifecycleMetadata,
        state: ReviewLifecycleState,
        supersededByProposalId: String? = null,
        archivedFrom: ReviewLifecycleState? = null,
    ): ReviewLifecycleResult<ReviewLifecycleAggregate>
}

public class FileReviewLifecycleRepository(
    projectRoot: Path,
    private val codec: ReviewLifecycleCodec = ReviewLifecycleCodec(),
) : ReviewLifecycleRepository {
    private val root = projectRoot.resolve(ReviewLifecycleFormat.DEFAULT_DIRECTORY)

    override fun initialize(bundle: StoredReviewBundle): ReviewLifecycleResult<ReviewLifecycleAggregate> {
        val existing = load(bundle.projectIdentity.projectId, bundle.proposalId)
        if (existing is ReviewLifecycleResult.Success) return existing
        if (existing is ReviewLifecycleResult.Failure && existing.reason != ReviewLifecycleFailure.NOT_FOUND) return existing
        val metadata = codec.createActive(bundle)
        return writeGeneration(metadata, null)
    }

    override fun load(projectId: String, proposalId: String): ReviewLifecycleResult<ReviewLifecycleAggregate> = try {
        val directory = proposalDirectory(proposalId)
        val pointer = directory.resolve("CURRENT")
        if (!Files.exists(pointer)) {
            ReviewLifecycleResult.Failure(ReviewLifecycleFailure.NOT_FOUND, "Review lifecycle was not found.")
        } else {
            val generationName = Files.readString(pointer, StandardCharsets.UTF_8).trim()
            require(generationName.matches(Regex("[0-9]{12}-[0-9a-f]{64}"))) { "Invalid lifecycle pointer." }
            val generation = directory.resolve("generations").resolve(generationName)
            val metadata = codec.decodeMetadata(Files.readString(generation.resolve("lifecycle.json")))
            require(metadata.projectId == projectId && metadata.proposalId == proposalId) {
                "Lifecycle identity mismatch."
            }
            val receiptPath = generation.resolve("receipt.json")
            val receipt = if (Files.exists(receiptPath)) codec.decodeReceipt(Files.readString(receiptPath)) else null
            require((metadata.state == ReviewLifecycleState.APPLIED) == (receipt != null)) {
                "Applied lifecycle and receipt must be committed together."
            }
            if (receipt != null) {
                require(receipt.receiptId == metadata.applyReceiptId)
                require(receipt.projectId == projectId && receipt.proposalId == proposalId)
            }
            ReviewLifecycleResult.Success(ReviewLifecycleAggregate(metadata, receipt))
        }
    } catch (error: Exception) {
        ReviewLifecycleResult.Failure(
            ReviewLifecycleFailure.INVALID,
            error.message ?: "Unable to load review lifecycle.",
        )
    }

    override fun observeBundle(
        current: ReviewLifecycleMetadata,
        bundle: StoredReviewBundle,
    ): ReviewLifecycleResult<ReviewLifecycleAggregate> {
        if (current.state != ReviewLifecycleState.ACTIVE ||
            current.projectId != bundle.projectIdentity.projectId ||
            current.proposalId != bundle.proposalId
        ) {
            return ReviewLifecycleResult.Failure(ReviewLifecycleFailure.CONFLICT, "Only an active matching lifecycle can observe a bundle.")
        }
        val loaded = load(current.projectId, current.proposalId)
        if (loaded !is ReviewLifecycleResult.Success || loaded.value.metadata != current) {
            return ReviewLifecycleResult.Failure(ReviewLifecycleFailure.CONFLICT, "Lifecycle generation changed.")
        }
        return writeGeneration(
            codec.metadata(
                current.projectId, current.proposalId, bundle.integrity.payloadSha256,
                current.generation + 1, ReviewLifecycleState.ACTIVE,
            ),
            null,
        )
    }

    override fun beginApply(
        current: ReviewLifecycleMetadata,
        transaction: ReviewApplyTransaction,
        receipt: ApplyReceipt,
    ): ReviewLifecycleResult<ReviewLifecycleAggregate> {
        val loaded = load(current.projectId, current.proposalId)
        if (loaded !is ReviewLifecycleResult.Success || loaded.value.metadata != current) {
            return ReviewLifecycleResult.Failure(ReviewLifecycleFailure.CONFLICT, "Lifecycle generation changed.")
        }
        return try {
            val directory = proposalDirectory(current.proposalId)
            Files.createDirectories(directory.resolve("transaction"))
            writeAtomic(directory.resolve("transaction").resolve("journal.json"), codec.encode(transaction))
            writeAtomic(directory.resolve("transaction").resolve("staged-receipt.json"), codec.encode(receipt))
            val applying = codec.metadata(
                current.projectId,
                current.proposalId,
                current.observedBundlePayloadSha256,
                current.generation + 1,
                ReviewLifecycleState.APPLYING,
                transaction.transactionId,
            )
            writeGeneration(applying, null)
        } catch (error: Exception) {
            ReviewLifecycleResult.Failure(ReviewLifecycleFailure.IO_FAILURE, error.message ?: "Unable to begin apply.")
        }
    }

    override fun updateTransaction(
        transaction: ReviewApplyTransaction,
    ): ReviewLifecycleResult<ReviewApplyTransaction> = try {
        writeAtomic(
            proposalDirectory(transaction.proposalId).resolve("transaction").resolve("journal.json"),
            codec.encode(transaction),
        )
        ReviewLifecycleResult.Success(transaction)
    } catch (error: Exception) {
        ReviewLifecycleResult.Failure(ReviewLifecycleFailure.IO_FAILURE, error.message ?: "Unable to update transaction.")
    }

    override fun commitApply(
        applying: ReviewLifecycleMetadata,
        receipt: ApplyReceipt,
    ): ReviewLifecycleResult<ReviewLifecycleAggregate> {
        val loaded = load(applying.projectId, applying.proposalId)
        if (loaded !is ReviewLifecycleResult.Success || loaded.value.metadata != applying) {
            return ReviewLifecycleResult.Failure(ReviewLifecycleFailure.CONFLICT, "Applying lifecycle changed.")
        }
        val applied = codec.metadata(
            applying.projectId,
            applying.proposalId,
            applying.observedBundlePayloadSha256,
            applying.generation + 1,
            ReviewLifecycleState.APPLIED,
            receiptId = receipt.receiptId,
        )
        return writeGeneration(applied, receipt)
    }

    override fun loadTransaction(
        proposalId: String,
    ): ReviewLifecycleResult<Pair<ReviewApplyTransaction, ApplyReceipt>> = try {
        val directory = proposalDirectory(proposalId).resolve("transaction")
        ReviewLifecycleResult.Success(
            codec.decodeTransaction(Files.readString(directory.resolve("journal.json"))) to
                codec.decodeReceipt(Files.readString(directory.resolve("staged-receipt.json"))),
        )
    } catch (error: Exception) {
        ReviewLifecycleResult.Failure(ReviewLifecycleFailure.NOT_FOUND, error.message ?: "Transaction was not found.")
    }

    override fun transition(
        current: ReviewLifecycleMetadata,
        state: ReviewLifecycleState,
        supersededByProposalId: String?,
        archivedFrom: ReviewLifecycleState?,
    ): ReviewLifecycleResult<ReviewLifecycleAggregate> {
        val loaded = load(current.projectId, current.proposalId)
        if (loaded !is ReviewLifecycleResult.Success || loaded.value.metadata != current) {
            return ReviewLifecycleResult.Failure(ReviewLifecycleFailure.CONFLICT, "Lifecycle generation changed.")
        }
        return writeGeneration(
            codec.metadata(
                current.projectId,
                current.proposalId,
                current.observedBundlePayloadSha256,
                current.generation + 1,
                state,
                supersededBy = supersededByProposalId,
                archivedFrom = archivedFrom,
            ),
            null,
        )
    }

    private fun writeGeneration(
        metadata: ReviewLifecycleMetadata,
        receipt: ApplyReceipt?,
    ): ReviewLifecycleResult<ReviewLifecycleAggregate> = try {
        val directory = proposalDirectory(metadata.proposalId)
        val generationName = "%012d-%s".format(metadata.generation, metadata.integrity.payloadSha256)
        val generation = directory.resolve("generations").resolve(generationName)
        Files.createDirectories(generation)
        writeNew(generation.resolve("lifecycle.json"), codec.encode(metadata))
        if (receipt != null) writeNew(generation.resolve("receipt.json"), codec.encode(receipt))
        writeAtomic(directory.resolve("CURRENT"), "$generationName\n")
        ReviewLifecycleResult.Success(ReviewLifecycleAggregate(metadata, receipt))
    } catch (error: Exception) {
        ReviewLifecycleResult.Failure(ReviewLifecycleFailure.IO_FAILURE, error.message ?: "Unable to store lifecycle.")
    }

    private fun proposalDirectory(proposalId: String): Path {
        require(proposalId.matches(Regex("review:[0-9a-f]{64}"))) { "Unsafe proposal id." }
        return root.resolve(proposalId.removePrefix("review:"))
    }

    private fun writeNew(path: Path, value: String) {
        if (Files.exists(path)) {
            require(Files.readString(path, StandardCharsets.UTF_8) == value) { "Immutable generation collision." }
            return
        }
        Files.writeString(path, value, StandardCharsets.UTF_8)
    }

    private fun writeAtomic(path: Path, value: String) {
        Files.createDirectories(requireNotNull(path.parent))
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
}
