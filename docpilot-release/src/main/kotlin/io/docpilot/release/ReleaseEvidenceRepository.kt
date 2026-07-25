package io.docpilot.release

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

public class ReleaseEvidenceRepository(
    private val codec: ReleaseEvidenceCodec = ReleaseEvidenceCodec(),
    private val renderer: ReleaseGateMarkdownRenderer = ReleaseGateMarkdownRenderer(),
) {
    public fun saveNew(outputRoot: Path, manifest: ReleaseEvidenceManifest): Path {
        val normalizedRoot = outputRoot.toAbsolutePath().normalize()
        require(!Files.exists(normalizedRoot)) { "Release evidence already exists: $normalizedRoot" }
        val parent = normalizedRoot.parent ?: error("Release evidence requires a parent directory.")
        Files.createDirectories(parent)
        val temp = Files.createTempDirectory(parent, ".${normalizedRoot.fileName}.tmp-")
        try {
            Files.writeString(temp.resolve(MANIFEST_FILE), codec.encode(manifest), Charsets.UTF_8)
            Files.writeString(temp.resolve(REPORT_FILE), renderer.render(manifest), Charsets.UTF_8)
            codec.decode(Files.readString(temp.resolve(MANIFEST_FILE), Charsets.UTF_8))
            publish(temp, normalizedRoot)
            return normalizedRoot.resolve(MANIFEST_FILE)
        } catch (failure: Exception) {
            deleteOwnedTemp(temp)
            throw failure
        }
    }

    public fun load(manifestPath: Path): ReleaseEvidenceManifest {
        val path = manifestPath.toAbsolutePath().normalize()
        require(Files.isRegularFile(path)) { "Release evidence manifest does not exist: $path" }
        return codec.decode(Files.readString(path, Charsets.UTF_8))
    }

    private fun publish(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target)
        }
    }

    private fun deleteOwnedTemp(path: Path) {
        if (!Files.exists(path) || !path.fileName.toString().startsWith(".")) return
        Files.walk(path).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    public companion object {
        public const val MANIFEST_FILE: String = "release-evidence.json"
        public const val REPORT_FILE: String = "release-gate.md"
    }
}
