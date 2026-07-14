package io.docpilot.core.model

/**
 * Result produced by an analysis engine.
 */
public data class AnalysisResult(
    public val specification: ProjectSpecification,
    public val diagnostics: List<AnalysisDiagnostic> = emptyList(),
)

public data class AnalysisDiagnostic(
    public val severity: DiagnosticSeverity,
    public val message: String,
    public val evidenceId: String? = null,
)

public enum class DiagnosticSeverity {
    INFO,
    WARNING,
    ERROR,
}
