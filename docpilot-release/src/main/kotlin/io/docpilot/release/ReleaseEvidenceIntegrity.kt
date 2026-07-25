package io.docpilot.release

import java.security.MessageDigest

internal fun sha256(value: ByteArray): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value)
        .joinToString("") { "%02x".format(it) }

internal fun sha256(value: String): String = sha256(value.toByteArray(Charsets.UTF_8))
