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
    fun `updates previous and current owners when api moves between types`() {
        val previous = specificationWithTwoTypes(apiOwnerId = "type:repository")
        val current = specificationWithTwoTypes(apiOwnerId = "type:service")

        val result = engine.analyze(previous, current)

        assertEquals(listOf("api:load"), result.diff.apiChanges.map { it.id })
        assertEquals(listOf("type:repository", "type:service"), result.plan.changedTypeIds)
        assertEquals(listOf("package:sample"), result.plan.changedPackageIds)
    }

    @Test
    fun `updates previous and current owners when property moves between types`() {
        val previous = specificationWithTwoTypes(propertyOwnerId = "type:repository")
        val current = specificationWithTwoTypes(propertyOwnerId = "type:service")

        val result = engine.analyze(previous, current)

        assertEquals(listOf("property:cache"), result.diff.propertyChanges.map { it.id })
        assertEquals(listOf("type:repository", "type:service"), result.plan.changedTypeIds)
        assertEquals(listOf("package:sample"), result.plan.changedPackageIds)
    }

    @Test
    fun `updates previous and current packages when type moves`() {
        val previous = specification(typePackageId = "package:sample")
        val current = specification(typePackageId = "package:other", includeOtherPackage = true)

        val result = engine.analyze(previous, current)

        assertEquals(listOf("type:repository"), result.diff.typeChanges.map { it.id })
        assertEquals(listOf("type:repository"), result.plan.changedTypeIds)
        assertEquals(listOf("package:other", "package:sample"), result.plan.changedPackageIds)
    }

    @Test
    fun `keeps update plan deterministic when specification order changes`() {
        val previous = specificationWithTwoTypes()
        val current = specificationWithTwoTypes(apiOwnerId = "type:service", propertyOwnerId = "type:service")

        val first = engine.analyze(previous, current).plan
        val second = engine.analyze(
            previous.copy(components = previous.components.reversed(), packages = previous.packages.reversed()),
            current.copy(components = current.components.reversed(), packages = current.packages.reversed()),
        ).plan

        assertEquals(first, second)
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
        typePackageId: String = "package:sample",
        includeOtherPackage: Boolean = false,
    ) = ProjectSpecification(
        schemaVersion = "0.3",
        project = ProjectDescriptor("project:sample", "Sample"),
        modules = listOf(ModuleSpecification("module:app", "app")),
        packages = buildList {
            add(
                PackageSpecification(
                    id = "package:sample",
                    name = "sample",
                    qualifiedName = "io.sample",
                    moduleId = "module:app",
                    description = packageDescription,
                ),
            )
            if (includeOtherPackage) {
                add(
                    PackageSpecification(
                        id = "package:other",
                        name = "other",
                        qualifiedName = "io.other",
                        moduleId = "module:app",
                    ),
                )
            }
        },
        components = listOf(
            ComponentSpecification(
                id = "type:repository",
                name = "Repository",
                moduleId = "module:app",
                packageId = typePackageId,
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


    private fun specificationWithTwoTypes(
        apiOwnerId: String = "type:repository",
        propertyOwnerId: String = "type:repository",
    ): ProjectSpecification {
        fun component(id: String, name: String) = ComponentSpecification(
            id = id,
            name = name,
            moduleId = "module:app",
            packageId = "package:sample",
            kind = "class",
            role = name,
            apis = if (apiOwnerId == id) listOf(
                ApiSpecification("api:load", "load", "function", "load(id: String)"),
            ) else emptyList(),
            properties = if (propertyOwnerId == id) listOf(
                PropertySpecification("property:cache", "cache", "Map<String, User>"),
            ) else emptyList(),
        )

        return ProjectSpecification(
            schemaVersion = "0.3",
            project = ProjectDescriptor("project:sample", "Sample"),
            modules = listOf(ModuleSpecification("module:app", "app")),
            packages = listOf(
                PackageSpecification("package:sample", "sample", "io.sample", "module:app"),
            ),
            components = listOf(
                component("type:repository", "Repository"),
                component("type:service", "Service"),
            ),
        )
    }

}
