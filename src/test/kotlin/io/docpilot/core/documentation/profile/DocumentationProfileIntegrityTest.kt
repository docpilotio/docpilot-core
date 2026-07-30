package io.docpilot.core.documentation.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocumentationProfileIntegrityTest {
    @Test
    fun `profile semantic identity ignores definition and set input order`() {
        val first = KotlinAndroidDocumentationProfile.profile
        val reversed = first.copy(
            supportedProjectKinds = first.supportedProjectKinds.reversed().toSet(),
            documentDefinitions = first.documentDefinitions.reversed().map { definition ->
                definition.copy(
                    primaryAudiences = definition.primaryAudiences.reversed().toSet(),
                    secondaryAudiences = definition.secondaryAudiences.reversed().toSet(),
                    sections = definition.sections.reversed(),
                    rendererCapabilities = definition.rendererCapabilities.reversed().toSet(),
                )
            },
        )

        assertEquals(
            DocumentationProfileIntegrity.profileSha256(first),
            DocumentationProfileIntegrity.profileSha256(reversed),
        )
    }

    @Test
    fun `section title changes semantic hash but preserves stable identity`() {
        val profile = minimalProfile()
        val changed = profile.copy(
            documentDefinitions = profile.documentDefinitions.map { definition ->
                definition.copy(sections = definition.sections.map { it.copy(title = "Summary") })
            },
        )

        assertEquals(
            profile.documentDefinitions.single().stableKey,
            changed.documentDefinitions.single().stableKey,
        )
        assertFalse(
            DocumentationProfileIntegrity.profileSha256(profile) ==
                DocumentationProfileIntegrity.profileSha256(changed),
        )
    }

    @Test
    fun `resolution integrity detects semantic tampering`() {
        val renderer = io.docpilot.core.render.ProjectSpecificationMarkdownRenderer()
        val resolution = DefaultDocumentationProfileResolver().resolve(
            DocumentationProfileResolutionRequest(
                DocumentationProfileId("kotlin-android"),
                DocumentationProfileVersion(1),
                profileTestSpecification(),
                renderer.describe(profileTestSpecification()),
                renderer.capabilities(),
            ),
        )

        assertTrue(DocumentationProfileIntegrity.verifyResolution(resolution))
        val tamperedDocument = resolution.documents.first().copy(relativePath = "tampered.md")
        val tampered = resolution.copy(documents = listOf(tamperedDocument) + resolution.documents.drop(1))
        assertFalse(DocumentationProfileIntegrity.verifyResolution(tampered))
    }
}
