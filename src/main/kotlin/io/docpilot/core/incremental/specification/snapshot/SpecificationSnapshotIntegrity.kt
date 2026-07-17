package io.docpilot.core.incremental.specification.snapshot

import java.security.MessageDigest

internal object SpecificationSnapshotIntegrity {
    fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
