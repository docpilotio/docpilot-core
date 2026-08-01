package io.docpilot.core.specification

import io.docpilot.core.model.ContractKind
import io.docpilot.core.model.ContractOwner
import io.docpilot.core.model.ContractRole
import io.docpilot.core.model.ContractRelationshipKind
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.Normalizer

public object ContractIdentity {
    public fun of(kind: ContractKind, role: ContractRole, owner: ContractOwner, semanticKey: String): String {
        val canonical = listOf(kind.name, role.name, owner.kind.name, owner.stableId, semanticKey)
            .joinToString("\u001f") { Normalizer.normalize(it.trim(), Normalizer.Form.NFC) }
        val hash = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "contract:${hash.take(32)}"
    }

    public fun nested(contractId: String, category: String, semanticKey: String): String {
        val canonical = listOf(contractId, category, semanticKey)
            .joinToString("\u001f") { Normalizer.normalize(it.trim(), Normalizer.Form.NFC) }
        val hash = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "$contractId:$category:${hash.take(16)}"
    }

    public fun relationship(contractId: String, kind: ContractRelationshipKind, targetStableId: String): String =
        nested(contractId, "relationship", "${kind.name}\u001f$targetStableId")
}
