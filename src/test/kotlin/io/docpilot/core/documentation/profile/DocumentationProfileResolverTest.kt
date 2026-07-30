package io.docpilot.core.documentation.profile

import io.docpilot.core.api.DocumentationArtifactDescriptor
import io.docpilot.core.api.DocumentationArtifactId
import io.docpilot.core.api.DocumentationArtifactKind
import io.docpilot.core.model.ModuleSpecification
import io.docpilot.core.reconciliation.DocumentationOwnership
import io.docpilot.core.reconciliation.DocumentationOwnershipManifest
import io.docpilot.core.render.ProjectSpecificationMarkdownRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import io.docpilot.core.specification.FeatureDir04SpecificationTest

class DocumentationProfileResolverTest {
    private val renderer = ProjectSpecificationMarkdownRenderer()
    private val resolver = DefaultDocumentationProfileResolver()

    @Test
    fun `validated DIR 0_4 features enable feature catalog and specifications without markdown rendering`() {
        val specification = FeatureDir04SpecificationTest().specification()
        val resolution = resolve(specification)
        val catalog = resolution.documents.single { it.type == DocumentType.FEATURE_CATALOG }
        val featureDocuments = resolution.documents.filter { it.type == DocumentType.FEATURE_SPECIFICATION }

        assertTrue(catalog.status in setOf(DocumentPlanningStatus.READY, DocumentPlanningStatus.PARTIAL))
        assertEquals(specification.features.size, featureDocuments.size)
        assertTrue(featureDocuments.all { it.status in setOf(DocumentPlanningStatus.READY, DocumentPlanningStatus.PARTIAL) })
        assertTrue(renderer.describe(specification).none { it.relativePath.contains("feature", ignoreCase = true) })
        assertTrue(DocumentationProfileIntegrity.verifyResolution(resolution))
    }

    @Test
    fun `resolves built-in profile and defers unsupported DIR models without inventing elements`() {
        val specification = profileTestSpecification()
        val resolution = resolve(specification)
        val byType = resolution.documents.associateBy { it.type }

        assertEquals(DocumentPlanningStatus.READY, byType.getValue(DocumentType.PROJECT_OVERVIEW).status)
        assertEquals(DocumentPlanningStatus.READY, byType.getValue(DocumentType.ARCHITECTURE_OVERVIEW).status)
        val moduleArchitecture = byType.getValue(DocumentType.MODULE_ARCHITECTURE)
        assertEquals(DocumentPlanningStatus.READY, moduleArchitecture.status)
        assertEquals(
            listOf(DocumentStableKey("architecture-overview")),
            moduleArchitecture.dependencyRules.map { it.documentStableKey },
        )
        assertEquals(DocumentationOwnership.DOCPILOT_OWNED, moduleArchitecture.resolvedOwnership)
        assertTrue(moduleArchitecture.sections.all { it.sectionStableId.startsWith("section:kotlin-android:module-architecture:") })
        assertEquals(DocumentPlanningStatus.READY, byType.getValue(DocumentType.TEST_STRATEGY).status)
        assertEquals(DocumentPlanningStatus.DEFERRED, byType.getValue(DocumentType.FEATURE_CATALOG).status)
        assertEquals(DocumentPlanningStatus.DEFERRED, byType.getValue(DocumentType.FEATURE_SPECIFICATION).status)
        assertEquals(DocumentPlanningStatus.DEFERRED, byType.getValue(DocumentType.DOMAIN_MODEL).status)
        assertEquals(DocumentPlanningStatus.DEFERRED, byType.getValue(DocumentType.DATABASE_SCHEMA).status)
        assertEquals(DocumentPlanningStatus.DEFERRED, byType.getValue(DocumentType.EXTERNAL_API_CONTRACT).status)
        assertTrue(byType.getValue(DocumentType.FEATURE_SPECIFICATION).sourceElementIds.isEmpty())
        assertTrue(DocumentationProfileIntegrity.verifyResolution(resolution))
    }

    @Test
    fun `missing section evidence produces explicit partial state`() {
        val specification = profileTestSpecification().copy(
            evidence = profileTestSpecification().evidence.filterNot { it.id == "e:readme" || it.id == "e:test" },
            modules = profileTestSpecification().modules.map { module ->
                module.copy(evidenceRefs = module.evidenceRefs - "e:test")
            },
        )
        val resolution = resolve(specification)

        assertEquals(
            DocumentPlanningStatus.PARTIAL,
            resolution.documents.single { it.type == DocumentType.PROJECT_OVERVIEW }.status,
        )
        assertEquals(
            DocumentPlanningStatus.PARTIAL,
            resolution.documents.single { it.type == DocumentType.TEST_STRATEGY }.status,
        )
        assertTrue(resolution.findings.any { it.kind == DocumentPlanningFindingKind.MISSING_REQUIRED_EVIDENCE })
    }

    @Test
    fun `missing required renderer capability is unsupported while optional diagram is omitted`() {
        val capabilities = renderer.capabilities() - RendererCapability.UNKNOWN_FINDING_RENDERING
        val resolution = resolver.resolve(
            DocumentationProfileResolutionRequest(
                DocumentationProfileId("kotlin-android"),
                DocumentationProfileVersion(1),
                profileTestSpecification(),
                renderer.describe(profileTestSpecification()),
                capabilities,
            ),
        )
        val architecture = resolution.documents.single { it.type == DocumentType.ARCHITECTURE_OVERVIEW }

        assertEquals(DocumentPlanningStatus.UNSUPPORTED, architecture.status)
        assertTrue(architecture.findings.any { it.kind == DocumentPlanningFindingKind.MISSING_RENDERER_CAPABILITY })
        assertEquals(
            ResolvedSectionStatus.OMITTED,
            architecture.sections.single { it.sectionId.value == "module-dependency-diagram" }.status,
        )
    }

    @Test
    fun `module input order does not change resolution identity`() {
        val specification = profileTestSpecification()
        val first = resolve(specification)
        val second = resolve(
            specification.copy(
                modules = specification.modules.reversed(),
                packages = specification.packages.reversed(),
                components = specification.components.reversed(),
                relationships = specification.relationships.reversed(),
                evidence = specification.evidence.reversed(),
            ),
        )

        assertEquals(first, second)
    }

    @Test
    fun `per module path resolution is canonical and stable`() {
        val definition = minimalDefinition(
            type = DocumentType.MODULE_ARCHITECTURE,
            key = "per-module-architecture",
            path = DocumentPathPolicy.Pattern("architecture/modules/{slug}-{scopeHash}.md"),
            multiplicity = DocumentMultiplicity.PER_MODULE,
        )
        val registry = ImmutableDocumentationProfileRegistry(listOf(minimalProfile(definitions = listOf(definition))))
        val customResolver = DefaultDocumentationProfileResolver(registry)
        val request = DocumentationProfileResolutionRequest(
            DocumentationProfileId("test-profile"),
            DocumentationProfileVersion(1),
            profileTestSpecification(),
            rendererCapabilities = setOf(RendererCapability.MARKDOWN_SECTION_RENDERING),
        )

        val documents = customResolver.resolve(request).documents

        assertEquals(2, documents.size)
        assertEquals(documents.map { it.relativePath }.distinct().size, documents.size)
        assertTrue(documents.all { it.relativePath?.matches(Regex("architecture/modules/[a-z0-9-]+-[0-9a-f]{24}\\.md")) == true })
    }

    @Test
    fun `user owned collision blocks profile document`() {
        val specification = profileTestSpecification()
        val profilePath = "project/project-overview.md"
        val colliding = descriptor(profilePath)
        val resolution = resolver.resolve(
            DocumentationProfileResolutionRequest(
                DocumentationProfileId("kotlin-android"),
                DocumentationProfileVersion(1),
                specification,
                artifactCatalog = renderer.describe(specification) + colliding,
                rendererCapabilities = renderer.capabilities(),
                ownershipManifests = listOf(manifest(colliding, DocumentationOwnership.USER_OWNED)),
            ),
        )
        val project = resolution.documents.single { it.type == DocumentType.PROJECT_OVERVIEW }

        assertEquals(DocumentPlanningStatus.BLOCKED, project.status)
        assertEquals(DocumentationOwnership.USER_OWNED, project.resolvedOwnership)
        assertTrue(project.findings.any { it.kind == DocumentPlanningFindingKind.OWNERSHIP_CONFLICT })
    }

    @Test
    fun `shared managed collision requires reconciliation but remains plannable partial`() {
        val specification = profileTestSpecification()
        val colliding = descriptor("project/project-overview.md")
        val resolution = resolver.resolve(
            DocumentationProfileResolutionRequest(
                DocumentationProfileId("kotlin-android"),
                DocumentationProfileVersion(1),
                specification,
                artifactCatalog = renderer.describe(specification) + colliding,
                rendererCapabilities = renderer.capabilities(),
                ownershipManifests = listOf(manifest(colliding, DocumentationOwnership.SHARED_MANAGED)),
            ),
        )
        val project = resolution.documents.single { it.type == DocumentType.PROJECT_OVERVIEW }

        assertEquals(DocumentPlanningStatus.PARTIAL, project.status)
        assertEquals(DocumentationOwnership.SHARED_MANAGED, project.resolvedOwnership)
        assertNotNull(project.findings.singleOrNull { it.kind == DocumentPlanningFindingKind.RECONCILIATION_REQUIRED })
    }

    private fun resolve(specification: io.docpilot.core.model.ProjectSpecification): DocumentationProfileResolution =
        resolver.resolve(
            DocumentationProfileResolutionRequest(
                DocumentationProfileId("kotlin-android"),
                DocumentationProfileVersion(1),
                specification,
                renderer.describe(specification),
                renderer.capabilities(),
            ),
        )

    private fun descriptor(path: String): DocumentationArtifactDescriptor = DocumentationArtifactDescriptor(
        DocumentationArtifactId("profile-collision:$path"),
        path,
        "text/markdown",
        DocumentationArtifactKind.PROJECT_OVERVIEW,
        emptyList(),
    )

    private fun manifest(
        descriptor: DocumentationArtifactDescriptor,
        ownership: DocumentationOwnership,
    ): DocumentationOwnershipManifest = DocumentationOwnershipManifest(
        artifactId = descriptor.artifactId,
        relativePath = descriptor.relativePath,
        mediaType = descriptor.mediaType,
        ownership = ownership,
        reviewedBaseSha256 = null,
        rendererIdentity = "test",
        evidenceRefs = emptyList(),
        manifestSha256 = "0".repeat(64),
    )
}
