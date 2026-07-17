package io.docpilot.core.incremental

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * SHA-256 based project snapshot builder.
 */
class DefaultProjectSnapshotBuilder : ProjectSnapshotBuilder {

    override fun build(
        projectRoot: Path,
        relativePaths: Collection<String>,
    ): ProjectSnapshot {
        val normalizedRoot = projectRoot
            .toAbsolutePath()
            .normalize()

        require(Files.isDirectory(normalizedRoot)) {
            "Project root must be an existing directory: $normalizedRoot"
        }

        val normalizedPaths = relativePaths
            .map(::normalizeRelativePath)

        require(
            normalizedPaths.distinct().size == normalizedPaths.size,
        ) {
            "Project snapshot relative paths must be unique."
        }

        val fingerprints = normalizedPaths
            .sorted()
            .map { relativePath ->
                fingerprint(
                    projectRoot = normalizedRoot,
                    relativePath = relativePath,
                )
            }

        return ProjectSnapshot(fingerprints)
    }

    private fun fingerprint(
        projectRoot: Path,
        relativePath: String,
    ): SourceFileFingerprint {
        val file = projectRoot
            .resolve(relativePath)
            .normalize()

        require(file.startsWith(projectRoot)) {
            "Project file must remain inside project root: $relativePath"
        }
        require(Files.isRegularFile(file)) {
            "Project file must be a regular file: $relativePath"
        }

        val content = Files.readAllBytes(file)

        return SourceFileFingerprint(
            relativePath = relativePath,
            contentSha256 = sha256(content),
            sizeBytes = content.size.toLong(),
        )
    }

    private fun normalizeRelativePath(
        value: String,
    ): String {
        require(value.isNotBlank()) {
            "Project snapshot relative path must not be blank."
        }

        val normalized = value
            .replace('\\', '/')
            .removePrefix("./")

        require(!normalized.startsWith('/')) {
            "Project snapshot path must be relative: $value"
        }
        require(
            normalized.split('/').none { segment ->
                segment == ".."
            },
        ) {
            "Project snapshot path must not escape the project root: $value"
        }

        return normalized
    }

    private fun sha256(
        content: ByteArray,
    ): String =
        MessageDigest.getInstance("SHA-256")
            .digest(content)
            .joinToString(separator = "") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }
}
