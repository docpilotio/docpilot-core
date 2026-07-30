package io.docpilot.core.documentation.profile

import io.docpilot.core.model.ComponentSpecification
import io.docpilot.core.model.Evidence
import io.docpilot.core.model.EvidenceConfidence
import io.docpilot.core.model.ModuleSpecification
import io.docpilot.core.model.PackageSpecification
import io.docpilot.core.model.ProjectDescriptor
import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.model.RelationshipSpecification

internal fun profileTestSpecification(): ProjectSpecification = ProjectSpecification(
    schemaVersion = "0.3",
    project = ProjectDescriptor(
        id = "project:sample",
        name = "Sample App",
        description = "Sample Android project.",
        platforms = setOf("Android", "JVM"),
        languages = setOf("Kotlin"),
        buildSystems = setOf("Gradle"),
    ),
    modules = listOf(
        ModuleSpecification(
            id = "module:app",
            name = "app",
            path = "app",
            sourceSets = setOf("main", "test"),
            evidenceRefs = setOf("e:module", "e:test"),
        ),
        ModuleSpecification(
            id = "module:data",
            name = "data",
            path = "data",
            sourceSets = setOf("main"),
            evidenceRefs = setOf("e:data-module"),
        ),
    ),
    packages = listOf(
        PackageSpecification(
            id = "package:sample",
            name = "sample",
            qualifiedName = "io.sample",
            moduleId = "module:app",
            evidenceRefs = setOf("e:component"),
        ),
    ),
    components = listOf(
        ComponentSpecification(
            id = "type:main",
            name = "MainViewModel",
            moduleId = "module:app",
            packageId = "package:sample",
            qualifiedName = "io.sample.MainViewModel",
            kind = "CLASS",
            role = "Coordinates the UI.",
            evidenceRefs = setOf("e:component"),
        ),
    ),
    relationships = listOf(
        RelationshipSpecification(
            id = "relationship:dependency",
            type = "DEPENDS_ON",
            sourceId = "type:main",
            targetId = "external:androidx.lifecycle",
            evidenceRefs = setOf("e:relationship"),
        ),
    ),
    evidence = listOf(
        Evidence(
            id = "e:readme",
            type = "SOURCE_DOCUMENT",
            file = "README.md",
            summary = "Project purpose and overview.",
            confidence = EvidenceConfidence.HIGH,
        ),
        Evidence(
            id = "e:module",
            type = "SOURCE_MODULE",
            file = "app/build.gradle.kts",
            summary = "Gradle application module.",
            confidence = EvidenceConfidence.HIGH,
        ),
        Evidence(
            id = "e:data-module",
            type = "SOURCE_MODULE",
            file = "data/build.gradle.kts",
            summary = "Gradle data module.",
            confidence = EvidenceConfidence.HIGH,
        ),
        Evidence(
            id = "e:component",
            type = "SOURCE_SYMBOL",
            file = "MainViewModel.kt",
            summary = "Application component.",
            confidence = EvidenceConfidence.HIGH,
        ),
        Evidence(
            id = "e:relationship",
            type = "KNOWLEDGE_EDGE",
            file = "MainViewModel.kt",
            summary = "Architecture dependency relationship.",
            confidence = EvidenceConfidence.MEDIUM,
        ),
        Evidence(
            id = "e:test",
            type = "SOURCE_TEST",
            file = "MainViewModelTest.kt",
            summary = "JUnit test boundary.",
            confidence = EvidenceConfidence.HIGH,
        ),
    ),
)

internal fun minimalDefinition(
    type: DocumentType = DocumentType.PROJECT_OVERVIEW,
    key: String = "project-overview",
    path: DocumentPathPolicy = DocumentPathPolicy.Fixed("project/overview.md"),
    multiplicity: DocumentMultiplicity = DocumentMultiplicity.SINGLE,
    sections: List<SectionDefinition> = listOf(
        SectionDefinition(SectionId("overview"), "Overview", 10, required = true),
    ),
): DocumentDefinition = DocumentDefinition(
    type = type,
    stableKey = DocumentStableKey(key),
    purpose = "Explain the project.",
    primaryAudiences = setOf(DocumentAudience.DEVELOPER),
    multiplicity = multiplicity,
    pathPolicy = path,
    sections = sections,
    rendererCapabilities = setOf(RendererCapability.MARKDOWN_SECTION_RENDERING),
)

internal fun minimalProfile(
    version: Int = 1,
    definitions: List<DocumentDefinition> = listOf(minimalDefinition()),
): DocumentationProfile = DocumentationProfile(
    id = DocumentationProfileId("test-profile"),
    version = DocumentationProfileVersion(version),
    displayName = "Test Profile",
    supportedProjectKinds = setOf(DocumentationProjectKind.KOTLIN_ANDROID),
    documentDefinitions = definitions,
)
