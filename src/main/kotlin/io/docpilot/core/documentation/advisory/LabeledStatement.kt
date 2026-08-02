package io.docpilot.core.documentation.advisory

public enum class StatementLabel { AS_IS, TO_BE }

/**
 * Fail-closed by construction: an AS_IS statement must cite Evidence/Contract; a TO_BE statement
 * (a proposal, not a fact) must not. Both `KnownIssuesRegisterBuilder` and `ExecutiveSummaryBuilder`
 * produce statements through this same type, so the invariant applies identically to deterministic
 * and AI-authored content.
 */
public data class LabeledStatement(
    public val label: StatementLabel,
    public val text: String,
    public val evidenceRefs: Set<String> = emptySet(),
) {
    init {
        require(text.isNotBlank()) { "Statement text must not be blank." }
        require(label != StatementLabel.AS_IS || evidenceRefs.isNotEmpty()) {
            "AS-IS statements must cite at least one Evidence/Contract reference."
        }
        require(label != StatementLabel.TO_BE || evidenceRefs.isEmpty()) {
            "TO-BE statements must not cite Evidence/Contract references — they are proposals, not facts."
        }
    }
}
