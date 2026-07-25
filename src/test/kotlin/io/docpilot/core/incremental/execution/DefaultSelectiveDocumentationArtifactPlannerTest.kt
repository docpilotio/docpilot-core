package io.docpilot.core.incremental.execution

import io.docpilot.core.api.DocumentationArtifactDescriptor
import io.docpilot.core.api.DocumentationArtifactId
import io.docpilot.core.api.DocumentationArtifactKind
import io.docpilot.core.incremental.specification.ChangeKind
import io.docpilot.core.incremental.specification.IncrementalUpdateAction
import io.docpilot.core.incremental.specification.IncrementalUpdatePlan
import io.docpilot.core.incremental.specification.IncrementalUpdateTarget
import io.docpilot.core.model.ProjectDescriptor
import io.docpilot.core.model.ProjectSpecification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DefaultSelectiveDocumentationArtifactPlannerTest {
    private val planner = DefaultSelectiveDocumentationArtifactPlanner()

    @Test
    fun `selects direct artifact and transitive dependents while keeping unrelated artifact`() {
        val component = descriptor("component:a", "docs/a.md", listOf("type:a"))
        val unrelated = descriptor("component:b", "docs/b.md", listOf("type:b"))
        val summary = descriptor("project:p", "docs/project.md", dependencies = listOf(component.artifactId))
        val catalog = listOf(component, unrelated, summary)

        val plan = planner.plan(request(catalog, catalog))

        assertEquals(DocumentationArtifactOperation.UPDATE, action(plan, "component:a").operation)
        assertEquals(DocumentationArtifactOperation.UPDATE, action(plan, "project:p").operation)
        assertEquals(DocumentationArtifactOperation.KEEP, action(plan, "component:b").operation)
        assertTrue(
            DocumentationArtifactReason.DEPENDENCY_REFRESH in action(plan, "project:p").reasons,
        )
        assertEquals(64, plan.planSha256.length)
    }

    @Test
    fun `creates missing expected artifact and retains removed catalog artifact as orphan`() {
        val removed = descriptor("component:old", "docs/old.md", listOf("type:old"))
        val current = descriptor("component:new", "docs/new.md", listOf("type:new"))

        val plan = planner.plan(
            request(
                previous = listOf(removed, current),
                current = listOf(current),
                existing = emptyList(),
            ),
        )

        assertEquals(DocumentationArtifactOperation.CREATE, action(plan, "component:new").operation)
        assertEquals(listOf("docs/old.md"), plan.orphanedArtifacts.map { it.relativePath })
    }

    @Test
    fun `fails closed for selected unknown-owned path`() {
        val component = descriptor("component:a", "docs/a.md", listOf("type:a"))
        assertFailsWith<IllegalArgumentException> {
            planner.plan(
                request(
                    previous = listOf(component),
                    current = listOf(component),
                    existing = listOf(
                        ExistingDocumentationArtifact(
                            "docs/a.md",
                            "text/markdown",
                            "manual",
                            DocumentationArtifactOwnership.UNKNOWN,
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun `plan is independent of catalog and inventory order`() {
        val a = descriptor("component:a", "docs/a.md", listOf("type:a"))
        val b = descriptor("component:b", "docs/b.md", listOf("type:b"))
        val first = planner.plan(request(listOf(a, b), listOf(a, b)))
        val second = planner.plan(request(listOf(b, a), listOf(b, a), existing().reversed()))

        assertEquals(first, second)
    }

    private fun request(
        previous: List<DocumentationArtifactDescriptor>,
        current: List<DocumentationArtifactDescriptor>,
        existing: List<ExistingDocumentationArtifact> = existing(),
    ) = DocumentationArtifactPlanningRequest(
        previousSpecification = specification(),
        currentSpecification = specification(),
        previousCatalog = previous,
        currentCatalog = current,
        updatePlan = IncrementalUpdatePlan(
            actions = listOf(
                IncrementalUpdateAction(
                    IncrementalUpdateTarget.TYPE,
                    "type:a",
                    changeKind = ChangeKind.MODIFIED,
                ),
            ),
            changedTypeIds = listOf("type:a"),
        ),
        existingArtifacts = existing,
    )

    private fun existing() = listOf(
        ExistingDocumentationArtifact("docs/a.md", "text/markdown", "a"),
        ExistingDocumentationArtifact("docs/b.md", "text/markdown", "b"),
        ExistingDocumentationArtifact("docs/project.md", "text/markdown", "project"),
    )

    private fun descriptor(
        id: String,
        path: String,
        scopes: List<String> = emptyList(),
        dependencies: List<DocumentationArtifactId> = emptyList(),
    ) = DocumentationArtifactDescriptor(
        DocumentationArtifactId(id),
        path,
        "text/markdown",
        DocumentationArtifactKind.COMPONENT,
        scopes.sorted(),
        dependencies.sortedBy { it.value },
    )

    private fun action(plan: DocumentationArtifactPlan, id: String) =
        plan.actions.single { it.artifactId == DocumentationArtifactId(id) }

    private fun specification() = ProjectSpecification(
        schemaVersion = "0.3",
        project = ProjectDescriptor("project", "Project"),
    )
}
