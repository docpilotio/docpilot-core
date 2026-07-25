package io.docpilot.core.validation

import io.docpilot.core.api.DocumentationArtifactDescriptor
import io.docpilot.core.api.DocumentationArtifactKind
import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.model.RenderedArtifact

public enum class DocumentationQualitySeverity { ERROR, WARNING }

public data class DocumentationQualityFinding(
    public val code: String,
    public val severity: DocumentationQualitySeverity,
    public val message: String,
    public val artifactPath: String? = null,
)

public data class DocumentationQualityMetrics(
    public val artifactCount: Int,
    public val totalBytes: Int,
    public val oversizedArtifactCount: Int,
    public val componentEvidenceCoveragePercent: Int,
    public val relationshipEvidenceCoveragePercent: Int,
    public val explanationCoveragePercent: Int,
    public val unresolvedRatioPercent: Int,
)

public data class DocumentationQualityReport(
    public val metrics: DocumentationQualityMetrics,
    public val findings: List<DocumentationQualityFinding>,
) {
    public val valid: Boolean
        get() = findings.none { it.severity == DocumentationQualitySeverity.ERROR }
}

public data class DocumentationQualityPolicy(
    public val maximumArtifactBytes: Int = 512_000,
    public val minimumEvidenceCoveragePercent: Int = 80,
    public val minimumExplanationCoveragePercent: Int = 60,
    public val maximumUnresolvedRatioPercent: Int = 50,
)

/**
 * Deterministic, presentation-independent release-quality checks.
 *
 * This validator never invents architecture facts or assigns subjective release
 * scores. It checks measurable completeness, traceability, navigation contracts,
 * and reviewability so a separate rubric and independent reviewer can make the
 * release decision.
 */
public class DocumentationQualityValidator(
    private val policy: DocumentationQualityPolicy = DocumentationQualityPolicy(),
) {
    public fun validate(
        specification: ProjectSpecification,
        catalog: List<DocumentationArtifactDescriptor>,
        artifacts: List<RenderedArtifact>,
    ): DocumentationQualityReport {
        val findings = mutableListOf<DocumentationQualityFinding>()
        val paths = catalog.map { it.relativePath }
        duplicateValues(paths).forEach {
            findings += error("DQV-001", "Duplicate catalog path: $it", it)
        }
        duplicateValues(catalog.map { it.artifactId.value }).forEach {
            findings += error("DQV-002", "Duplicate artifact identity: $it")
        }

        val ids = catalog.mapTo(hashSetOf()) { it.artifactId }
        catalog.sortedBy { it.artifactId.value }.forEach { descriptor ->
            descriptor.dependencyArtifactIds.filterNot(ids::contains).forEach { missing ->
                findings += error(
                    "DQV-003",
                    "Artifact ${descriptor.artifactId.value} references missing dependency ${missing.value}.",
                    descriptor.relativePath,
                )
            }
        }

        REQUIRED_KINDS.filterNot { required -> catalog.any { it.kind == required } }.forEach {
            findings += error("DQV-004", "Required documentation view is missing: ${it.name}.")
        }

        val renderedByPath = artifacts.associateBy { it.relativePath }
        paths.sorted().filterNot(renderedByPath::containsKey).forEach {
            findings += error("DQV-005", "Catalog Artifact was not rendered.", it)
        }
        renderedByPath.keys.filterNot(paths.toSet()::contains).sorted().forEach {
            findings += error("DQV-006", "Rendered Artifact is absent from the catalog.", it)
        }

        val oversized = artifacts.filter { it.content.toByteArray(Charsets.UTF_8).size > policy.maximumArtifactBytes }
        oversized.sortedBy { it.relativePath }.forEach {
            findings += warning(
                "DQV-101",
                "Artifact exceeds ${policy.maximumArtifactBytes} bytes and should use progressive disclosure.",
                it.relativePath,
            )
        }

        val componentEvidence = percent(
            specification.components.count { it.evidenceRefs.isNotEmpty() },
            specification.components.size,
        )
        val relationshipEvidence = percent(
            specification.relationships.count { it.evidenceRefs.isNotEmpty() },
            specification.relationships.size,
        )
        val explanations = specification.components.flatMap { component ->
            component.apis.map { it.purpose } + component.properties.map { it.purpose }
        }
        val explanationCoverage = percent(
            explanations.count { !it.isNullOrBlank() },
            explanations.size,
        )
        val unresolvedRatio = percent(
            specification.unresolved.size,
            specification.components.size + specification.relationships.size + specification.unresolved.size,
        )

        if (componentEvidence < policy.minimumEvidenceCoveragePercent) {
            findings += error("DQV-007", "Component Evidence coverage is $componentEvidence%.")
        }
        if (relationshipEvidence < policy.minimumEvidenceCoveragePercent) {
            findings += error("DQV-008", "Relationship Evidence coverage is $relationshipEvidence%.")
        }
        if (explanationCoverage < policy.minimumExplanationCoveragePercent) {
            findings += warning("DQV-102", "API/property explanation coverage is $explanationCoverage%.")
        }
        if (unresolvedRatio > policy.maximumUnresolvedRatioPercent) {
            findings += warning("DQV-103", "Unresolved ratio is $unresolvedRatio%.")
        }

        return DocumentationQualityReport(
            metrics = DocumentationQualityMetrics(
                artifactCount = artifacts.size,
                totalBytes = artifacts.sumOf { it.content.toByteArray(Charsets.UTF_8).size },
                oversizedArtifactCount = oversized.size,
                componentEvidenceCoveragePercent = componentEvidence,
                relationshipEvidenceCoveragePercent = relationshipEvidence,
                explanationCoveragePercent = explanationCoverage,
                unresolvedRatioPercent = unresolvedRatio,
            ),
            findings = findings.sortedWith(
                compareBy({ it.severity.ordinal }, { it.code }, { it.artifactPath ?: "" }, { it.message }),
            ),
        )
    }

    private fun percent(numerator: Int, denominator: Int): Int =
        if (denominator == 0) 100 else numerator * 100 / denominator

    private fun duplicateValues(values: List<String>): List<String> =
        values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted()

    private fun error(code: String, message: String, path: String? = null) =
        DocumentationQualityFinding(code, DocumentationQualitySeverity.ERROR, message, path)

    private fun warning(code: String, message: String, path: String? = null) =
        DocumentationQualityFinding(code, DocumentationQualitySeverity.WARNING, message, path)

    private companion object {
        val REQUIRED_KINDS = setOf(
            DocumentationArtifactKind.INDEX,
            DocumentationArtifactKind.PROJECT_OVERVIEW,
            DocumentationArtifactKind.ARCHITECTURE_OVERVIEW,
            DocumentationArtifactKind.RELATIONSHIP,
            DocumentationArtifactKind.EVIDENCE,
        )
    }
}
