package io.docpilot.core.incremental.persistence

import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Filesystem snapshot repository rooted at a project directory.
 */
class FileSnapshotRepository(
    projectRoot: Path,
    private val codec: SnapshotCodec = JsonSnapshotCodec(),
) : SnapshotRepository {

    private val snapshotDirectory: Path =
        projectRoot
            .toAbsolutePath()
            .normalize()
            .resolve(".docpilot")
            .resolve("snapshots")

    private val latestFile: Path =
        snapshotDirectory.resolve("latest.json")

    private val previousFile: Path =
        snapshotDirectory.resolve("previous.json")

    override fun loadLatest(): StoredProjectSnapshot? =
        load(latestFile)

    override fun loadPrevious(): StoredProjectSnapshot? =
        load(previousFile)

    override fun save(
        snapshot: StoredProjectSnapshot,
    ) {
        Files.createDirectories(snapshotDirectory)

        if (Files.exists(latestFile)) {
            moveReplacing(
                source = latestFile,
                target = previousFile,
            )
        }

        val temporaryFile = Files.createTempFile(
            snapshotDirectory,
            "latest-",
            ".tmp",
        )

        try {
            Files.writeString(
                temporaryFile,
                codec.encode(snapshot),
                StandardCharsets.UTF_8,
            )
            moveReplacing(
                source = temporaryFile,
                target = latestFile,
            )
        } finally {
            Files.deleteIfExists(temporaryFile)
        }
    }

    private fun load(
        file: Path,
    ): StoredProjectSnapshot? {
        if (!Files.exists(file)) {
            return null
        }

        require(Files.isRegularFile(file)) {
            "Snapshot path must be a regular file: $file"
        }

        val json = Files.readString(
            file,
            StandardCharsets.UTF_8,
        )
        return codec.decode(json)
    }

    private fun moveReplacing(
        source: Path,
        target: Path,
    ) {
        try {
            Files.move(
                source,
                target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (
            exception: AtomicMoveNotSupportedException,
        ) {
            Files.move(
                source,
                target,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }
}
