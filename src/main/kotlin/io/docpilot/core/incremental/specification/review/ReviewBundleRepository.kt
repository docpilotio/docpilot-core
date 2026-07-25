package io.docpilot.core.incremental.specification.review

import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

public interface ReviewBundleRepository {
    public fun load(expectedProjectId: String, proposalId: String): ReviewBundleLoadResult
    public fun saveNew(bundle: StoredReviewBundle): ReviewBundleSaveResult
    public fun replace(bundle: StoredReviewBundle, expectedPayloadSha256: String): ReviewBundleSaveResult
}

public class FileReviewBundleRepository(
    projectRoot: Path,
    private val codec: JsonReviewBundleCodec = JsonReviewBundleCodec(),
    private val exactBundlePath: Path? = null,
) : ReviewBundleRepository {
    private val directory = exactBundlePath?.parent ?: projectRoot.resolve(ReviewBundleFormat.DEFAULT_DIRECTORY)

    override fun load(expectedProjectId: String, proposalId: String): ReviewBundleLoadResult {
        val path = pathFor(proposalId)
        if (!Files.exists(path)) return ReviewBundleLoadResult.NotFound
        if (!Files.isRegularFile(path)) {
            return ReviewBundleLoadResult.Invalid(
                ReviewBundleValidationFailure.CORRUPTED,
                "Review bundle path is not a regular file.",
            )
        }
        return try {
            codec.decode(Files.readString(path, StandardCharsets.UTF_8), expectedProjectId)
        } catch (error: Exception) {
            ReviewBundleLoadResult.Invalid(
                ReviewBundleValidationFailure.CORRUPTED,
                error.message ?: "Unable to read review bundle.",
            )
        }
    }

    override fun saveNew(bundle: StoredReviewBundle): ReviewBundleSaveResult {
        val path = pathFor(bundle.proposalId)
        if (Files.exists(path)) return ReviewBundleSaveResult.AlreadyExists
        return save(bundle, path)
    }

    override fun replace(
        bundle: StoredReviewBundle,
        expectedPayloadSha256: String,
    ): ReviewBundleSaveResult {
        require(expectedPayloadSha256.isSha256()) { "Expected review bundle SHA-256 is invalid." }
        val path = pathFor(bundle.proposalId)
        val current = load(bundle.projectIdentity.projectId, bundle.proposalId)
        if (current !is ReviewBundleLoadResult.Valid) {
            return ReviewBundleSaveResult.Invalid("Cannot replace a missing or invalid review bundle.")
        }
        val actual = current.bundle.integrity.payloadSha256
        if (actual != expectedPayloadSha256) {
            return ReviewBundleSaveResult.Conflict(expectedPayloadSha256, actual)
        }
        return save(bundle, path, expectedPayloadSha256)
    }

    private fun save(
        bundle: StoredReviewBundle,
        path: Path,
        expectedPayloadSha256: String? = null,
    ): ReviewBundleSaveResult = try {
        Files.createDirectories(directory)
        val encoded = codec.encode(bundle)
        val temporary = Files.createTempFile(directory, "review-", ".tmp")
        try {
            Files.writeString(temporary, encoded, StandardCharsets.UTF_8)
            val validation = codec.decode(
                Files.readString(temporary, StandardCharsets.UTF_8),
                bundle.projectIdentity.projectId,
            )
            require(validation is ReviewBundleLoadResult.Valid) {
                "Refusing to store an invalid temporary review bundle."
            }
            if (expectedPayloadSha256 != null) {
                val current = load(bundle.projectIdentity.projectId, bundle.proposalId)
                if (current !is ReviewBundleLoadResult.Valid) {
                    return ReviewBundleSaveResult.Invalid("Review bundle changed before replacement.")
                }
                val actual = current.bundle.integrity.payloadSha256
                if (actual != expectedPayloadSha256) {
                    return ReviewBundleSaveResult.Conflict(expectedPayloadSha256, actual)
                }
            }
            moveReplacing(temporary, path)
            ReviewBundleSaveResult.Saved(bundle)
        } finally {
            Files.deleteIfExists(temporary)
        }
    } catch (error: IllegalArgumentException) {
        ReviewBundleSaveResult.Invalid(error.message ?: "Invalid review bundle.")
    } catch (error: Exception) {
        ReviewBundleSaveResult.Failed(error.message ?: "Unable to save review bundle.")
    }

    private fun pathFor(proposalId: String): Path {
        require(proposalId.matches(Regex("review:[0-9a-f]{64}"))) { "Unsafe review proposal id." }
        return exactBundlePath ?: directory.resolve("review-${proposalId.removePrefix("review:")}.json")
    }

    private fun moveReplacing(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    public companion object {
        public fun atPath(
            bundlePath: Path,
            codec: JsonReviewBundleCodec = JsonReviewBundleCodec(),
        ): FileReviewBundleRepository {
            val normalized = bundlePath.toAbsolutePath().normalize()
            val parent = requireNotNull(normalized.parent) { "Review bundle path must have a parent." }
            return FileReviewBundleRepository(parent, codec, normalized)
        }
    }
}
