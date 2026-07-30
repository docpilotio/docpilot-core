package io.docpilot.core.documentation.profile

import io.docpilot.core.render.ProjectSpecificationMarkdownRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProfileArtifactCompatibilityTest {
    @Test
    fun `binds only explicit compatible legacy artifacts without replacing legacy paths`() {
        val specification = profileTestSpecification()
        val renderer = ProjectSpecificationMarkdownRenderer()
        val resolution = DefaultDocumentationProfileResolver().resolve(
            DocumentationProfileResolutionRequest(
                DocumentationProfileId("kotlin-android"),
                DocumentationProfileVersion(1),
                specification,
                renderer.describe(specification),
                renderer.capabilities(),
            ),
        )
        val byDocument = resolution.artifactBindings.associateBy { it.documentStableId }
        val project = resolution.documents.single { it.type == DocumentType.PROJECT_OVERVIEW }
        val architecture = resolution.documents.single { it.type == DocumentType.ARCHITECTURE_OVERVIEW }

        assertEquals(ProfileArtifactBindingStatus.LEGACY_KIND_COMPATIBLE, byDocument.getValue(project.documentStableId).status)
        assertEquals(ProfileArtifactBindingStatus.LEGACY_KIND_COMPATIBLE, byDocument.getValue(architecture.documentStableId).status)
        assertTrue(resolution.artifactBindings.any { it.status == ProfileArtifactBindingStatus.UNBOUND })
        assertEquals("project/project-overview.md", project.relativePath)
        assertTrue(renderer.describe(specification).any { it.relativePath == "docs/specification/project.md" })
    }
}
