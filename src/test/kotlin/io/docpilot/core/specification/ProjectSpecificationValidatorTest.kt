package io.docpilot.core.specification

import io.docpilot.core.model.ComponentSpecification
import io.docpilot.core.model.ModuleSpecification
import io.docpilot.core.model.PackageSpecification
import io.docpilot.core.model.ProjectDescriptor
import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.model.RelationshipSpecification
import io.docpilot.core.model.UnresolvedItem
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals

class ProjectSpecificationValidatorTest {
    @Test
    fun `rejects blank DIR entity ids`() {
        val specification = validSpecification().copy(
            modules = listOf(ModuleSpecification(" ", "app")),
        )

        assertFailsWith<IllegalArgumentException> {
            ProjectSpecificationValidator.validate(specification)
        }
    }

    @Test
    fun `rejects component package that does not exist`() {
        val specification = validSpecification().copy(
            components = listOf(component(packageId = "package:missing")),
        )

        assertFailsWith<IllegalArgumentException> {
            ProjectSpecificationValidator.validate(specification)
        }
    }

    @Test
    fun `rejects component package from another module`() {
        val specification = validSpecification().copy(
            modules = listOf(ModuleSpecification("module:app", "app"), ModuleSpecification("module:lib", "lib")),
            packages = listOf(PackageSpecification("package:sample", "sample", "io.sample", "module:lib")),
        )

        assertFailsWith<IllegalArgumentException> {
            ProjectSpecificationValidator.validate(specification)
        }
    }

    @Test
    fun `rejects blank relationship endpoints`() {
        val specification = validSpecification().copy(
            relationships = listOf(RelationshipSpecification("relationship:test", "USES", "", "type:repository")),
        )

        assertFailsWith<IllegalArgumentException> {
            ProjectSpecificationValidator.validate(specification)
        }
    }

    @Test
    fun `accepts internal source and external or evidenced unresolved targets`() {
        val specification = validSpecification().copy(
            relationships = listOf(
                RelationshipSpecification("relationship:internal", "USES", "type:repository", "module:app"),
                RelationshipSpecification("relationship:external", "USES", "type:repository", "external:kotlin.String"),
                RelationshipSpecification("relationship:unresolved", "USES", "type:repository", "unresolved:missing:target"),
            ),
            unresolved = listOf(UnresolvedItem("missing", "type:repository", "Resolve target")),
        )

        ProjectSpecificationValidator.validate(specification)
        assertEquals(3, specification.relationships.size)
    }

    @Test
    fun `rejects non internal relationship source`() {
        assertFailsWith<IllegalArgumentException> {
            ProjectSpecificationValidator.validate(validSpecification().copy(
                relationships = listOf(RelationshipSpecification("relationship:test", "USES", "external:source", "module:app")),
            ))
        }
    }

    @Test
    fun `rejects unresolved target without matching evidence`() {
        assertFailsWith<IllegalArgumentException> {
            ProjectSpecificationValidator.validate(validSpecification().copy(
                relationships = listOf(RelationshipSpecification("relationship:test", "USES", "type:repository", "unresolved:missing:target")),
            ))
        }
    }

    @Test
    fun `rejects raw unknown endpoint fallback`() {
        val specification = validSpecification().copy(
            relationships = listOf(
                RelationshipSpecification("relationship:test", "USES", "type:repository", "unknown:type"),
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            ProjectSpecificationValidator.validate(specification)
        }
    }

    @Test
    fun `rejects structural self relationships`() {
        val specification = validSpecification().copy(
            relationships = listOf(
                RelationshipSpecification("relationship:test", "USES", "type:repository", "type:repository"),
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            ProjectSpecificationValidator.validate(specification)
        }
    }

    @Test
    fun `enforces direct depends on dependency ids`() {
        val relationship = RelationshipSpecification(
            "relationship:test",
            "DEPENDS_ON",
            "type:repository",
            "external:database",
        )
        val valid = validSpecification().copy(
            components = listOf(component().copy(dependencyIds = setOf("external:database"))),
            relationships = listOf(
                relationship,
                RelationshipSpecification(
                    "relationship:unresolved",
                    "DEPENDS_ON",
                    "type:repository",
                    "unresolved:missing:target",
                ),
            ),
            unresolved = listOf(UnresolvedItem("missing", "type:repository", "Resolve target")),
        )
        ProjectSpecificationValidator.validate(valid)

        assertFailsWith<IllegalArgumentException> {
            ProjectSpecificationValidator.validate(
                valid.copy(components = listOf(component().copy(dependencyIds = setOf("external:other")))),
            )
        }
    }

    private fun validSpecification() = ProjectSpecification(
        schemaVersion = "0.3",
        project = ProjectDescriptor("project:sample", "Sample"),
        modules = listOf(ModuleSpecification("module:app", "app")),
        packages = listOf(PackageSpecification("package:sample", "sample", "io.sample", "module:app")),
        components = listOf(component()),
    )

    private fun component(packageId: String = "package:sample") = ComponentSpecification(
        id = "type:repository",
        name = "Repository",
        moduleId = "module:app",
        packageId = packageId,
        kind = "class",
        role = "Repository",
    )
}
