package io.docpilot.core.incremental

/**
 * Stable content identity for one project source file.
 */
data class SourceFileFingerprint(
    val relativePath: String,
    val contentSha256: String,
    val sizeBytes: Long,
) {
    init {
        require(relativePath.isNotBlank()) {
            "Source file relativePath must not be blank."
        }
        require('\\' !in relativePath) {
            "Source file relativePath must use '/' separators."
        }
        require(!relativePath.startsWith('/')) {
            "Source file relativePath must not be absolute."
        }
        require(SHA_256_PATTERN.matches(contentSha256)) {
            "Source file contentSha256 must be a lowercase SHA-256 value."
        }
        require(sizeBytes >= 0) {
            "Source file sizeBytes must not be negative."
        }
    }

    companion object {
        private val SHA_256_PATTERN = Regex("[0-9a-f]{64}")
    }
}
