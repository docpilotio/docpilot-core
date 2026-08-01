package io.docpilot.core.documentation.profile

import io.docpilot.core.render.ProjectSpecificationMarkdownRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocumentationProfileContractTest {
    @Test
    fun `built-in kotlin android profile exposes canonical document contracts`() {
        val profile = KotlinAndroidDocumentationProfile.profile

        assertEquals("kotlin-android", profile.id.value)
        assertEquals(1, profile.version.value)
        assertEquals(DocumentationProfileCompatibilityPolicy.EXACT_VERSION, profile.compatibilityPolicy)
        assertEquals(11, profile.documentDefinitions.size)
        assertEquals(
            profile.documentDefinitions.sortedBy { it.stableKey.value },
            profile.documentDefinitions,
        )
        assertTrue(profile.documentDefinitions.all { it.purpose.isNotBlank() })
        assertTrue(profile.documentDefinitions.all { it.primaryAudiences.isNotEmpty() })
        assertTrue(profile.documentDefinitions.all { it.requiredSections.isNotEmpty() })
        assertTrue(
            profile.documentDefinitions.all {
                RendererCapability.MARKDOWN_SECTION_RENDERING in it.rendererCapabilities
            },
        )
        assertEquals(
            DocumentMultiplicity.PER_FEATURE,
            profile.documentDefinitions.single { it.type == DocumentType.FEATURE_SPECIFICATION }.multiplicity,
        )
        assertEquals(
            DocumentMultiplicity.PER_CONTRACT,
            profile.documentDefinitions.single { it.type == DocumentType.CONTRACT_DETAIL }.multiplicity,
        )
    }

    @Test
    fun `legacy renderer declares additive capabilities without changing renderer contract`() {
        val renderer = ProjectSpecificationMarkdownRenderer()

        assertEquals(
            setOf(
                RendererCapability.MARKDOWN_SECTION_RENDERING,
                RendererCapability.EVIDENCE_REFERENCE_RENDERING,
                RendererCapability.UNKNOWN_FINDING_RENDERING,
                RendererCapability.FEATURE_DOCUMENTATION_RENDERING,
                RendererCapability.CONTRACT_DOCUMENTATION_RENDERING,
            ),
            renderer.capabilities(),
        )
        assertTrue(renderer.describe(profileTestSpecification()).isNotEmpty())
    }
}
