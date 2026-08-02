package io.docpilot.core.specification.claim

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.Normalizer

internal object ClaimHashing {
    val FIELD_SEPARATOR: String = 0x1f.toChar().toString()

    fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun canonicalize(parts: List<String>): String =
        parts.joinToString(FIELD_SEPARATOR) { Normalizer.normalize(it.trim(), Normalizer.Form.NFC) }
}
