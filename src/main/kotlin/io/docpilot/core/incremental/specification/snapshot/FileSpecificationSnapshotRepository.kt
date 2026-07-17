package io.docpilot.core.incremental.specification.snapshot

import io.docpilot.core.model.ProjectSpecification
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

public class FileSpecificationSnapshotRepository(
    projectRoot: Path,
    private val codec: JsonSpecificationSnapshotCodec = JsonSpecificationSnapshotCodec(),
) : SpecificationSnapshotRepository {
    private val snapshotPath: Path = projectRoot.resolve(SpecificationSnapshotFormat.DEFAULT_RELATIVE_PATH)

    override fun load(expectedProjectId: String): SpecificationSnapshotLoadResult {
        if (!Files.exists(snapshotPath)) return SpecificationSnapshotLoadResult.NotFound
        if (!Files.isRegularFile(snapshotPath)) {
            return SpecificationSnapshotLoadResult.Invalid(
                SnapshotValidationFailure.CORRUPTED,
                "Specification snapshot path is not a regular file: $snapshotPath",
            )
        }
        return try {
            codec.decode(Files.readString(snapshotPath, StandardCharsets.UTF_8), expectedProjectId)
        } catch (e: Exception) {
            SpecificationSnapshotLoadResult.Invalid(
                SnapshotValidationFailure.CORRUPTED,
                e.message ?: "Unable to read specification snapshot.",
            )
        }
    }

    override fun save(specification: ProjectSpecification) {
        val parent = snapshotPath.parent
        Files.createDirectories(parent)
        val encoded = codec.encode(specification)
        val temp = Files.createTempFile(parent, "specification-", ".tmp")
        try {
            Files.writeString(temp, encoded, StandardCharsets.UTF_8)
            val validation = codec.decode(Files.readString(temp, StandardCharsets.UTF_8), specification.project.id)
            require(validation is SpecificationSnapshotLoadResult.Valid) {
                "Refusing to replace a valid snapshot with an invalid temporary snapshot: $validation"
            }
            moveReplacing(temp, snapshotPath)
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    private fun moveReplacing(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
