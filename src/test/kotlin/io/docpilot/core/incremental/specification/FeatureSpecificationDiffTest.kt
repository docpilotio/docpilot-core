package io.docpilot.core.incremental.specification

import io.docpilot.core.model.*
import kotlin.test.Test
import kotlin.test.assertEquals

class FeatureSpecificationDiffTest {
    @Test
    fun `diffs and plans feature flow entities including step reorder`() {
        val project = ProjectDescriptor("project:test", "Test")
        val before = ProjectSpecification(
            schemaVersion = "0.4", project = project,
            features = listOf(FeatureSpecification("feature:a", "A", ownerComponentId = "component:a")),
            scenarios = listOf(ScenarioSpecification("scenario:a", "feature:a", "A", steps = listOf(
                ScenarioStepSpecification("scenario-step:a", 10, "CALL", "component:a"),
            ))),
        )
        val after = before.copy(
            features = listOf(before.features.single().copy(name = "Changed")),
            entryPoints = listOf(EntryPointSpecification("entry-point:PUBLIC_API:a", "A", "PUBLIC_API", "component:a")),
            scenarios = listOf(before.scenarios.single().copy(steps = listOf(
                ScenarioStepSpecification("scenario-step:a", 20, "CALL", "component:a"),
            ))),
        )
        val diff = DefaultSpecificationDiffer().diff(before, after)
        assertEquals(ChangeKind.MODIFIED, diff.featureChanges.single().kind)
        assertEquals(ChangeKind.ADDED, diff.entryPointChanges.single().kind)
        assertEquals(ChangeKind.MODIFIED, diff.scenarioStepChanges.single().kind)
        assertEquals(
            listOf(IncrementalUpdateTarget.FEATURE, IncrementalUpdateTarget.ENTRY_POINT, IncrementalUpdateTarget.SCENARIO_STEP),
            DefaultIncrementalSpecificationPlanner().plan(diff, before, after).actions.map { it.target },
        )
    }
}
