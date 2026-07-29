package io.docpilot.core.baseline

import io.docpilot.core.evolution.DocumentationEvolutionFormat
import io.docpilot.core.incremental.specification.review.ReviewBundleFormat
import io.docpilot.core.incremental.specification.snapshot.SpecificationSnapshotFormat
import io.docpilot.core.model.ProjectDescriptor
import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.specification.DefaultSpecificationBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CanonicalBaselineContractTest {
    private val projectRoot: Path = locateProjectRoot()
    private val baseline: Properties = Properties().apply {
        Files.newInputStream(projectRoot.resolve("docs/planning/DOCPILOT-CANONICAL-BASELINE.properties")).use { load(it) }
    }

    @Test
    fun `baseline manifest matches runtime format contracts`() {
        assertEquals("1", baseline.required("baseline.format"))
        assertEquals(DefaultSpecificationBuilder.CURRENT_SCHEMA_VERSION, baseline.required("dir.builder.current"))
        assertEquals("0.2", baseline.required("dir.manual.default"))
        assertEquals(SpecificationSnapshotFormat.CURRENT_VERSION.toString(), baseline.required("specification.snapshot.format"))
        assertEquals(SpecificationSnapshotFormat.SUPPORTED_DIR_SCHEMA_VERSION, baseline.required("specification.snapshot.supported.dir"))
        assertEquals(ReviewBundleFormat.CURRENT_VERSION.toString(), baseline.required("review.bundle.format"))
        assertEquals(DocumentationEvolutionFormat.CURRENT_VERSION.toString(), baseline.required("evolution.report.format"))

        val manual = ProjectSpecification(project = ProjectDescriptor("project:baseline", "Baseline"))
        assertEquals(baseline.required("dir.manual.default"), manual.schemaVersion)
    }

    @Test
    fun `baseline manifest matches canonical build settings`() {
        val buildScript = Files.readString(projectRoot.resolve("build.gradle.kts"))
        val wrapperProperties = Files.readString(projectRoot.resolve("gradle/wrapper/gradle-wrapper.properties"))

        assertTrue(buildScript.contains("kotlin(\"jvm\") version \"${baseline.required("kotlin.version")}\""))
        assertTrue(buildScript.contains("version = \"${baseline.required("artifact.version")}\""))
        assertTrue(buildScript.contains("jvmToolchain(${baseline.required("java.toolchain")})"))
        assertTrue(wrapperProperties.contains("gradle-${baseline.required("gradle.wrapper.version")}-bin.zip"))

        val relationshipProjectionSource = Files.readString(
            projectRoot.resolve("src/main/kotlin/io/docpilot/core/specification/RelationshipProjection.kt"),
        )
        assertTrue(
            relationshipProjectionSource.contains(
                "public val formatVersion: Int = ${baseline.required("relationship.projection.format")}",
            ),
        )
    }

    @Test
    fun `rfc inventory does not mark proposed RFC-0054 complete`() {
        assertEquals("RFC-0057", baseline.required("active.rfc"))
        assertEquals("RFC-0053", baseline.required("completed.rfc.range.end"))
        assertTrue(baseline.csv("completed.rfc.additional").containsAll(listOf("RFC-0055", "RFC-0056")))
        assertTrue("RFC-0054" in baseline.csv("not.completed.rfcs"))
        assertFalse("RFC-0054" in baseline.csv("completed.rfc.additional"))
    }

    @Test
    fun `release decisions remain independent from technical baseline`() {
        assertEquals("PRODUCT_VALIDATION_FAIL", baseline.required("public.v1.product.validation"))
        assertEquals("NOT_APPROVED", baseline.required("public.v1.release"))
        assertEquals("PENDING", baseline.required("pv.009"))
        assertEquals("IMPLEMENTATION_COMPLETED_WITH_VERIFICATION_LIMITATION", baseline.required("rfc.0056.status"))
        assertEquals("NOT_DECLARED", baseline.required("release.candidate"))
    }

    private fun Properties.required(key: String): String =
        getProperty(key)?.takeIf(String::isNotBlank) ?: error("Missing canonical baseline property: $key")

    private fun Properties.csv(key: String): List<String> =
        required(key).split(',').map(String::trim).filter(String::isNotEmpty)

    private fun locateProjectRoot(): Path =
        generateSequence(Paths.get("").toAbsolutePath().normalize()) { it.parent }
            .firstOrNull { Files.exists(it.resolve("settings.gradle.kts")) }
            ?: error("Unable to locate DocPilot project root.")
}
