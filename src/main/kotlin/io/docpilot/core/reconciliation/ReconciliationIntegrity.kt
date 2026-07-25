package io.docpilot.core.reconciliation

import java.security.MessageDigest

internal object ReconciliationIntegrity {
    fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun manifestPayload(manifest: DocumentationOwnershipManifest): String = buildString {
        append(manifest.formatVersion).append('|').append(manifest.artifactId.value).append('|')
        append(manifest.relativePath).append('|').append(manifest.mediaType).append('|')
        append(manifest.ownership.name).append('|').append(manifest.reviewedBaseSha256 ?: "").append('|')
        append(manifest.rendererIdentity).append('|')
        append(manifest.evidenceRefs.distinct().sorted().joinToString(",")).append('\n')
        manifest.managedBlocks.sortedBy { it.blockId }.forEach {
            append(it.blockId).append('|').append(it.targetId).append('|')
                .append(it.reviewedBaseContentSha256).append('|')
                .append(it.lastAppliedContentSha256).append('\n')
        }
    }

    fun signManifest(manifest: DocumentationOwnershipManifest): DocumentationOwnershipManifest =
        manifest.copy(manifestSha256 = sha256(manifestPayload(manifest)))

    fun verifyManifest(manifest: DocumentationOwnershipManifest): Boolean =
        manifest.formatVersion == 1 &&
            manifest.manifestSha256 == sha256(manifestPayload(manifest))

    fun safePath(path: String): Boolean =
        path.isNotBlank() && !path.startsWith("/") && !path.contains('\\') &&
            path.split('/').none { it == ".." || it.isBlank() }
}
