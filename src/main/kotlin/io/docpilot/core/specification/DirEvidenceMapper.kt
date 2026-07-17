package io.docpilot.core.specification

import io.docpilot.core.model.Evidence as DirEvidence
import io.docpilot.core.model.EvidenceConfidence
import io.docpilot.core.model.evidence.Evidence

internal object DirEvidenceMapper {
    fun map(evidence: Evidence): DirEvidence = DirEvidence(
        id = evidence.id.value,
        type = evidence.type.name,
        file = evidence.location.relativePath,
        symbol = evidence.attributes["symbolName"],
        lineStart = evidence.location.lineStart,
        lineEnd = evidence.location.lineEnd,
        summary = evidence.summary,
        confidence = EvidenceConfidence.HIGH,
    )
}
