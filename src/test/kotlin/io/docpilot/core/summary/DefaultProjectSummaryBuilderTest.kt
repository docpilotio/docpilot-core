package io.docpilot.core.summary

import io.docpilot.core.loader.LocalProjectLoader
import io.docpilot.core.model.ProjectBuildSystem
import io.docpilot.core.model.ProjectFileType
import io.docpilot.core.model.ProjectLanguage
import io.docpilot.core.scanner.LocalSourceScanner
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultProjectSummaryBuilderTest {

    private val loader = LocalProjectLoader()
    private val scanner = LocalSourceScanner()
    private val builder = DefaultProjectSummaryBuilder()

    @Test
    fun `builds summary from inventory facts`() {
        val root = Files.createTempDirectory("docpilot-summary")
        root.resolve("settings.gradle.kts").writeText("")
        root.resolve("build.gradle.kts").writeText("")
        root.resolve("app").createDirectories()
            .resolve("build.gradle.kts").writeText("")
        root.resolve("feature/tasks").createDirectories()
            .resolve("build.gradle.kts").writeText("")
        root.resolve("app/src/main/kotlin/example").createDirectories()
            .resolve("Main.kt").writeText("class Main")
        root.resolve("app/src/main/java/example").createDirectories()
            .resolve("Legacy.java").writeText("class Legacy {}")
        root.resolve("app/src/main").createDirectories()
            .resolve("AndroidManifest.xml").writeText("<manifest />")

        val summary = builder.build(
            scanner.scan(loader.load(root)),
        )

        assertTrue(ProjectLanguage.KOTLIN in summary.languages)
        assertTrue(ProjectLanguage.JAVA in summary.languages)
        assertTrue(ProjectBuildSystem.GRADLE in summary.buildSystems)
        assertEquals(
            listOf("app", "feature/tasks"),
            summary.candidateModulePaths,
        )
        assertEquals(1, summary.count(ProjectFileType.KOTLIN_SOURCE))
        assertEquals(1, summary.count(ProjectFileType.JAVA_SOURCE))
        assertEquals(3, summary.count(ProjectFileType.GRADLE_BUILD))
        assertEquals(1, summary.count(ProjectFileType.GRADLE_SETTINGS))
        assertEquals(1, summary.count(ProjectFileType.ANDROID_MANIFEST))
    }

    @Test
    fun `does not claim languages or build systems without evidence`() {
        val root = Files.createTempDirectory("docpilot-empty-summary")

        val summary = builder.build(
            scanner.scan(loader.load(root)),
        )

        assertTrue(summary.languages.isEmpty())
        assertTrue(summary.buildSystems.isEmpty())
        assertTrue(summary.candidateModulePaths.isEmpty())
    }
}
