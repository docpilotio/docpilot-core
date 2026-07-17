package io.docpilot.core.specification

import io.docpilot.core.model.ComponentSpecification
import io.docpilot.core.model.ModuleSpecification
import io.docpilot.core.model.PackageSpecification
import io.docpilot.core.model.ProjectDescriptor
import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.model.RelationshipSpecification
import kotlin.test.Test
import kotlin.test.assertFailsWith

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
