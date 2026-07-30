package io.docpilot.core.specification

import io.docpilot.core.model.*
import kotlin.test.Test
import kotlin.test.assertFailsWith

class FeatureDir04SpecificationTest {
    @Test
    fun `validates complete DIR 0_4 feature flow`() {
        ProjectSpecificationValidator.validate(specification())
    }

    @Test
    fun `rejects missing references and noncanonical ordering without changing legacy behavior`() {
        assertFailsWith<IllegalArgumentException> {
            ProjectSpecificationValidator.validate(specification().copy(features = specification().features.reversed()))
        }
        assertFailsWith<IllegalArgumentException> {
            ProjectSpecificationValidator.validate(
                specification().copy(
                    scenarios = specification().scenarios.map {
                        it.copy(steps = it.steps.map { step -> step.copy(apiId = "api:missing") })
                    },
                ),
            )
        }
        ProjectSpecificationValidator.validate(
            specification().copy(schemaVersion = "0.3", features = emptyList(), entryPoints = emptyList(), scenarios = emptyList()),
        )
    }

    internal fun specification(): ProjectSpecification {
        val evidence = Evidence("e:feature", "SOURCE", "Feature.kt", "Feature", 1, 10, "feature scenario", EvidenceConfidence.HIGH)
        val component = ComponentSpecification(
            id = "component:feature", name = "Feature", moduleId = "module:app", kind = "CLASS", role = "feature",
            apis = listOf(ApiSpecification("api:feature:start", "start", "FUNCTION", evidenceRefs = setOf(evidence.id))),
        )
        val step = ScenarioStepSpecification(
            "scenario-step:open-feature:component-feature:api-feature-start", 10, "CALL", component.id, apiId = "api:feature:start",
            evidenceRefs = setOf(evidence.id),
        )
        return ProjectSpecification(
            schemaVersion = "0.4",
            project = ProjectDescriptor("project:test", "Test"),
            modules = listOf(ModuleSpecification("module:app", "app")),
            components = listOf(component),
            evidence = listOf(evidence),
            entryPoints = listOf(
                EntryPointSpecification("entry-point:PUBLIC_API:api-feature-start", "Start", "PUBLIC_API", component.id, "api:feature:start", setOf(evidence.id)),
            ),
            features = listOf(
                FeatureSpecification(
                    "feature:a", "Feature A", ownerComponentId = component.id,
                    entryPointIds = listOf("entry-point:PUBLIC_API:api-feature-start"), scenarioIds = listOf("scenario:open"),
                    evidenceRefs = setOf(evidence.id),
                ),
                FeatureSpecification("feature:z", "Feature Z", ownerComponentId = component.id, evidenceRefs = setOf(evidence.id)),
            ),
            scenarios = listOf(
                ScenarioSpecification(
                    "scenario:open", "feature:a", "Open", "entry-point:PUBLIC_API:api-feature-start", listOf(step), setOf(evidence.id),
                ),
            ),
        )
    }
}
