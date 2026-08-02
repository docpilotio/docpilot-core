package io.docpilot.core.specification.finding

@JvmInline
public value class FindingId(public val value: String) {
    init {
        require(value.isNotBlank()) { "Finding id must not be blank." }
    }
}

public enum class FindingSeverity { CRITICAL, HIGH, MEDIUM, LOW, INFO }

public data class Finding(
    public val formatVersion: Int = 1,
    public val id: FindingId,
    public val subjectStableId: String,
    public val semanticKey: String,
    public val category: String,
    public val severity: FindingSeverity,
    public val summary: String,
    public val evidenceRefs: Set<String>,
    public val unresolvedRefs: Set<String> = emptySet(),
)
