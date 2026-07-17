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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultSpecificationDifferTest {
    private val differ = DefaultSpecificationDiffer()

    @Test
    fun `returns empty diff for equivalent specifications regardless of ordering`() {
        val previous = specification()
        val current = previous.copy(
            packages = previous.packages.reversed(),
            components = previous.components.reversed(),
        )

        val diff = differ.diff(previous, current)

        assertEquals(SpecificationDiff.EMPTY, diff)
        assertFalse(diff.hasChanges)
    }

    @Test
    fun `detects added removed and modified entities by stable identity`() {
        val previous = specification()
        val current = specification(
            packages = listOf(packageSpec("package:sample", "Changed package"), packageSpec("package:new", null)),
            components = listOf(
                typeSpec(
                    id = "type:repository",
                    role = "Changed role",
                    apis = listOf(api("api:find", "find(id: Long)"), api("api:add", "add()")),
                    properties = emptyList(),
                ),
                typeSpec(id = "type:new", role = "New type", apis = emptyList(), properties = emptyList()),
            ),
        )

        val diff = differ.diff(previous, current)

        assertEquals(
            listOf("package:new" to ChangeKind.ADDED, "package:sample" to ChangeKind.MODIFIED),
            diff.packageChanges.map { it.id to it.kind },
        )
        assertEquals(
            listOf("type:new" to ChangeKind.ADDED, "type:repository" to ChangeKind.MODIFIED),
            diff.typeChanges.map { it.id to it.kind },
        )
        assertEquals(
            listOf("api:add" to ChangeKind.ADDED, "api:find" to ChangeKind.MODIFIED),
            diff.apiChanges.map { it.id to it.kind },
        )
        assertEquals(listOf("property:cache" to ChangeKind.REMOVED), diff.propertyChanges.map { it.id to it.kind })
        assertTrue(diff.hasChanges)
    }

    @Test
    fun `nested api-only change does not mark owning type modified`() {
        val previous = specification()
        val current = specification(
            components = listOf(typeSpec(apis = listOf(api("api:find", "find(id: Long)")))),
        )

        val diff = differ.diff(previous, current)

        assertTrue(diff.typeChanges.isEmpty())
        assertEquals(listOf("api:find"), diff.apiChanges.map { it.id })
        assertEquals("type:repository", diff.apiChanges.single().parentId)
    }

    @Test
    fun `rejects duplicate stable identities`() {
        val invalid = specification(packages = listOf(packageSpec(), packageSpec()))

        assertFailsWith<IllegalArgumentException> {
            differ.diff(invalid, specification())
        }
    }

    private fun specification(
        packages: List<PackageSpecification> = listOf(packageSpec()),
        components: List<ComponentSpecification> = listOf(typeSpec()),
    ) = ProjectSpecification(
        schemaVersion = "0.3",
        project = ProjectDescriptor("project:sample", "Sample"),
        modules = listOf(ModuleSpecification("module:app", "app")),
        packages = packages,
        components = components,
    )

    private fun packageSpec(id: String = "package:sample", description: String? = null) = PackageSpecification(
        id = id,
        name = id.substringAfter(':'),
        qualifiedName = "io.$id",
        moduleId = "module:app",
        description = description,
    )

    private fun typeSpec(
        id: String = "type:repository",
        role: String = "Repository",
        apis: List<ApiSpecification> = listOf(api()),
        properties: List<PropertySpecification> = listOf(property()),
    ) = ComponentSpecification(
        id = id,
        name = id.substringAfter(':'),
        moduleId = "module:app",
        packageId = "package:sample",
        kind = "class",
        role = role,
        apis = apis,
        properties = properties,
    )

    private fun api(id: String = "api:find", signature: String = "find(id: String)") = ApiSpecification(
        id = id,
        name = id.substringAfter(':'),
        kind = "function",
        signature = signature,
    )

    private fun property(id: String = "property:cache") = PropertySpecification(
        id = id,
        name = id.substringAfter(':'),
        type = "Map<String, User>",
    )
}
