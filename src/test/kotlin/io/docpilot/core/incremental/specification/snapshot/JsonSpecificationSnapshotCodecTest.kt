package io.docpilot.core.incremental.specification.snapshot

import io.docpilot.core.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class JsonSpecificationSnapshotCodecTest {
    private val codec = JsonSpecificationSnapshotCodec()

    @Test
    fun `round trips full specification deterministically`() {
        val specification = sampleSpecification()
        val first = codec.encode(specification)
        val second = codec.encode(specification)
        assertEquals(first, second)
        val decoded = assertIs<SpecificationSnapshotLoadResult.Valid>(codec.decode(first, specification.project.id))
        assertEquals(specification, decoded.snapshot.specification)
    }

    @Test
    fun `detects payload tampering`() {
        val specification = sampleSpecification()
        val encoded = codec.encode(specification).replace("Sample component", "Changed component")
        val decoded = codec.decode(encoded, specification.project.id)
        assertEquals(SnapshotValidationFailure.INTEGRITY_MISMATCH, assertIs<SpecificationSnapshotLoadResult.Invalid>(decoded).reason)
    }

    @Test
    fun `detects project mismatch`() {
        val decoded = codec.decode(codec.encode(sampleSpecification()), "project:other")
        assertEquals(SnapshotValidationFailure.PROJECT_MISMATCH, assertIs<SpecificationSnapshotLoadResult.Invalid>(decoded).reason)
    }

    private fun sampleSpecification() = ProjectSpecification(
        schemaVersion = "0.3",
        project = ProjectDescriptor("project:sample", "샘플", "설명", setOf("Android"), setOf("Kotlin"), setOf("Gradle")),
        modules = listOf(ModuleSpecification("module:app", "app", ":app", sourceSets = setOf("main"), evidenceRefs = setOf("e:1"))),
        packages = listOf(PackageSpecification("package:sample", "sample", "com.example.sample", "module:app", evidenceRefs = setOf("e:1"))),
        components = listOf(ComponentSpecification(
            id = "type:sample", name = "Sample", moduleId = "module:app", kind = "class", role = "Sample component",
            packageId = "package:sample", modifiers = setOf("public"), annotations = listOf("Stable"),
            apis = listOf(ApiSpecification("api:run", "run", "function", parameters = listOf(ParameterSpecification("value", "String", true)), evidenceRefs = setOf("e:1"))),
            properties = listOf(PropertySpecification("property:name", "name", "String", mutable = false, hasInitializer = true, evidenceRefs = setOf("e:1"))),
            evidenceRefs = setOf("e:1"),
        )),
        relationships = listOf(RelationshipSpecification("rel:1", "depends_on", "type:sample", "type:other", evidenceRefs = setOf("e:1"))),
        evidence = listOf(Evidence("e:1", "source", "src/Sample.kt", "Sample", 1, 10, "근거", EvidenceConfidence.HIGH)),
        unresolved = listOf(UnresolvedItem("u:1", "Sample", "Why?", "Review")),
    )
}
