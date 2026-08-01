package io.docpilot.core.specification

import io.docpilot.core.api.DocumentationArtifactKind
import io.docpilot.core.render.ProjectSpecificationMarkdownRenderer
import io.docpilot.core.incremental.execution.DefaultSelectiveDocumentationArtifactPlanner
import io.docpilot.core.incremental.execution.DocumentationArtifactOperation
import io.docpilot.core.incremental.execution.DocumentationArtifactPlanningRequest
import io.docpilot.core.incremental.execution.ExistingDocumentationArtifact
import io.docpilot.core.incremental.specification.DefaultIncrementalSpecificationPlanner
import io.docpilot.core.incremental.specification.DefaultSpecificationDiffer
import io.docpilot.core.model.Evidence
import io.docpilot.core.model.EvidenceConfidence
import io.docpilot.core.model.UnresolvedItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeatureDocumentationRenderingTest {
    private val renderer = ProjectSpecificationMarkdownRenderer()

    @Test
    fun `renders catalog and feature details only from DIR 0_4 facts`() {
        val specification = FeatureDir04SpecificationTest().specification()
        val descriptors = renderer.describe(specification)
        val featureDescriptors = descriptors.filter {
            it.kind == DocumentationArtifactKind.FEATURE_CATALOG || it.kind == DocumentationArtifactKind.FEATURE_DETAIL
        }
        val artifacts = renderer.render(specification, featureDescriptors.mapTo(linkedSetOf()) { it.artifactId })
        val catalog = artifacts.single { it.relativePath == "docs/project/feature-catalog.md" }.content
        val detail = artifacts.single { it.content.startsWith("# Feature A\n") }.content

        assertEquals(3, artifacts.size)
        assertTrue(catalog.indexOf("`feature:a`") < catalog.indexOf("`feature:z`"))
        assertTrue(catalog.contains("../features/feature-a-"))
        assertTrue(detail.contains("## Entry Points"))
        assertTrue(detail.contains("`PUBLIC_API`"))
        assertTrue(detail.contains("## Scenarios"))
        assertTrue(detail.contains("10. CALL"))
        assertTrue(detail.contains("Feature.kt"))
        assertFalse(detail.contains(Regex("[A-Z]:\\\\")))
    }

    @Test
    fun `feature artifacts and bytes are deterministic for reordered collections`() {
        val specification = FeatureDir04SpecificationTest().specification()
        val reversed = specification.copy(
            features = specification.features.reversed(),
            entryPoints = specification.entryPoints.reversed(),
            scenarios = specification.scenarios.reversed().map { scenario ->
                scenario.copy(
                    evidenceRefs = scenario.evidenceRefs.reversed().toSet(),
                    steps = scenario.steps.reversed().map { it.copy(evidenceRefs = it.evidenceRefs.reversed().toSet()) },
                )
            },
            evidence = specification.evidence.reversed(),
        )

        assertEquals(renderer.describe(specification), renderer.describe(reversed))
        assertEquals(renderer.render(specification), renderer.render(reversed))
    }

    @Test
    fun `renders nested evidence and unresolved facts and marks the feature partial`() {
        val base = FeatureDir04SpecificationTest().specification()
        val nestedEvidence = Evidence(
            "e:nested", "SOURCE", "src/Feature.kt", "openFeature", 20, 24,
            "nested scenario evidence", EvidenceConfidence.HIGH,
        )
        val nestedUnresolved = UnresolvedItem("u:nested", "scenario-step:open", "Target cannot be resolved")
        val scenario = base.scenarios.single().let { value ->
            value.copy(
                steps = value.steps.map { step ->
                    step.copy(
                        evidenceRefs = step.evidenceRefs + nestedEvidence.id,
                        unresolvedRefs = step.unresolvedRefs + nestedUnresolved.id,
                    )
                },
            )
        }
        val specification = base.copy(
            evidence = base.evidence + nestedEvidence,
            unresolved = listOf(nestedUnresolved),
            scenarios = listOf(scenario),
        )

        val detail = renderer.render(specification).single {
            it.content.startsWith("# Feature A\n")
        }.content

        assertTrue(detail.contains("- Completeness: PARTIAL"))
        assertTrue(detail.contains("`e:nested` — nested scenario evidence; `src/Feature.kt`; symbol `openFeature`; lines 20-24"))
        assertTrue(detail.contains("`u:nested` — Target cannot be resolved"))
    }

    @Test
    fun `nested evidence change updates only the owning feature and its catalog`() {
        val previous = FeatureDir04SpecificationTest().specification()
        val nestedEvidence = Evidence(
            "e:nested", "SOURCE", "src/Feature.kt", "openFeature", 20, 24,
            "nested scenario evidence", EvidenceConfidence.HIGH,
        )
        val current = previous.copy(
            evidence = previous.evidence + nestedEvidence,
            scenarios = previous.scenarios.map { scenario ->
                scenario.copy(steps = scenario.steps.map { step ->
                    step.copy(evidenceRefs = step.evidenceRefs + nestedEvidence.id)
                })
            },
        )
        val previousCatalog = renderer.describe(previous)
        val currentCatalog = renderer.describe(current)
        val updatePlan = DefaultIncrementalSpecificationPlanner().plan(
            DefaultSpecificationDiffer().diff(previous, current),
            previous,
            current,
        )
        val plan = DefaultSelectiveDocumentationArtifactPlanner().plan(
            DocumentationArtifactPlanningRequest(
                previous,
                current,
                previousCatalog,
                currentCatalog,
                updatePlan,
                currentCatalog.map { ExistingDocumentationArtifact(it.relativePath, it.mediaType, "existing") },
            ),
        )

        assertEquals(
            DocumentationArtifactOperation.UPDATE,
            plan.actions.single { it.artifactId.value == "feature:feature:a" }.operation,
        )
        assertEquals(
            DocumentationArtifactOperation.KEEP,
            plan.actions.single { it.artifactId.value == "feature:feature:z" }.operation,
        )
        assertEquals(
            DocumentationArtifactOperation.UPDATE,
            plan.actions.single { it.artifactId.value.startsWith("feature-catalog:") }.operation,
        )
    }
}
