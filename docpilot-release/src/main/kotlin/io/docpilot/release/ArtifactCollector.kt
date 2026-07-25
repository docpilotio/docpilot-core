package io.docpilot.release

import java.nio.file.Files
import java.nio.file.Path

public class ArtifactCollector {
    public fun collect(
        repository: Path,
        id: String,
        kind: ArtifactKind,
        path: Path,
        required: Boolean,
        producerExecutionId: String,
    ): ReleaseArtifact {
        val root = repository.toAbsolutePath().normalize()
        val absolute = path.toAbsolutePath().normalize()
        require(absolute.startsWith(root)) { "Artifact must be inside the repository." }
        require(Files.isRegularFile(absolute)) { "Artifact is not a regular file: $absolute" }
        val relative = root.relativize(absolute).toString().replace('\\', '/')
        val bytes = Files.readAllBytes(absolute)
        return ReleaseArtifact(
            id, kind, relative, bytes.size.toLong(), sha256(bytes), required, producerExecutionId,
        )
    }

    public fun verify(repository: Path, artifact: ReleaseArtifact): Boolean {
        val root = repository.toAbsolutePath().normalize()
        val path = root.resolve(artifact.path).normalize()
        if (!path.startsWith(root) || !Files.isRegularFile(path)) return false
        val bytes = Files.readAllBytes(path)
        return bytes.size.toLong() == artifact.sizeBytes && sha256(bytes) == artifact.sha256
    }
}
