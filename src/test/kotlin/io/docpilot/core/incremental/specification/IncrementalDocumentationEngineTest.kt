package io.docpilot.core.incremental.specification

import io.docpilot.core.model.ApiSpecification
import io.docpilot.core.model.ComponentSpecification
import io.docpilot.core.model.ModuleSpecification
import io.docpilot.core.model.PackageSpecification
import io.docpilot.core.model.ProjectDescriptor
import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.model.PropertySpecification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IncrementalDocumentationEngineTest {
    private val engine = IncrementalDocumentationEngine()

    @Test
    fun `builds deterministic update plan for changed package type api and property`() {
        val previous = specification()
        val current = specification(
            packageDescription = "Updated",
            typeRole = "Updated role",
            apiSignature = "load(id: Long)",
            propertyType = "MutableMap<String, User>",
        )

        val result = engine.analyze(previous, current)

        assertTrue(result.plan.requiresUpdate)
        assertEquals(listOf("package:sample"), result.plan.changedPackageIds)
        assertEquals(listOf("type:repository"), result.plan.changedTypeIds)
        assertEquals(
            listOf(
                IncrementalUpdateTarget.PACKAGE,
                IncrementalUpdateTarget.TYPE,
                IncrementalUpdateTarget.API,
                IncrementalUpdateTarget.PROPERTY,
            ),
            result.plan.actions.map { it.target },
        )
        assertTrue(result.plan.actions.all { it.changeKind == ChangeKind.MODIFIED })
    }

    @Test
    fun `propagates api change to owning type and package without changing renderer contract`() {
        val result = engine.analyze(specification(), specification(apiSignature = "load(id: Long)"))

        assertEquals(listOf("api:load"), result.diff.apiChanges.map { it.id })
        assertTrue(result.diff.typeChanges.isEmpty())
        assertEquals(listOf("type:repository"), result.plan.changedTypeIds)
        assertEquals(listOf("package:sample"), result.plan.changedPackageIds)
    }

    @Test
    fun `returns empty plan when specification is unchanged`() {
        val specification = specification()

        val result = engine.analyze(specification, specification.copy())

        assertFalse(result.diff.hasChanges)
        assertEquals(IncrementalUpdatePlan.EMPTY, result.plan)
    }

    private fun specification(
        packageDescription: String? = null,
        typeRole: String = "Repository",
        apiSignature: String = "load(id: String)",
        propertyType: String = "Map<String, User>",
    ) = ProjectSpecification(
        schemaVersion = "0.3",
        project = ProjectDescriptor("project:sample", "Sample"),
        modules = listOf(ModuleSpecification("module:app", "app")),
        packages = listOf(
            PackageSpecification(
                id = "package:sample",
                name = "sample",
                qualifiedName = "io.sample",
                moduleId = "module:app",
                description = packageDescription,
            ),
        ),
        components = listOf(
            ComponentSpecification(
                id = "type:repository",
                name = "Repository",
                moduleId = "module:app",
                packageId = "package:sample",
                kind = "class",
                role = typeRole,
                apis = listOf(
                    ApiSpecification(
                        id = "api:load",
                        name = "load",
                        kind = "function",
                        signature = apiSignature,
                    ),
                ),
                properties = listOf(
                    PropertySpecification(
                        id = "property:cache",
                        name = "cache",
                        type = propertyType,
                    ),
                ),
            ),
        ),
    )
}
