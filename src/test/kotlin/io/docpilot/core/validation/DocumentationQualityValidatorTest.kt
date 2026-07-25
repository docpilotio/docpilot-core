package io.docpilot.core.validation

import io.docpilot.core.model.ApiSpecification
import io.docpilot.core.model.ComponentSpecification
import io.docpilot.core.model.Evidence
import io.docpilot.core.model.EvidenceConfidence
import io.docpilot.core.model.ModuleSpecification
import io.docpilot.core.model.ProjectDescriptor
import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.model.RelationshipSpecification
import io.docpilot.core.render.ProjectSpecificationMarkdownRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocumentationQualityValidatorTest {
    private val renderer = ProjectSpecificationMarkdownRenderer()

    @Test
    fun `accepts complete deterministic catalog and reports measurable metrics`() {
        val specification = specification()
        val report = DocumentationQualityValidator().validate(
            specification,
            renderer.describe(specification),
            renderer.render(specification),
        )

        assertTrue(report.valid)
        assertEquals(100, report.metrics.componentEvidenceCoveragePercent)
        assertEquals(100, report.metrics.relationshipEvidenceCoveragePercent)
        assertEquals(100, report.metrics.explanationCoveragePercent)
        assertTrue(report.findings.isEmpty())
    }

    @Test
    fun `fails closed for missing rendered views and missing evidence`() {
        val specification = specification().copy(
            components = specification().components.map { it.copy(evidenceRefs = emptySet()) },
            relationships = specification().relationships.map { it.copy(evidenceRefs = emptySet()) },
        )
        val catalog = renderer.describe(specification)
        val report = DocumentationQualityValidator().validate(
            specification,
            catalog,
            renderer.render(specification).filterNot { it.relativePath == "docs/architecture/overview.md" },
        )

        assertFalse(report.valid)
        assertEquals(listOf("DQV-005", "DQV-007", "DQV-008"), report.findings.map { it.code })
    }

    @Test
    fun `reports oversized output and low explanation as deterministic warnings`() {
        val specification = specification().copy(
            components = specification().components.map {
                it.copy(apis = it.apis.map { api -> api.copy(purpose = null) })
            },
        )
        val report = DocumentationQualityValidator(
            DocumentationQualityPolicy(maximumArtifactBytes = 1),
        ).validate(specification, renderer.describe(specification), renderer.render(specification))

        assertTrue(report.valid)
        assertTrue(report.findings.any { it.code == "DQV-101" })
        assertTrue(report.findings.any { it.code == "DQV-102" })
        assertEquals(report.findings.sortedWith(
            compareBy({ it.severity.ordinal }, { it.code }, { it.artifactPath ?: "" }, { it.message }),
        ), report.findings)
    }

    private fun specification(): ProjectSpecification = ProjectSpecification(
        schemaVersion = "0.3",
        project = ProjectDescriptor("project:sample", "Sample"),
        modules = listOf(ModuleSpecification("module:app", "app", evidenceRefs = setOf("e:module"))),
        components = listOf(
            ComponentSpecification(
                id = "type:service",
                name = "Service",
                moduleId = "module:app",
                kind = "CLASS",
                role = "service",
                apis = listOf(ApiSpecification("api:run", "run", "FUNCTION", purpose = "Runs work.")),
                evidenceRefs = setOf("e:type"),
            ),
        ),
        relationships = listOf(
            RelationshipSpecification(
                "relationship:call",
                "CALLS",
                "api:run",
                "external:worker",
                evidenceRefs = setOf("e:relationship"),
            ),
        ),
        evidence = listOf(
            Evidence("e:module", "SOURCE", summary = "module", confidence = EvidenceConfidence.HIGH),
            Evidence("e:type", "SOURCE", summary = "type", confidence = EvidenceConfidence.HIGH),
            Evidence("e:relationship", "SOURCE", summary = "call", confidence = EvidenceConfidence.HIGH),
        ),
    )
}
